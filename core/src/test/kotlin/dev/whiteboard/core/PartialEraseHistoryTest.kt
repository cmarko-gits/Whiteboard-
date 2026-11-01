package dev.whiteboard.core

import dev.whiteboard.core.editor.*
import dev.whiteboard.core.model.*
import org.junit.Assert.*
import org.junit.Test

class PartialEraseHistoryTest {
    private val stroke = Stroke(points = listOf(Point(0f, 0f), Point(100f, 0f)), style = InkStyle(width = 2f))
    private fun cut(source: Stroke = stroke) = StrokeCut(source, PartialStrokeEraser.cut(source, Point(50f, 0f), Point(50f, 0f), 9f))

    @Test fun aSplitIsOneUndoStepAndRedoRestoresTheSameFragmentIdsAndOrder() {
        val first = Note(bounds = Bounds(0f, 100f, 100f, 200f), text = "Before")
        val last = first.copy(id = newId(), text = "After")
        val board = Board(name = "History", pages = listOf(Page(elements = listOf(first, stroke, last))))
        val session = DocumentSession(board)
        val split = cut()
        assertTrue(session.eraseParts(listOf(split)))
        assertEquals(listOf(first) + split.fragments + last, session.page.elements)
        assertTrue(session.dirty)
        val after = session.page
        session.undo()
        assertEquals(board.activePage, session.page)
        assertFalse(session.dirty)
        session.redo()
        assertEquals(after, session.page)
    }

    @Test fun groupedStrokesSplitInPlaceAndKeepNonStrokeChildrenUnchanged() {
        val note = Note(bounds = Bounds(0f, 100f, 100f, 200f), text = "Keep me")
        val group = ElementGroup(children = listOf(stroke, note))
        val session = DocumentSession(Board(name = "Group", pages = listOf(Page(elements = listOf(group)))))
        val split = cut()
        session.eraseParts(listOf(split))
        val after = session.page.elements.single() as ElementGroup
        assertEquals(group.id, after.id)
        assertEquals(split.fragments + note, after.children)
        session.undo()
        assertEquals(listOf(group), session.page.elements)
    }

    @Test fun aSingleGestureCanSplitMultipleLayersWithoutMovingInterveningObjects() {
        val second = stroke.copy(id = newId())
        val note = Note(bounds = Bounds(0f, 100f, 100f, 200f), text = "Middle layer")
        val board = Board(name = "Layers", pages = listOf(Page(elements = listOf(stroke, note, second))))
        val session = DocumentSession(board)
        val firstCut = cut()
        val lastCut = cut(second)
        session.eraseParts(listOf(firstCut, lastCut))
        assertEquals(firstCut.fragments + note + lastCut.fragments, session.page.elements)
        session.undo()
        assertEquals(board.activePage, session.page)
    }

    @Test fun fullyErasingTheStrokeCollapsesAGroupAndUndoRestoresIt() {
        val note = Note(bounds = Bounds(0f, 100f, 100f, 200f), text = "Keep me")
        val group = ElementGroup(children = listOf(stroke, note))
        val session = DocumentSession(Board(name = "Collapse", pages = listOf(Page(elements = listOf(group)))))
        session.eraseParts(listOf(StrokeCut(stroke, emptyList())))
        assertEquals(listOf(note), session.page.elements)
        session.undo()
        assertEquals(listOf(group), session.page.elements)
    }

    @Test fun staleOriginalRejectsTheEntireGestureWithoutOverwritingANewerEdit() {
        val session = DocumentSession(Board(name = "Race", pages = listOf(Page(elements = listOf(stroke)))))
        session.replace(stroke.copy(points = listOf(Point(0f, 20f), Point(100f, 20f))))
        val before = session.page
        val revision = session.revision
        assertFalse(session.eraseParts(listOf(cut())))
        assertEquals(before, session.page)
        assertEquals(revision, session.revision)
    }

    @Test fun exceedingTheUndoBudgetRejectsTheEditAtomically() {
        val board = Board(name = "Budget", pages = listOf(Page(elements = listOf(stroke))))
        val session = DocumentSession(board, historyByteLimit = 64)
        val error = assertThrows(DocumentFailure::class.java) { session.eraseParts(listOf(cut())) }
        assertEquals(DocumentError.EDIT_LIMIT, error.error)
        assertEquals(board, session.board)
        assertFalse(session.dirty)
        assertFalse(session.canUndo)
    }

    @Test fun aNoOpDoesNotCreateHistoryOrDiscardRedo() {
        val session = DocumentSession(Board(name = "No op", pages = listOf(Page(elements = listOf(stroke)))))
        session.add(Note(bounds = Bounds(0f, 100f, 100f, 200f), text = "Undo me"))
        session.undo()
        assertFalse(session.eraseParts(listOf(StrokeCut(stroke, listOf(stroke)))))
        assertTrue(session.canRedo)
        assertFalse(session.dirty)
    }

    @Test fun exceedingThePageObjectLimitLeavesTheFullPageUntouched() {
        val elements = listOf(stroke) + List(Page.MAX_ELEMENTS - 1) { stroke.copy(id = newId()) }
        val board = Board(name = "Full", pages = listOf(Page(elements = elements)))
        val session = DocumentSession(board)
        val error = assertThrows(DocumentFailure::class.java) { session.eraseParts(listOf(cut())) }
        assertEquals(DocumentError.EDIT_LIMIT, error.error)
        assertSame(board, session.board)
        assertFalse(session.canUndo)
    }

    @Test fun aFragmentCannotReplaceAnUnrelatedObjectOrItsGroup() {
        val note = Note(bounds = Bounds(0f, 100f, 100f, 200f), text = "Keep me")
        val group = ElementGroup(children = listOf(stroke, note))
        val board = Board(name = "Identity", pages = listOf(Page(elements = listOf(group))))
        listOf(note.id, group.id).forEach { occupiedId ->
            val session = DocumentSession(board)
            val forged = StrokeCut(stroke, listOf(stroke.copy(id = occupiedId)))
            assertThrows(IllegalArgumentException::class.java) { session.eraseParts(listOf(forged)) }
            assertSame(board, session.board)
            assertFalse(session.canUndo)
        }
    }

    @Test fun fragmentIdsMustBeUniqueAcrossAllCutsInOneGesture() {
        val second = stroke.copy(id = newId())
        val board = Board(name = "Duplicate fragments", pages = listOf(Page(elements = listOf(stroke, second))))
        val session = DocumentSession(board)
        val shared = newId()
        val firstCut = StrokeCut(stroke, listOf(stroke.copy(id = shared)))
        val secondCut = StrokeCut(second, listOf(second.copy(id = shared)))
        assertThrows(IllegalArgumentException::class.java) { session.eraseParts(listOf(firstCut, secondCut)) }
        assertSame(board, session.board)
        assertFalse(session.canUndo)
    }

    @Test fun fragmentationLimitAlsoAppliesAtTheDomainBoundary() {
        val board = Board(name = "Domain limit", pages = listOf(Page(elements = listOf(stroke))))
        val session = DocumentSession(board)
        val fragments = List(PartialStrokeEraser.MAX_FRAGMENTS + 1) { stroke.copy(id = newId()) }
        val error = assertThrows(DocumentFailure::class.java) { session.eraseParts(listOf(StrokeCut(stroke, fragments))) }
        assertEquals(DocumentError.EDIT_LIMIT, error.error)
        assertSame(board, session.board)
        assertFalse(session.canUndo)
    }
}
