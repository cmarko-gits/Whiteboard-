package dev.whiteboard.core

import dev.whiteboard.core.model.*
import org.junit.Assert.*
import org.junit.Test

class TemplatesAndSearchTest {
    @Test fun `all templates produce valid editable objects with unique identities`() {
        BoardTemplate.entries.forEach { template ->
            val first = TemplateFactory.create("Plan", template)
            val second = TemplateFactory.create("Plan", template)
            assertNotEquals(first.id, second.id)
            assertEquals(first.activePage.elements.size, first.activePage.elements.map { it.id }.distinct().size)
            assertTrue(first.activePage.elements.none { it is MediaElement })
            assertTrue(first.activePage.elements.map { it.id }.intersect(second.activePage.elements.map { it.id }.toSet()).isEmpty())
        }
    }

    @Test fun `search locates text on a page that was not active`() {
        val first = Page()
        val note = Note(bounds = Bounds(500f, 300f, 700f, 500f), text = "Čas za đake")
        val second = Page(name = "Study", elements = listOf(note))
        val board = Board(name = "Notes", pages = listOf(first, second), activePageId = first.id)
        val match = requireNotNull(board.firstTextMatch("cas za djake"))
        assertEquals(second.id, match.pageId)
        assertEquals(note.id, match.element?.id)
    }
}
