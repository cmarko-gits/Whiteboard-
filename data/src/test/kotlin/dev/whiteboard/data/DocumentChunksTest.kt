package dev.whiteboard.data

import org.junit.Assert.*
import org.junit.Test

class DocumentChunksTest {
    @Test fun `chunk boundaries retain supplementary Unicode characters`() {
        val source = "a".repeat(DOCUMENT_CHUNK_CHARACTERS - 1) + "🙂" + "b".repeat(DOCUMENT_CHUNK_CHARACTERS)
        val chunks = chunkDocument(source)
        assertEquals(source, chunks.joinToString(""))
        assertTrue(chunks.dropLast(1).none { it.last().isHighSurrogate() })
    }

    @Test fun `search is case and Serbian Latin diacritic insensitive`() {
        assertEquals("cas i djak", searchKey("ČAS i ĐAK"))
        assertEquals("ћирилица", searchKey("ЋИРИЛИЦА"))
    }
}
