package dev.whiteboard.core

import dev.whiteboard.core.editor.*
import dev.whiteboard.core.model.*
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

class ObjectRotationTest {
    private val shape =
        Shape(bounds = Bounds(0f, 0f, 200f, 100f), kind = Tool.RECTANGLE, style = InkStyle(width = 2f), fill = 0xFFDD3344.toInt())
    private fun documentSession(vararg elements: Element) =
        DocumentSession(Board(name = "Rotation", pages = listOf(Page(elements = elements.toList()))))
    private fun point(expected: Point, actual: Point, tolerance: Float = 0.01f) {
        assertEquals(expected.x, actual.x, tolerance)
        assertEquals(expected.y, actual.y, tolerance)
    }

    @Test fun aRotatedRectangleHasAnExactLocalFrameAndRejectsEmptyBoundingBoxCorners() {
        val session = documentSession(shape)
        assertTrue(session.rotate(setOf(shape.id), 45.0))
        val rotated = session.page.elements.single() as AffineElement
        assertEquals(shape.id, rotated.id)
        assertEquals(Bounds(-100f, -50f, 100f, 50f), rotated.source.bounds)
        point(Point(100f, 50f), rotated.bounds.center)
        assertTrue(rotated.hitTest(Point(100f, 50f), 0f))
        assertFalse(rotated.hitTest(Point(rotated.bounds.left + 2, rotated.bounds.top + 2), 0f))
        session.undo()
        assertSame(shape, session.page.elements.single())
        session.redo()
        assertEquals(rotated, session.page.elements.single())
    }

    @Test fun aGroupUsesOnePivotAndKeepsItsIdChildrenAndOneUndoEntry() {
        val stroke = Stroke(points = listOf(Point(1f, 1f), Point(99f, 99f)), style = InkStyle(width = 2f))
        val note = Note(bounds = Bounds(100f, 0f, 200f, 100f), text = "Keep me")
        val group = ElementGroup(children = listOf(stroke, note))
        val session = documentSession(group)
        session.rotate(setOf(group.id), 90.0)
        val rotated = session.page.elements.single() as ElementGroup
        assertEquals(group.id, rotated.id)
        assertEquals(group.children.map(PageObject::id), rotated.children.map(PageObject::id))
        point(Point(149f, -49f), (rotated.children.first() as Stroke).points.first())
        point(Point(100f, 100f), rotated.children.last().bounds.center)
        session.undo()
        assertSame(group, session.page.elements.single())
        assertFalse(session.canUndo)
    }

    @Test fun multipleObjectsShareTheSelectionCenterAndKeepLayerOrder() {
        val second = shape.copy(id = newId(), bounds = Bounds(200f, 0f, 400f, 100f))
        val session = documentSession(shape, second)
        session.rotate(setOf(shape.id, second.id), 180.0)
        assertEquals(listOf(shape.id, second.id), session.page.elements.map(PageObject::id))
        point(Point(300f, 50f), session.page.geometry.resolve(session.page.elements.first()).bounds.center)
        point(Point(100f, 50f), session.page.geometry.resolve(session.page.elements.last()).bounds.center)
    }

    @Test fun trianglesUseTheirPaintedInteriorForPickingAndFill() {
        val triangle = shape.copy(kind = Tool.TRIANGLE)
        assertFalse(triangle.hitTest(Point(5f, 5f), 0f))
        assertEquals(FillEngine.Target.Background, FillEngine.target(Page(elements = listOf(triangle)), Point(5f, 5f)))
        val session = documentSession(triangle)
        session.rotate(setOf(triangle.id), 37.0)
        val placed = session.page.elements.single() as AffineElement
        val emptyCorner = placed.placement.map(Point(-95f, -45f))
        assertFalse(placed.hitTest(emptyCorner, 0f))
        assertEquals(FillEngine.Target.Background, FillEngine.target(session.page, emptyCorner))
    }

    @Test fun fillChangesTheRotatedShapeContentAndPreservesItsPlacement() {
        val session = documentSession(shape)
        session.rotate(setOf(shape.id), 45.0)
        val before = session.page.elements.single() as AffineElement
        session.fill(before.bounds.center, 0x0012AB34)
        val after = session.page.elements.single() as AffineElement
        assertEquals(before.placement, after.placement)
        assertEquals(shape.id, after.id)
        assertEquals(0xFF12AB34.toInt(), (after.source as Shape).fill)
        session.undo()
        assertEquals(before, session.page.elements.single())
    }

    @Test fun resizingAfterRotationKeepsTheExactAffineFrameWithoutNestedWrappers() {
        val original = AffineElement.fromDocument(shape, AffineTransform2D.rotation(33.0, shape.bounds.center))
        val target = Bounds(20f, 40f, 620f, 340f)
        val resized = original.transform(target)
        assertSame(original.source, resized.source)
        assertFalse(resized.source is AffineElement)
        assertEquals(target.left, resized.bounds.left, 0.01f)
        assertEquals(target.top, resized.bounds.top, 0.01f)
        assertEquals(target.right, resized.bounds.right, 0.01f)
        assertEquals(target.bottom, resized.bounds.bottom, 0.01f)
        point(Point(30f, 10f), resized.placement.unmap(resized.placement.map(Point(30f, 10f))))
    }

    @Test fun mediaKeepsItsExistingAngleCropAndAttachmentReference() {
        val media = MediaElement(bounds = Bounds(100f, 100f, 500f, 300f), assetId = "${newId()}.png", rotation = 90)
        val session = documentSession(media)
        session.rotate(setOf(media.id), 35.0)
        val placed = session.page.elements.single() as AffineElement
        assertEquals(0, (placed.source as MediaElement).rotation)
        val expected = AffineTransform2D.rotation(125.0, media.bounds.center).map(Point(400f, 200f))
        point(expected, placed.placement.map(Point(100f, 0f)))
        val cropped = placed.withContent((placed.source as MediaElement).cropped(Bounds(0.5f, 0f, 1f, 1f))) as AffineElement
        point(placed.placement.map(Point(100f, 0f)), cropped.bounds.center)
        assertEquals(setOf(media.assetId), listOf(cropped).referencedAssets())
        assertEquals(setOf(media.assetId), session.retainedAssetIds)
    }

    @Test fun clipboardAndUngroupKeepRotatedContentAndItsAttachmentsEditable() {
        val media = MediaElement(bounds = Bounds(0f, 0f, 100f, 100f), assetId = "${newId()}.png")
        val note = Note(bounds = Bounds(200f, 0f, 300f, 100f), text = "Searchable Čvor")
        val group = ElementGroup(children = listOf(media, note))
        val session = documentSession(group)
        session.rotate(setOf(group.id), -30.0)
        val rotated = session.page.elements.single()
        assertTrue(session.board.searchableText().contains(note.text))
        assertEquals(group.id, session.board.firstTextMatch("cvor")?.element?.id)
        val clipboard = ObjectClipboard()
        clipboard.copy(listOf(rotated))
        val copy = clipboard.paste().single() as ElementGroup
        assertNotEquals(group.id, copy.id)
        assertTrue(copy.children.none { child -> group.children.any { it.id == child.id } })
        point(session.page.geometry.resolve(rotated).bounds.center + Point(24f, 24f), copy.bounds.center)
        assertEquals(setOf(media.assetId), clipboard.assetIds)
        session.ungroup(setOf(group.id))
        assertEquals(group.children.map(PageObject::id), session.page.elements.map(PageObject::id))
        assertEquals(setOf(media.assetId), session.board.referencedAssets())
    }

    @Test fun partialErasingWorksOnTheRotatedPolylineAndBothEditsUndoSeparately() {
        val stroke = Stroke(points = listOf(Point(100f, 200f), Point(600f, 200f)), style = InkStyle(width = 6f))
        val session = documentSession(stroke)
        session.rotate(setOf(stroke.id), 90.0)
        val rotated = session.page.elements.single() as Stroke
        val pieces = PartialStrokeEraser.cut(rotated, Point(250f, 200f), Point(450f, 200f), 10f)
        assertEquals(2, pieces.size)
        session.eraseParts(listOf(StrokeCut(rotated, pieces)))
        assertTrue(pieces.all { piece -> piece.points.all { it.x == 350f } })
        session.undo()
        assertEquals(listOf(rotated), session.page.elements)
        session.undo()
        assertEquals(listOf(stroke), session.page.elements)
    }

    @Test fun aStalePlanCannotOverwriteAnInterveningEdit() {
        val session = documentSession(shape)
        val plan = ObjectRotation.plan(session.page, setOf(shape.id), 45.0)
        session.replace(shape.copy(fill = 0xFF112233.toInt()))
        val newer = session.page
        assertFalse(session.applySelectionEdit(plan))
        assertSame(newer, session.page)
    }

    @Test fun aFullTurnDoesNotCreateHistoryOrDiscardRedo() {
        val session = documentSession(shape)
        session.add(Note(bounds = Bounds(300f, 0f, 400f, 100f), text = "Undo me"))
        session.undo()
        assertFalse(session.rotate(setOf(shape.id), -720.0))
        assertTrue(session.canRedo)
        assertFalse(session.canUndo)
    }

    @Test fun exceedingTheHistoryOrCoordinateBudgetLeavesTheDocumentUntouched() {
        val board = Board(name = "Budget", pages = listOf(Page(elements = listOf(shape))))
        val session = DocumentSession(board, historyByteLimit = 64)
        assertEquals(DocumentError.EDIT_LIMIT, assertThrows(DocumentFailure::class.java) { session.rotate(setOf(shape.id), 45.0) }.error)
        assertSame(board, session.board)
        val edge = shape.copy(bounds = Bounds(900_000_000f, 900_000_000f, 1_000_000_000f, 1_000_000_000f))
        val edgeSession = documentSession(edge)
        assertThrows(IllegalArgumentException::class.java) { edgeSession.rotate(setOf(edge.id), 45.0) }
        assertSame(edge, edgeSession.page.elements.single())
        assertFalse(edgeSession.canUndo)
    }

    @Test fun cancellingPlanningCannotPartiallyMutateThePage() {
        val stroke = Stroke(points = List(5000) { Point(it.toFloat(), 0f) }, style = InkStyle())
        val page = Page(elements = listOf(stroke))
        var checkpoints = 0
        assertThrows(CancellationException::class.java) {
            ObjectRotation.plan(page, setOf(stroke.id), 30.0) {
                if (++checkpoints == 3) throw CancellationException()
            }
        }
        assertSame(stroke, page.elements.single())
    }

    @Test fun matrixZeroSignsDoNotChangeEqualityOrHashingAcrossJsonImplementations() {
        val first = AffineTransform2D(xy = -0.0)
        val second = AffineTransform2D(xy = 0.0)
        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertEquals(1.0, AffineTransform2D.rotation(37.0).inverseMaxScale, 1e-12)
        assertEquals(0.5, AffineTransform2D.scale(2.0, 3.0).inverseMaxScale, 1e-12)
    }

    @Test fun aLargeLocalShapeCanBePickedInsideItsSmallValidDocumentFrame() {
        val large = shape.copy(bounds = Bounds(-1_000_000_000f, -1_000_000_000f, 1_000_000_000f, 1_000_000_000f))
        val placed = AffineElement(large, AffineTransform2D.scale(0.000001, 0.000001))
        assertTrue(placed.hitTest(Point(0f, 0f), 0f))
        assertFalse(placed.hitTest(Point(1010f, 0f), 0f))
        assertEquals(FillEngine.Target.Surface(placed, placed.id), FillEngine.target(Page(elements = listOf(placed)), Point(0f, 0f)))
    }

    @Test fun arrowheadPickingFollowsItsRotatedPaintedSegments() {
        val arrow = Line(start = Point(200f, 200f), end = Point(300f, 200f), style = InkStyle(width = 4f), arrow = true)
        val session = documentSession(arrow)
        session.rotate(setOf(arrow.id), 45.0)
        val rotated = session.page.elements.single() as Line
        val wing = checkNotNull(rotated.arrowhead()).first
        val midpoint = Point((wing.x + rotated.end.x) / 2, (wing.y + rotated.end.y) / 2)
        assertTrue(rotated.hitTest(midpoint, 0f))
        assertTrue(distanceToSegment(midpoint, rotated.start, rotated.end) > rotated.style.width / 2)
    }
}
