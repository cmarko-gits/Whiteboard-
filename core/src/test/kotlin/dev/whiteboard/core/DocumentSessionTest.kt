package dev.whiteboard.core

import dev.whiteboard.core.editor.DocumentSession
import dev.whiteboard.core.model.*
import org.junit.Assert.*
import org.junit.Test

class DocumentSessionTest {
    private fun stroke(x: Float = 0f) = Stroke(points = listOf(Point(x, 0f), Point(x + 50, 50f)), style = InkStyle())

    @Test fun relativeTransformsAccumulateAgainstTheLatestRevisionAndUndoIndependently() {
        val note = Note(bounds = Bounds(100f, 100f, 200f, 200f), text = "Move")
        val session = DocumentSession(Board(name = "Relative moves", pages = listOf(Page(elements = listOf(note)))))
        assertTrue(session.transformBy(note.id, Point(10f, 0f), 1f))
        assertTrue(session.transformBy(note.id, Point(20f, 0f), 1f))
        assertEquals(130f, session.page.elements.single().bounds.left)
        session.undo()
        assertEquals(110f, session.page.elements.single().bounds.left)
        session.undo()
        assertEquals(note, session.page.elements.single())
        assertFalse(session.transformBy("missing", Point(10f, 0f), 1f))
    }

    @Test fun `undo redo preserves object identity and order`() {
        val session = DocumentSession(Board(name = "Test"))
        val a = stroke()
        val b = stroke(100f)
        session.add(a)
        session.add(b)
        session.delete(setOf(a.id))
        session.undo()
        assertEquals(listOf(a, b), session.page.elements)
        session.redo()
        assertEquals(listOf(b), session.page.elements)
    }

    @Test fun `new edit after undo clears redo and remains dirty`() {
        val session = DocumentSession(Board(name = "Test"))
        session.add(stroke())
        session.markSaved(session.revision)
        session.undo()
        session.add(stroke(200f))
        assertFalse(session.canRedo)
        assertTrue(session.dirty)
    }

    @Test fun `undo back to saved revision clears dirty flag`() {
        val session = DocumentSession(Board(name = "Test"))
        session.add(stroke())
        session.markSaved(session.revision)
        session.add(stroke(10f))
        session.undo()
        assertFalse(session.dirty)
    }

    @Test fun `save finishing during another edit does not mark newer edits saved`() {
        val session = DocumentSession(Board(name = "Test"))
        session.add(stroke())
        val saving = session.revision
        session.add(stroke(10f))
        session.markSaved(saving)
        assertTrue(session.dirty)
    }

    @Test fun `batch deletion is restored at original indices`() {
        val session = DocumentSession(Board(name = "Test"))
        val objects = List(6) { stroke(it * 20f) }
        objects.forEach(session::add)
        session.delete(setOf(objects[0].id, objects[2].id, objects[5].id))
        session.undo()
        assertEquals(objects, session.page.elements)
    }

    @Test fun `history limit does not discard document content`() {
        val session = DocumentSession(Board(name = "Test"), historyLimit = 2)
        repeat(5) { session.add(stroke(it * 10f)) }
        repeat(5) { session.undo() }
        assertEquals(3, session.page.elements.size)
    }

    @Test fun `history memory budget prunes operations without deleting content`() {
        val session = DocumentSession(Board(name = "Memory"), historyByteLimit = 100)
        session.add(stroke())
        assertFalse(session.canUndo)
        assertEquals(1, session.page.elements.size)
    }

    @Test fun `recovered document starts unsaved`() {
        val session = DocumentSession(Board(name = "Test"))
        session.markRecovered()
        assertTrue(session.dirty)
    }

    @Test fun `move is undoable without modifying original stroke`() {
        val session = DocumentSession(Board(name = "Test"))
        val original = stroke()
        session.add(original)
        session.replace(original.transform(Bounds(100f, 100f, 153f, 153f)))
        assertNotEquals(original, session.page.elements.single())
        session.undo()
        assertEquals(original, session.page.elements.single())
    }
}
