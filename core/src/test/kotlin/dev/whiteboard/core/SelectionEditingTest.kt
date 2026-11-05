package dev.whiteboard.core

import dev.whiteboard.core.editor.*
import dev.whiteboard.core.model.*
import org.junit.Assert.*
import org.junit.Test

class SelectionEditingTest {
    private fun shape(x: Float) = Shape(bounds = Bounds(x, 0f, x + 100f, 100f), kind = Tool.RECTANGLE, style = InkStyle())

    @Test fun batchTransformAndDeleteAreSingleReversibleOperations() {
        val original = listOf(shape(0f), shape(200f), shape(400f))
        val session = DocumentSession(Board(name = "Selection", pages = listOf(Page(elements = original))))
        val moved = original.take(2).map { it.transform(it.bounds.copy(bottom = 200f)) }
        session.replaceAll(moved)
        session.undo()
        assertEquals(original, session.page.elements)
        session.redo()
        assertEquals(moved + original.last(), session.page.elements)
        session.delete(moved.map { it.id }.toSet())
        session.undo()
        assertEquals(moved + original.last(), session.page.elements)
    }

    @Test fun reorderingPreservesRelativeOrderAndHistory() {
        val original = List(5) { shape(it * 100f) }
        val session = DocumentSession(Board(name = "Layers", pages = listOf(Page(elements = original))))
        val selection = setOf(original[1].id, original[3].id)
        session.reorder(selection, true)
        assertEquals(listOf(original[0], original[2], original[4], original[1], original[3]), session.page.elements)
        session.undo()
        assertEquals(original, session.page.elements)
        session.reorder(selection, false)
        assertEquals(listOf(original[1], original[3], original[0], original[2], original[4]), session.page.elements)
        session.undo()
        assertFalse(session.dirty)
    }

    @Test fun clipboardCreatesIndependentIdsAcrossPagesWithOneUndo() {
        val first = Page(elements = listOf(shape(0f), shape(200f)))
        val second = Page()
        val session = DocumentSession(Board(name = "Clipboard", pages = listOf(first, second)))
        val clipboard = ObjectClipboard()
        clipboard.copy(first.elements)
        session.selectPage(second.id)
        val pasted = clipboard.paste()
        session.addAll(pasted)
        assertEquals(2, session.page.elements.size)
        assertTrue(pasted.none { copy -> first.elements.any { it.id == copy.id } })
        assertEquals(24f, (pasted.first() as Shape).bounds.left)
        session.undo()
        assertTrue(session.page.elements.isEmpty())
        session.redo()
        assertEquals(pasted, session.page.elements)
        assertEquals(48f, (clipboard.paste().first() as Shape).bounds.left)
    }

    @Test fun clipboardRetainsMediaAssetWithoutDuplicatingBinaryFile() {
        val image = MediaElement(bounds = Bounds(0f, 0f, 100f, 100f), assetId = "${newId()}.png")
        val clipboard = ObjectClipboard()
        clipboard.copy(listOf(image))
        assertEquals(setOf(image.assetId), clipboard.assetIds)
        assertEquals(image.assetId, (clipboard.paste().single() as MediaElement).assetId)
        clipboard.copy(emptyList())
        assertFalse(clipboard.available)
    }
}
