package dev.whiteboard.core.model

object SavedColorPalette {
    const val LIMIT = 12
    fun normalize(colors: List<Int>): List<Int> = colors.map { it or 0xFF000000.toInt() }.distinct().take(LIMIT)
}
