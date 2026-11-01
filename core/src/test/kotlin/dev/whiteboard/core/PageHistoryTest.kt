package dev.whiteboard.core

import dev.whiteboard.core.editor.DocumentSession
import dev.whiteboard.core.model.*
import org.junit.Assert.*
import org.junit.Test

class PageHistoryTest {
    @Test fun `deleting page is undoable with full object content`() {
        val session = DocumentSession(Board(name = "Pages"))
        session.addPage()
        val pageId = session.page.id
        val note = Note(bounds = Bounds(0f, 0f, 200f, 200f), text = "Keep me")
        session.add(note)
        session.deletePage(pageId)
        session.undo()
        assertEquals(pageId, session.page.id)
        assertEquals(note, session.page.elements.single())
    }

    @Test fun `duplicated page gives its objects new identities`() {
        val session = DocumentSession(Board(name = "Pages"))
        session.add(Note(bounds = Bounds(0f, 0f, 200f, 200f), text = "A"))
        val original = session.page
        session.duplicatePage(original.id)
        assertNotEquals(original.id, session.page.id)
        assertNotEquals(original.elements.single().id, session.page.elements.single().id)
    }

    @Test fun `page reordering and background are reversible`() {
        val session = DocumentSession(Board(name = "Pages"))
        val first = session.page.id
        session.addPage()
        session.movePage(first, 1)
        assertEquals(first, session.board.pages.last().id)
        session.undo()
        assertEquals(first, session.board.pages.first().id)
        val before = session.page.background
        session.background(Background.RULED, 0xFF000000.toInt())
        session.undo()
        assertEquals(before, session.page.background)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `last page cannot be deleted`() {
        val session = DocumentSession(Board(name = "Pages"))
        session.deletePage(session.page.id)
    }
}
