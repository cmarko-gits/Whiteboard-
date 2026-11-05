package dev.whiteboard.core.model

import java.text.Normalizer
import java.util.Locale

fun normalizeForSearch(text: String): String = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").replace("đ", "dj")

data class BoardMatch(val pageId: String, val element: PageObject? = null)

fun Board.firstTextMatch(query: String?): BoardMatch? {
    val key = query?.let(::normalizeForSearch)?.takeIf { it.isNotBlank() } ?: return null
    if (normalizeForSearch(name).contains(key)) return BoardMatch(activePageId)
    pages.forEach { page ->
        if (normalizeForSearch(page.name).contains(key)) return BoardMatch(page.id)
        page.elements.forEach { element ->
            val text = element.leaves().map { it.content() }.joinToString("\n") {
                when (it) {
                    is TextElement -> it.text
                    is Note -> it.text
                    else -> ""
                }
            }
            if (normalizeForSearch(text).contains(key)) return BoardMatch(page.id, element)
        }
    }
    return null
}
