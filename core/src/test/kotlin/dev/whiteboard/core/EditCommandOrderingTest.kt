package dev.whiteboard.core

import dev.whiteboard.core.editor.*
import dev.whiteboard.core.model.*
import org.junit.Assert.*
import org.junit.Test

class EditCommandOrderingTest {
    private fun shape(x: Float) = Shape(bounds = Bounds(x, 0f, x + 2f, 2f), kind = Tool.RECTANGLE, style = InkStyle())
    private val a = shape(0f)
    private val b = shape(10f)
    private val board = Board(name = "Ordering", pages = listOf(Page(elements = listOf(a, b))))
    private fun insertion(first: Int, second: Int): List<PageObject> {
        val c = shape(20f)
        val d = shape(30f)
        val command = EditCommand(
            board.activePageId,
            listOf(ElementDelta(null, LocatedElement(first, c)), ElementDelta(null, LocatedElement(second, d)))
        )
        val expected = if (first <= 0 && second <= 0) listOf(d, c, a, b) else listOf(a, b, c, d)
        val result = command.apply(board, true)
        assertEquals(expected, result.activePage.elements)
        assertEquals(board, command.apply(result, false))
        return result.activePage.elements
    }

    @Test fun destinationsPastTheEndAppendInOrderAndUndoExactly() {
        insertion(99, 100)
    }

    @Test fun equalDestinationsRetainTheirExistingInsertionSemantics() {
        insertion(0, 0)
    }

    @Test fun negativeDestinationsRetainTheirExistingClampingSemantics() {
        insertion(-4, -3)
    }

    @Test fun aMixedDeleteMoveAndInsertionPreservesUnchangedObjectsAndReversesExactly() {
        val c = shape(20f)
        val d = shape(30f)
        val e = shape(40f)
        val original = board.withPage(board.activePage.copy(elements = listOf(a, b, c, d)))
        val changed = d.copy(fill = 0xFF12AB34.toInt())
        val command = EditCommand(
            original.activePageId,
            listOf(
                ElementDelta(LocatedElement(1, b), null),
                ElementDelta(LocatedElement(3, d), LocatedElement(0, changed)),
                ElementDelta(null, LocatedElement(2, e))
            )
        )
        val result = command.apply(original, true)
        assertEquals(listOf(changed, a, e, c), result.activePage.elements)
        assertSame(a, result.activePage.elements[1])
        assertSame(c, result.activePage.elements[3])
        assertEquals(original, command.apply(result, false))
    }

    @Test fun rotatingAlternatingObjectsOnALargePageKeepsEveryLayerAndUndoRedoIdentity() {
        val objects = List(10_000) { shape(it * 4f) }
        val session = DocumentSession(Board(name = "Large selection", pages = listOf(Page(elements = objects))))
        val ids = objects.filterIndexed { index, _ -> index % 2 == 0 }.map(PageObject::id).toSet()
        assertTrue(session.rotate(ids, 45.0))
        val rotated = session.page.elements
        assertEquals(objects.map(PageObject::id), rotated.map(PageObject::id))
        objects.indices.forEach { index ->
            if (index % 2 == 0) assertTrue(rotated[index] is AffineElement) else assertSame(objects[index], rotated[index])
        }
        session.undo()
        assertEquals(objects, session.page.elements)
        session.redo()
        assertEquals(rotated, session.page.elements)
    }
}
