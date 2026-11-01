package dev.whiteboard.app

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.createComposeRule
import dev.whiteboard.core.model.ThemeMode
import dev.whiteboard.ui.WhiteboardTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ThemeContrastTest {
    @get:Rule val compose = createComposeRule()

    @Test fun textAndControlOutlinesHaveContrastInBothApplicationThemes() {
        val schemes = mutableMapOf<ThemeMode, ColorScheme>()
        compose.setContent {
            listOf(ThemeMode.LIGHT, ThemeMode.DARK).forEach { mode ->
                WhiteboardTheme(mode) {
                    val colors = MaterialTheme.colorScheme
                    SideEffect { schemes[mode] = colors }
                }
            }
        }
        compose.runOnIdle {
            assertEquals(2, schemes.size)
            schemes.forEach { (mode, colors) ->
                val textPairs = listOf(
                    "background" to (colors.onBackground to colors.background),
                    "surface" to (colors.onSurface to colors.surface),
                    "surface variant" to (colors.onSurfaceVariant to colors.surfaceVariant),
                    "primary" to (colors.onPrimary to colors.primary),
                    "primary container" to (colors.onPrimaryContainer to colors.primaryContainer),
                    "secondary" to (colors.onSecondary to colors.secondary),
                    "secondary container" to (colors.onSecondaryContainer to colors.secondaryContainer)
                )
                textPairs.forEach { (name, pair) ->
                    assertTrue("$mode $name text contrast", contrast(pair.first, pair.second) >= 4.5)
                }
                listOf(colors.surface, colors.surfaceContainer, colors.surfaceContainerHigh, colors.surfaceContainerHighest).forEach {
                    assertTrue("$mode secondary text on container", contrast(colors.onSurfaceVariant, it) >= 4.5)
                    assertTrue("$mode control outline", contrast(colors.outline, it) >= 3.0)
                }
            }
        }
    }

    private fun contrast(first: Color, second: Color): Double {
        val a = first.luminance().toDouble()
        val b = second.luminance().toDouble()
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }
}
