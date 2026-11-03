package dev.whiteboard.core

import dev.whiteboard.core.editor.PageConnectionGeometry
import dev.whiteboard.core.model.*
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

class PageConnectionGeometryTest {
    private val first = Shape(id = "first", bounds = Bounds(0f, 0f, 100f, 100f), kind = Tool.RECTANGLE, style = InkStyle())
    private val second = Note(id = "second", bounds = Bounds(200f, 100f, 300f, 200f), text = "Target")
    private val page = Page(elements = listOf(first, second))
    private val connector = Connector(
        id = "edge",
        start = ConnectorEnd.Bound(first.id, ConnectionPort.RIGHT),
        end = ConnectorEnd.Bound(second.id, ConnectionPort.LEFT)
    )

    @Test fun boundEndsResolveToTheCurrentObjectsAndKeepTheirStyle() {
        val edge = connector.copy(style = InkStyle(width = 12f, opacity = 0.5f), arrow = false)
        val line = PageConnectionGeometry(page, listOf(edge)).line(edge.id)
        assertEquals(Line(edge.id, Point(100f, 50f), Point(200f, 150f), edge.style, arrow = false), line)
        assertSame(first, page.elements.first())
    }

    @Test fun movingAndResizingTargetsChangesGeometryWithoutEditingTheConnection() {
        val before = PageConnectionGeometry(page, listOf(connector))
        val updated = page.copy(elements = listOf(first.copy(bounds = Bounds(40f, 60f, 240f, 100f)), second))
        val after = PageConnectionGeometry(updated, listOf(connector)).line(connector.id)
        assertEquals(Point(240f, 80f), after.start)
        assertEquals(Point(200f, 150f), after.end)
        assertEquals(Point(100f, 50f), before.line(connector.id).start)
        assertEquals(ConnectorEnd.Bound(first.id, ConnectionPort.RIGHT), connector.start)
    }

    @Test fun rotationUsesTheSameLocalPortForPreviewAndCommittedSnapshots() {
        val placed = AffineElement.fromDocument(first, AffineTransform2D.rotation(90.0, first.bounds.center))
        val preview = page.copy(elements = listOf(placed, second))
        val original = PageConnectionGeometry(page, listOf(connector))
        val geometry = PageConnectionGeometry(preview, listOf(connector))
        assertEquals(50f, geometry.line(connector.id).start.x, 0.001f)
        assertEquals(100f, geometry.line(connector.id).start.y, 0.001f)
        assertFalse(original.matches(preview, listOf(connector)))
        assertEquals(Point(100f, 50f), original.line(connector.id).start)
    }

    @Test fun groupChildrenAndGroupFramesRemainDistinctTargets() {
        val group = ElementGroup(id = "group", children = listOf(first, second))
        val grouped = page.copy(elements = listOf(group))
        val groupEdge = connector.copy(id = "group-edge", end = ConnectorEnd.Bound(group.id, ConnectionPort.BOTTOM))
        val geometry = PageConnectionGeometry(grouped, listOf(connector, groupEdge))
        assertEquals(Point(200f, 150f), geometry.line(connector.id).end)
        assertEquals(Point(150f, 200f), geometry.line(groupEdge.id).end)
        assertEquals(Point(100f, 50f), geometry.line(groupEdge.id).start)
    }

    @Test fun freeEndsResolveWithoutAnyTargetAndCanCoincide() {
        val point = ConnectorEnd.Free(Point(17f, 29f))
        val free = connector.copy(start = point, end = point)
        val line = PageConnectionGeometry(Page(), listOf(free)).line(free.id)
        assertEquals(point.point, line.start)
        assertEquals(point.point, line.end)
        assertNotNull(line.arrowhead())
    }

    @Test fun absentAndOtherPageTargetsAreRejectedInsteadOfDetached() {
        val otherPage = Page(elements = listOf(first))
        assertThrows(IllegalArgumentException::class.java) { PageConnectionGeometry(otherPage, listOf(connector)) }
        assertThrows(IllegalArgumentException::class.java) { PageConnectionGeometry(Page(), listOf(connector)) }
    }

    @Test fun strokeAndOrdinaryLineIdsCannotBeBoundTargets() {
        val stroke = Stroke(id = "stroke", points = listOf(Point(10f, 20f)), style = InkStyle())
        val line = Line(id = "line", start = Point(0f, 0f), end = Point(20f, 20f), style = InkStyle())
        listOf(stroke, line).forEach { target ->
            val edge = connector.copy(end = ConnectorEnd.Bound(target.id, ConnectionPort.CENTER))
            assertThrows(IllegalArgumentException::class.java) {
                PageConnectionGeometry(page.copy(elements = page.elements + target), listOf(edge))
            }
        }
    }

    @Test fun referencesToOtherConnectorsAreRejectedIncludingCycles() {
        val one = connector.copy(end = ConnectorEnd.Bound("other-edge", ConnectionPort.LEFT))
        val two = connector.copy(id = "other-edge", end = ConnectorEnd.Bound(one.id, ConnectionPort.RIGHT))
        assertThrows(IllegalArgumentException::class.java) { PageConnectionGeometry(page, listOf(one, two)) }
        assertThrows(IllegalArgumentException::class.java) { PageConnectionGeometry(page, listOf(one, connector.copy(id = "other-edge"))) }
    }

    @Test fun cyclesBetweenDistinctObjectsAreValidAndDoNotRequireRecursiveResolution() {
        val third = second.copy(id = "third", bounds = Bounds(400f, 0f, 500f, 100f))
        val two = connector.copy(id = "two", start = connector.end, end = ConnectorEnd.Bound(third.id, ConnectionPort.LEFT))
        val three = connector.copy(id = "three", start = two.end, end = connector.start)
        val geometry = PageConnectionGeometry(page.copy(elements = page.elements + third), listOf(connector, two, three))
        assertEquals(Point(200f, 150f), geometry.line(two.id).start)
        assertEquals(Point(400f, 50f), geometry.line(three.id).start)
        assertEquals(Point(100f, 50f), geometry.line(three.id).end)
    }

    @Test fun connectionIdsCannotCollideWithObjectsChildrenOrEachOther() {
        val grouped = page.copy(elements = listOf(ElementGroup(id = "group", children = page.elements)))
        listOf(first.id, second.id, "group").forEach { id ->
            val free = Connector(id, ConnectorEnd.Free(Point(0f, 0f)), ConnectorEnd.Free(Point(30f, 40f)))
            assertThrows(IllegalArgumentException::class.java) { PageConnectionGeometry(grouped, listOf(free)) }
        }
        assertThrows(IllegalArgumentException::class.java) { PageConnectionGeometry(page, listOf(connector, connector)) }
    }

    @Test fun snapshotsInvalidateOnPageObjectsOrAnyConnectionEditButIgnoreViewport() {
        val edges = listOf(connector)
        val geometry = PageConnectionGeometry(page, edges)
        assertTrue(geometry.matches(page.copy(viewport = Viewport(scale = 2f)), edges.toList()))
        assertFalse(geometry.matches(page.copy(id = "other-page"), edges))
        assertFalse(geometry.matches(page.copy(elements = page.elements.toList()), edges))
        assertFalse(geometry.matches(page, listOf(connector.copy(arrow = false))))
        assertFalse(geometry.matches(page, listOf(connector.copy(style = InkStyle(width = 7f)))))
        assertFalse(geometry.matches(page, listOf(connector.copy(end = ConnectorEnd.Free(Point(3f, 4f))))))
        assertFalse(geometry.matches(page, emptyList()))
    }

    @Test fun callerListEditsCannotChangeAnAlreadyResolvedSnapshot() {
        val edges = mutableListOf(connector)
        val geometry = PageConnectionGeometry(page, edges)
        edges.clear()
        assertFalse(geometry.matches(page, edges))
        assertEquals(Point(100f, 50f), geometry.line(connector.id).start)
        assertThrows(IllegalArgumentException::class.java) { geometry.line("unknown") }
    }

    @Test fun actualInkAndArrowBoundsAreValidatedBeforePublishingGeometry() {
        val free = connector.copy(
            start = ConnectorEnd.Free(Point(999_999_936f, 0f)),
            end = ConnectorEnd.Free(Point(1_000_000_000f, 0f)),
            style = InkStyle(width = 200f)
        )
        assertThrows(IllegalArgumentException::class.java) { PageConnectionGeometry(Page(), listOf(free)) }
    }

    @Test fun combinedObjectLimitIncludesConnectionsAndGroupChildren() {
        val grouped = page.copy(elements = listOf(ElementGroup(children = page.elements)))
        assertThrows(IllegalArgumentException::class.java) {
            PageConnectionGeometry(grouped, List(Page.MAX_ELEMENTS - 1) { connector })
        }
        assertThrows(IllegalArgumentException::class.java) {
            PageConnectionGeometry(Page(), List(Page.MAX_ELEMENTS + 1) { connector })
        }
    }

    @Test fun constructionCanBeCancelledWithoutChangingInputs() {
        val edges = List(100) { connector.copy(id = "edge-$it") }
        var visits = 0
        assertThrows(CancellationException::class.java) {
            PageConnectionGeometry(page, edges) { if (++visits == 17) throw CancellationException() }
        }
        assertEquals(17, visits)
        assertEquals(100, edges.size)
        assertEquals(listOf(first, second), page.elements)
    }

    @Test fun manyConnectionsShareOneTargetIndexWithoutChangingDocumentObjects() {
        val edges = List(10_000) { connector.copy(id = "edge-$it") }
        val geometry = PageConnectionGeometry(page, edges)
        edges.forEach { edge ->
            assertEquals(Point(100f, 50f), geometry.line(edge.id).start)
            assertEquals(Point(200f, 150f), geometry.line(edge.id).end)
        }
        assertEquals(listOf(first, second), page.elements)
    }
}
