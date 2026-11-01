package dev.whiteboard.data

import dev.whiteboard.core.model.SavedColorPalette
import java.util.Locale

object FavoriteColors {
    const val LIMIT = SavedColorPalette.LIMIT

    fun normalize(colors: List<Int>): List<Int> = SavedColorPalette.normalize(colors)

    fun encode(colors: List<Int>): String = normalize(colors).joinToString(",") { String.format(Locale.ROOT, "%06X", it and 0xFFFFFF) }

    fun decode(source: String?): List<Int> = normalize(
        source.orEmpty().take(1024).split(',').mapNotNull {
            it.takeIf { token -> token.matches(Regex("[a-fA-F0-9]{6}")) }?.toIntOrNull(16)
        }
    )
}
