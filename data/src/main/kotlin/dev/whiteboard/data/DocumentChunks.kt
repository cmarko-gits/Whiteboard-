package dev.whiteboard.data

import dev.whiteboard.core.model.normalizeForSearch

const val DOCUMENT_CHUNK_CHARACTERS = 131_072

/** Keep every SQLite row below CursorWindow limits, without splitting a UTF-16 surrogate pair. */
fun chunkDocument(text: String): List<String> {
    val chunks = mutableListOf<String>()
    var start = 0
    while (start < text.length) {
        var end = minOf(start + DOCUMENT_CHUNK_CHARACTERS, text.length)
        if (end < text.length && text[end - 1].isHighSurrogate()) end--
        chunks.add(text.substring(start, end))
        start = end
    }
    return chunks
}

fun searchKey(text: String): String = normalizeForSearch(text)
