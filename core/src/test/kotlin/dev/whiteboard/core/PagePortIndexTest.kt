package dev.whiteboard.core

import dev.whiteboard.core.model.*
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

class PagePortIndexTest {
    private val bounds = Bounds(100f, 200f, 300f, 400f)
    private fun shape(kind: Tool = Tool.RECTANGLE) = Shape(bounds = bounds, kind = kind, style = InkStyle())
    private fun point(element: Element, port: ConnectionPort) = PagePortIndex(Page(elements = listOf(element))).point(element.id, port)
    private fun assertPoint(expected: Point, actual: Point) {
        assertEquals(expected.x, actual.x, 0.001f)
        assertEquals(expected.y, actual.y, 0.001f)
    }

    @Test fun everyClosedShapePortLiesOnItsActualContour() {
        listOf(Tool.RECTANGLE, Tool.SQUARE, Tool.ELLIPSE, Tool.CIRCLE, Tool.TRIANGLE, Tool.DIAMOND).forEach { kind ->
            val shape = shape(kind)
            val index = PagePortIndex(Page(elements = listOf(shape)))
            ConnectionPort.entries.forEach { port ->
                val point = index.point(shape.id, port)
                assertTrue("$kind $port", shape.containsShape(point, 0.001f))
                val outward = when (port) {
                    ConnectionPort.LEFT -> point + Point(-1f, 0f)
                    ConnectionPort.TOP -> point + Point(0f, -1f)
                    ConnectionPort.RIGHT -> point + Point(1f, 0f)
                    ConnectionPort.BOTTOM -> point + Point(0f, 1f)
                    ConnectionPort.CENTER -> null
                }
                if (outward != null) assertFalse("$kind $port must be on the edge", shape.containsShape(outward))
            }
        }
    }

    @Test fun triangleSidePortsDoNotUseTheEmptyBoundingBoxEdges() {
        val triangle = shape(Tool.TRIANGLE)
        assertEquals(Point(150f, 300f), point(triangle, ConnectionPort.LEFT))
        assertEquals(Point(250f, 300f), point(triangle, ConnectionPort.RIGHT))
        assertEquals(Point(200f, 200f), point(triangle, ConnectionPort.TOP))
        assertEquals(Point(200f, 400f), point(triangle, ConnectionPort.BOTTOM))
        assertEquals(Point(200f, 300f), point(triangle, ConnectionPort.CENTER))
    }

    @Test fun notesAndTextExposeTheirEditableFrameWithoutChangingTheirContent() {
        val note = Note(bounds = bounds, text = "Keep this note")
        val text = TextElement(bounds = bounds, text = "Keep this text", fontSize = 27f)
        listOf(note, text).forEach { element ->
            assertEquals(Point(100f, 300f), point(element, ConnectionPort.LEFT))
            assertEquals(Point(200f, 200f), point(element, ConnectionPort.TOP))
            assertEquals(Point(300f, 300f), point(element, ConnectionPort.RIGHT))
            assertEquals(Point(200f, 400f), point(element, ConnectionPort.BOTTOM))
            assertEquals(bounds.center, point(element, ConnectionPort.CENTER))
        }
        assertEquals("Keep this note", note.text)
        assertEquals("Keep this text", text.text)
    }

    @Test fun affineTrianglePortsFollowRotationScaleAndShearInsteadOfItsWorldAabb() {
        val triangle = shape(Tool.TRIANGLE)
        val transform = AffineTransform2D.rotation(37.0, bounds.center).then(AffineTransform2D.scale(1.7, 0.6))
            .then(AffineTransform2D(xx = 1.0, xy = 0.25, yx = 0.1, yy = 1.0, tx = 50.0, ty = -25.0))
        val posed = AffineElement.fromDocument(triangle, transform)
        val before = PagePortIndex(Page(elements = listOf(triangle)))
        val after = PagePortIndex(Page(elements = listOf(posed)))
        ConnectionPort.entries.forEach { port ->
            val resolved = after.point(posed.id, port)
            assertPoint(transform.map(before.point(triangle.id, port)), resolved)
            assertTrue((posed.source as Shape).containsShape(posed.placement.unmap(resolved), 0.001f))
        }
        assertNotEquals(Point(posed.bounds.left, posed.bounds.center.y), after.point(posed.id, ConnectionPort.LEFT))
    }

    @Test fun mediaPortsRespectLegacyRotationWithoutTouchingCropOrAssets() {
        val media = MediaElement(bounds = bounds, assetId = "${newId()}.png", rotation = 45, crop = Bounds(0.1f, 0.2f, 0.9f, 0.8f))
        val rotation = AffineTransform2D.rotation(45.0, bounds.center)
        assertPoint(rotation.map(Point(100f, 300f)), point(media, ConnectionPort.LEFT))
        assertPoint(rotation.map(Point(200f, 200f)), point(media, ConnectionPort.TOP))
        assertEquals(45, media.rotation)
        assertEquals(Bounds(0.1f, 0.2f, 0.9f, 0.8f), media.crop)
    }

    @Test fun affineMediaAndEditableTextKeepTheirOwnLocalPorts() {
        val media = MediaElement(bounds = bounds, assetId = "${newId()}.png")
        val note = Note(bounds = bounds, text = "Placed")
        val text = TextElement(bounds = bounds, text = "Placed text")
        val transform = AffineTransform2D.rotation(90.0, bounds.center)
        listOf(media, note, text).forEach { element ->
            val placed = AffineElement.fromDocument(element, transform)
            assertEquals(Point(200f, 200f), point(placed, ConnectionPort.LEFT))
            assertEquals(Point(300f, 300f), point(placed, ConnectionPort.TOP))
        }
    }

    @Test fun aGroupHasWorldFramePortsAndItsChildrenKeepTheirSeparateIdentities() {
        val note = Note(bounds = bounds, text = "Child")
        val placed = AffineElement.fromDocument(shape(), AffineTransform2D.rotation(37.0, bounds.center))
        val group = ElementGroup(children = listOf(note, placed))
        val index = PagePortIndex(Page(elements = listOf(group)))
        assertEquals(Point(group.bounds.left, group.bounds.center.y), index.point(group.id, ConnectionPort.LEFT))
        assertEquals(Point(100f, 300f), index.point(note.id, ConnectionPort.LEFT))
        assertPoint(point(placed, ConnectionPort.TOP), index.point(placed.id, ConnectionPort.TOP))
    }

    @Test fun strokesLinesAndMissingOrOtherPageIdsCannotBecomeTargets() {
        val stroke = Stroke(points = listOf(Point(0f, 0f)), style = InkStyle())
        val line = Line(start = Point(10f, 20f), end = Point(100f, 200f), style = InkStyle(), arrow = true)
        val other = shape()
        val index = PagePortIndex(Page(elements = listOf(stroke, line)))
        listOf(stroke.id, line.id, other.id).forEach { id ->
            assertFalse(index.supports(id))
            assertThrows(IllegalArgumentException::class.java) { index.point(id, ConnectionPort.CENTER) }
        }
        assertFalse(PagePortIndex(Page()).supports(other.id))
    }

    @Test fun aMixedGroupCanBeATargetWhileItsStrokeChildCannot() {
        val stroke = Stroke(points = listOf(Point(0f, 0f)), style = InkStyle())
        val note = Note(bounds = bounds, text = "Node")
        val group = ElementGroup(children = listOf(stroke, note))
        val index = PagePortIndex(Page(elements = listOf(group)))
        assertTrue(index.supports(group.id))
        assertTrue(index.supports(note.id))
        assertFalse(index.supports(stroke.id))
    }

    @Test fun zeroSizedFramesHaveFinitePortsWithoutDivisionByZero() {
        val zero = Note(bounds = Bounds(10f, 20f, 10f, 20f), text = "Zero")
        val index = PagePortIndex(Page(elements = listOf(zero)))
        ConnectionPort.entries.forEach { assertEquals(Point(10f, 20f), index.point(zero.id, it)) }
        val flat = shape(Tool.TRIANGLE).copy(bounds = Bounds(10f, 20f, 30f, 20f))
        assertEquals(Point(15f, 20f), point(flat, ConnectionPort.LEFT))
        assertEquals(Point(25f, 20f), point(flat, ConnectionPort.RIGHT))
    }

    @Test fun snapshotsAreInvalidatedByEditsOrPageChangesButNotByViewportChanges() {
        val shape = shape()
        val page = Page(elements = listOf(shape))
        val index = PagePortIndex(page)
        assertTrue(index.matches(page))
        assertTrue(index.matches(page.copy(viewport = Viewport(Point(200f, -300f), 2f))))
        val moved = shape.copy(bounds = Bounds(0f, 0f, 200f, 200f))
        assertFalse(index.matches(page.copy(elements = listOf(moved))))
        assertFalse(index.matches(page.copy(id = newId())))
        assertEquals(Point(100f, 300f), index.point(shape.id, ConnectionPort.LEFT))
        assertEquals(Point(0f, 100f), PagePortIndex(page.copy(elements = listOf(moved))).point(shape.id, ConnectionPort.LEFT))
    }

    @Test fun unsafeRenderFramesAreRejectedBeforePublishingAnIndex() {
        val shape = shape().copy(bounds = Bounds(999_999_488f, 0f, 1_000_000_000f, 100f), style = InkStyle(width = 200f))
        assertThrows(IllegalArgumentException::class.java) { PagePortIndex(Page(elements = listOf(shape))) }
    }

    @Test fun indexConstructionCanBeCancelledWithoutModifyingThePage() {
        val elements = List(100) { shape().copy(id = newId()) }
        val page = Page(elements = elements)
        var visits = 0
        assertThrows(CancellationException::class.java) {
            PagePortIndex(page) { if (++visits == 17) throw CancellationException() }
        }
        assertEquals(17, visits)
        assertSame(elements, page.elements)
    }

    @Test fun aLargeGroupResolvesAllChildIdsWithoutChangingTheirGeometry() {
        val children = List(10_000) { shape().copy(id = newId(), bounds = Bounds(it * 10f, 0f, it * 10f + 5f, 5f)) }
        val group = ElementGroup(children = children)
        val index = PagePortIndex(Page(elements = listOf(group)))
        children.forEach { child -> assertEquals(Point(child.bounds.left, 2.5f), index.point(child.id, ConnectionPort.LEFT)) }
        assertEquals(Point(0f, 2.5f), index.point(group.id, ConnectionPort.LEFT))
        assertSame(children, group.children)
    }
}
