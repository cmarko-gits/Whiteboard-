package dev.whiteboard.core

import dev.whiteboard.core.editor.*
import dev.whiteboard.core.model.*
import org.junit.Assert.*
import org.junit.Test

class LayerOrderingTest {
    private fun shape(x: Float) = Shape(bounds = Bounds(x, 0f, x + 10f, 10f), kind = Tool.RECTANGLE, style = InkStyle())
    private val objects = List(5) { shape(it * 20f) }
    private fun session(elements: List<Element> = objects, budget: Long = 32L * 1024 * 1024) =
        DocumentSession(Board(name = "Layers", pages = listOf(Page(elements = elements))), historyByteLimit = budget)

    @Test fun aSingleObjectMovesForwardAcrossExactlyOneNeighbor() {
        val session = session()
        assertTrue(session.stepLayer(setOf(objects[1].id), LayerStep.FORWARD))
        assertEquals(listOf(objects[0], objects[2], objects[1], objects[3], objects[4]), session.page.elements)
        assertTrue(session.canUndo)
        session.undo()
        assertEquals(objects, session.page.elements)
        assertFalse(session.canUndo)
        session.redo()
        assertSame(objects[1], session.page.elements[2])
    }

    @Test fun aSingleObjectMovesBackwardAcrossExactlyOneNeighbor() {
        val session = session()
        session.stepLayer(setOf(objects[3].id), LayerStep.BACKWARD)
        assertEquals(listOf(objects[0], objects[1], objects[3], objects[2], objects[4]), session.page.elements)
        session.undo()
        assertEquals(objects, session.page.elements)
    }

    @Test fun anAdjacentSelectionMovesAsOneBlockInBothDirections() {
        val session = session()
        val selected = setOf(objects[1].id, objects[2].id)
        session.stepLayer(selected, LayerStep.FORWARD)
        assertEquals(listOf(objects[0], objects[3], objects[1], objects[2], objects[4]), session.page.elements)
        session.undo()
        session.stepLayer(selected, LayerStep.BACKWARD)
        assertEquals(listOf(objects[1], objects[2], objects[0], objects[3], objects[4]), session.page.elements)
        session.undo()
        assertEquals(objects, session.page.elements)
    }

    @Test fun disjointBlocksPreserveBothSelectedAndUnselectedRelativeOrders() {
        val session = session()
        val selected = setOf(objects[1].id, objects[3].id)
        session.stepLayer(selected, LayerStep.FORWARD)
        assertEquals(listOf(objects[0], objects[2], objects[1], objects[4], objects[3]), session.page.elements)
        session.undo()
        session.stepLayer(selected, LayerStep.BACKWARD)
        assertEquals(listOf(objects[1], objects[0], objects[3], objects[2], objects[4]), session.page.elements)
    }

    @Test fun boundariesUnknownIdsAndEmptyOrFullSelectionsKeepRedoAndSavedState() {
        val session = session()
        session.add(Note(bounds = Bounds(0f, 0f, 20f, 20f), text = "Undo"))
        session.undo()
        val before = session.board
        assertFalse(session.stepLayer(setOf(objects.last().id), LayerStep.FORWARD))
        assertFalse(session.stepLayer(setOf(objects.first().id), LayerStep.BACKWARD))
        assertFalse(session.stepLayer(emptySet(), LayerStep.FORWARD))
        assertFalse(session.stepLayer(setOf(newId()), LayerStep.BACKWARD))
        assertFalse(session.stepLayer(objects.map(Element::id).toSet(), LayerStep.FORWARD))
        assertFalse(session.reorder(objects.map(Element::id).toSet(), true))
        assertSame(before, session.board)
        assertTrue(session.canRedo)
        assertFalse(session.canUndo)
        assertFalse(session.dirty)
        val empty = arrayListOf<Element>()
        assertSame(empty, LayerOrdering.step(empty, emptySet(), LayerStep.FORWARD))
    }

    @Test fun theAvailabilityQueryMatchesTheSelectionBoundary() {
        assertFalse(LayerOrdering.canStep(objects, setOf(objects.last().id), LayerStep.FORWARD))
        assertFalse(LayerOrdering.canStep(objects, setOf(objects.first().id), LayerStep.BACKWARD))
        assertFalse(LayerOrdering.canStep(objects, objects.map(Element::id).toSet(), LayerStep.BACKWARD))
        assertTrue(LayerOrdering.canStep(objects, setOf(objects[1].id), LayerStep.FORWARD))
        assertTrue(LayerOrdering.canStep(objects, setOf(objects[1].id), LayerStep.BACKWARD))
    }

    @Test fun groupsAndPlacedMediaKeepTheirExactObjectsAndAttachments() {
        val media = MediaElement(bounds = Bounds(0f, 0f, 100f, 50f), assetId = "${newId()}.png")
        val placed = AffineElement.fromDocument(media, AffineTransform2D.rotation(37.0, media.bounds.center))
        val group = ElementGroup(children = listOf(placed, objects[0]))
        val session = session(listOf(group, objects[1]))
        session.stepLayer(setOf(group.id), LayerStep.FORWARD)
        assertSame(group, session.page.elements.last())
        assertSame(placed, (session.page.elements.last() as ElementGroup).children.first())
        assertEquals(setOf(media.assetId), session.retainedAssetIds)
        session.undo()
        assertEquals(listOf(group, objects[1]), session.page.elements)
    }

    @Test fun reorderingLargeGeometryOnlyRetainsAnOrderSizedHistoryEntry() {
        val stroke = Stroke(points = List(5000) { Point(it.toFloat(), 50f) }, style = InkStyle())
        val session = session(listOf(stroke, objects[0]), budget = 256)
        assertTrue(stroke.estimatedBytes() > session.editBudgetBytes)
        assertTrue(session.reorder(setOf(stroke.id), true))
        assertTrue(session.canUndo)
        session.undo()
        assertSame(stroke, session.page.elements.first())
        session.redo()
        assertSame(stroke, session.page.elements.last())
    }

    @Test fun exceedingTheOrderBudgetLeavesDocumentAndHistoryUntouched() {
        val session = session(objects.take(2), budget = 128)
        val before = session.board
        val failure = assertThrows(DocumentFailure::class.java) { session.stepLayer(setOf(objects[0].id), LayerStep.FORWARD) }
        assertEquals(DocumentError.EDIT_LIMIT, failure.error)
        assertSame(before, session.board)
        assertFalse(session.canUndo)
        assertFalse(session.dirty)
    }

    @Test fun deletingAndUndoingAfterReorderingCannotLoseAnAttachment() {
        val media = MediaElement(bounds = Bounds(0f, 0f, 100f, 50f), assetId = "${newId()}.png")
        val session = session(listOf(media, objects[0]))
        session.stepLayer(setOf(media.id), LayerStep.FORWARD)
        session.delete(setOf(media.id))
        assertEquals(setOf(media.assetId), session.retainedAssetIds)
        session.undo()
        session.undo()
        assertSame(media, session.page.elements.first())
        session.redo()
        session.redo()
        assertEquals(listOf(objects[0]), session.page.elements)
        assertEquals(setOf(media.assetId), session.retainedAssetIds)
    }

    @Test fun malformedAndStaleOrderCommandsCannotDiscardObjects() {
        val before = objects.take(2).map(Element::id)
        assertThrows(IllegalArgumentException::class.java) { LayerOrderChange(newId(), before, listOf(before[0], before[0])) }
        assertThrows(IllegalArgumentException::class.java) { LayerOrderChange(newId(), before, listOf(before[1], newId())) }
        val session = session(objects.take(2))
        val command = LayerOrderChange(session.page.id, before, before.reversed())
        session.add(objects[2])
        val updated = session.board
        assertThrows(IllegalArgumentException::class.java) { session.execute(command) }
        assertSame(updated, session.board)
        session.undo()
        assertEquals(objects.take(2), session.page.elements)
        assertFalse(session.canUndo)
    }

    @Test fun aLargeSparseSelectionMovesLinearlyWithoutChangingAnyContent() {
        val original = List(10_000) { shape(it * 20f) }
        val session = session(original)
        val selected = original.filterIndexed { index, _ -> index % 2 == 0 }.map(Element::id).toSet()
        session.stepLayer(selected, LayerStep.FORWARD)
        val expected = original.chunked(2).flatMap { it.reversed() }
        assertEquals(expected, session.page.elements)
        expected.indices.forEach { assertSame(expected[it], session.page.elements[it]) }
        session.undo()
        assertEquals(original, session.page.elements)
        session.redo()
        assertEquals(expected, session.page.elements)
    }
}
