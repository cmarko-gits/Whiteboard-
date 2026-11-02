package dev.whiteboard.core

import dev.whiteboard.core.editor.*
import dev.whiteboard.core.model.*
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

class SelectionAlignmentTest {
    private val first = Shape(bounds = Bounds(10f, 20f, 50f, 60f), kind = Tool.RECTANGLE, style = InkStyle())
    private val second = Shape(bounds = Bounds(80f, 100f, 140f, 180f), kind = Tool.ELLIPSE, style = InkStyle())
    private fun session(elements: List<Element>, budget: Long = 32L * 1024 * 1024) =
        DocumentSession(Board(name = "Alignment", pages = listOf(Page(elements = elements))), historyByteLimit = budget)

    private fun assertFrames(alignment: ObjectAlignment, a: Bounds, b: Bounds) {
        val outside = Note(bounds = Bounds(-500f, -500f, -400f, -400f), text = "Not selected")
        val original = listOf(first, outside, second)
        val session = session(original)
        assertTrue(session.align(setOf(first.id, second.id), alignment))
        val result = session.page.elements
        assertEquals(listOf(first.id, outside.id, second.id), result.map(Element::id))
        assertEquals(first.copy(bounds = a), result[0])
        assertSame(outside, result[1])
        assertEquals(second.copy(bounds = b), result[2])
        session.undo()
        assertEquals(original, session.page.elements)
        assertFalse(session.canUndo)
        session.redo()
        assertEquals(result, session.page.elements)
    }

    @Test fun leftEdgesUseTheLeftmostSelectedFrame() = assertFrames(ObjectAlignment.LEFT, first.bounds, Bounds(10f, 100f, 70f, 180f))

    @Test fun horizontalCentersUseTheCombinedFrameCenter() =
        assertFrames(ObjectAlignment.HORIZONTAL_CENTER, Bounds(55f, 20f, 95f, 60f), Bounds(45f, 100f, 105f, 180f))

    @Test fun rightEdgesUseTheRightmostSelectedFrame() = assertFrames(ObjectAlignment.RIGHT, Bounds(100f, 20f, 140f, 60f), second.bounds)

    @Test fun topEdgesUseTheTopmostSelectedFrame() = assertFrames(ObjectAlignment.TOP, first.bounds, Bounds(80f, 20f, 140f, 100f))

    @Test fun verticalCentersUseTheCombinedFrameCenter() =
        assertFrames(ObjectAlignment.VERTICAL_CENTER, Bounds(10f, 80f, 50f, 120f), Bounds(80f, 60f, 140f, 140f))

    @Test fun bottomEdgesUseTheBottommostSelectedFrame() = assertFrames(ObjectAlignment.BOTTOM, Bounds(10f, 140f, 50f, 180f), second.bounds)

    @Test fun fewerThanTwoExistingObjectsCannotChangeTheDocument() {
        val session = session(listOf(first, second))
        val before = session.board
        assertFalse(session.align(emptySet(), ObjectAlignment.LEFT))
        assertFalse(session.align(setOf(first.id, newId()), ObjectAlignment.RIGHT))
        assertSame(before, session.board)
        assertFalse(session.canUndo)
        assertFalse(session.dirty)
    }

    @Test fun anAlreadyAlignedSelectionPreservesRedoAndTheSavedRevision() {
        val aligned = second.copy(bounds = Bounds(10f, 100f, 70f, 180f))
        val session = session(listOf(first, aligned))
        session.add(Note(bounds = Bounds(0f, 0f, 20f, 20f), text = "Undo"))
        session.undo()
        val before = session.board
        assertFalse(session.align(setOf(first.id, aligned.id), ObjectAlignment.LEFT))
        assertSame(before, session.board)
        assertTrue(session.canRedo)
        assertFalse(session.dirty)
    }

    @Test fun aGroupMovesItsAffineContentAndStrokeTogetherWithoutChangingTheirPoseOrAssets() {
        val media = MediaElement(bounds = Bounds(100f, 100f, 180f, 160f), assetId = "${newId()}.png")
        val posed = AffineElement.fromDocument(media, AffineTransform2D.rotation(37.0, media.bounds.center))
        val stroke = Stroke(points = listOf(Point(120f, 200f), Point(160f, 240f)), style = InkStyle(width = 7f))
        val group = ElementGroup(children = listOf(posed, stroke))
        val session = session(listOf(first, group))
        val dx = first.bounds.left - group.bounds.left
        session.align(setOf(first.id, group.id), ObjectAlignment.LEFT)
        val result = session.page.elements[1] as ElementGroup
        assertEquals(group.id, result.id)
        assertEquals(group.children.map(Element::id), result.children.map(Element::id))
        val moved = result.children[0] as AffineElement
        assertSame(posed.source, moved.source)
        assertEquals(posed.placement.then(AffineTransform2D.translation(dx.toDouble(), 0.0)), moved.placement)
        assertEquals(stroke.copy(points = stroke.points.map { it + Point(dx, 0f) }), result.children[1])
        session.delete(setOf(group.id))
        assertEquals(setOf(media.assetId), session.retainedAssetIds)
        session.undo()
        session.undo()
        assertSame(group, session.page.elements[1])
        session.redo()
        assertEquals(result, session.page.elements[1])
    }

    @Test fun legacyRotatedMediaRetainsItsCropAndAngleWhileItsWorldFrameIsAligned() {
        val media = MediaElement(
            bounds = Bounds(100f, 100f, 180f, 160f),
            assetId = "${newId()}.png",
            rotation = 45,
            crop = Bounds(0.1f, 0.2f, 0.9f, 0.8f)
        )
        val session = session(listOf(first, media))
        session.align(setOf(first.id, media.id), ObjectAlignment.LEFT)
        val moved = session.page.elements[1] as MediaElement
        assertEquals(10f, moved.selectionBounds.left, 0.001f)
        assertEquals(media.bounds.width, moved.bounds.width, 0.001f)
        assertEquals(media.rotation, moved.rotation)
        assertEquals(media.crop, moved.crop)
        assertEquals(media.assetId, moved.assetId)
        assertEquals(media.bounds.top, moved.bounds.top, 0f)
    }

    @Test fun translatingLineEndpointsDoesNotScaleTheirArrowOrInk() {
        val line = Line(start = Point(100f, 200f), end = Point(200f, 200f), style = InkStyle(width = 0.5f), arrow = true)
        val session = session(listOf(first, line))
        val dy = first.bounds.top - line.selectionBounds.top
        session.align(setOf(first.id, line.id), ObjectAlignment.TOP)
        assertEquals(line.copy(start = line.start + Point(0f, dy), end = line.end + Point(0f, dy)), session.page.elements[1])
    }

    @Test fun anAlignedLargeStrokeDoesNotConsumeTheEditBudget() {
        val stroke = Stroke(points = List(5000) { Point(it.toFloat(), 0f) }, style = InkStyle())
        val anchor = first.copy(bounds = Bounds(stroke.bounds.left, 100f, 50f, 200f))
        val session = session(listOf(stroke, anchor), budget = 128)
        assertFalse(session.align(setOf(stroke.id, anchor.id), ObjectAlignment.LEFT))
        assertFalse(session.canUndo)
    }

    @Test fun anOverBudgetMoveCannotPartiallyAlignObjects() {
        val session = session(listOf(first, second), budget = 128)
        val before = session.board
        val failure = assertThrows(DocumentFailure::class.java) { session.align(setOf(first.id, second.id), ObjectAlignment.LEFT) }
        assertEquals(DocumentError.EDIT_LIMIT, failure.error)
        assertSame(before, session.board)
        assertFalse(session.canUndo)
    }

    @Test fun cancellingInsideALargeStrokeLeavesTheOriginalGeometryUntouched() {
        val stroke = Stroke(points = List(5000) { Point(it.toFloat(), 0f) }, style = InkStyle())
        val anchor = first.copy(bounds = Bounds(-100f, 100f, -50f, 200f))
        val page = Page(elements = listOf(stroke, anchor))
        var calls = 0
        assertThrows(CancellationException::class.java) {
            SelectionAlignment.plan(page, setOf(stroke.id, anchor.id), ObjectAlignment.LEFT) {
                if (++calls == 5) throw CancellationException()
            }
        }
        assertEquals(5, calls)
        assertSame(stroke, page.elements[0])
        assertEquals(Point(4999f, 0f), stroke.points.last())
    }

    @Test fun aFrameThatWouldPushInkBeyondTheCoordinateLimitIsRejectedAtomically() {
        val shape = first.copy(style = InkStyle(width = 200f))
        val anchor = Note(bounds = Bounds(999_999_488f, 0f, 1_000_000_000f, 100f), text = "Boundary")
        val session = session(listOf(shape, anchor))
        val before = session.board
        assertThrows(IllegalArgumentException::class.java) { session.align(setOf(shape.id, anchor.id), ObjectAlignment.RIGHT) }
        assertSame(before, session.board)
        assertFalse(session.canUndo)
    }

    @Test fun aComputedPlanCannotReplaceANewerEditOrAnotherPage() {
        val session = session(listOf(first, second))
        val plan = SelectionAlignment.plan(session.page, setOf(first.id, second.id), ObjectAlignment.LEFT)
        val newer = second.copy(fill = 0xFF12AB34.toInt())
        session.replace(newer)
        assertFalse(session.applySelectionEdit(plan))
        assertSame(newer, session.page.elements[1])
        session.addPage()
        assertFalse(session.applySelectionEdit(plan))
        assertTrue(session.page.elements.isEmpty())
    }

    @Test fun aLargeSelectionKeepsEveryLayerAndAlignsInOneUndoCommand() {
        val objects = List(10_000) { first.copy(id = newId(), bounds = Bounds(it * 10f, it * 20f, it * 10f + 5f, it * 20f + 5f)) }
        val session = session(objects)
        session.align(objects.map(Element::id).toSet(), ObjectAlignment.LEFT)
        assertEquals(objects.map(Element::id), session.page.elements.map(Element::id))
        session.page.elements.forEachIndexed { index, element ->
            assertEquals(Bounds(0f, index * 20f, 5f, index * 20f + 5f), element.bounds)
        }
        session.undo()
        assertEquals(objects, session.page.elements)
        assertFalse(session.canUndo)
    }
}
