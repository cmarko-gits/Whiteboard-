package dev.whiteboard.app

import androidx.compose.foundation.layout.Row
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.whiteboard.core.model.ThemeMode
import dev.whiteboard.ui.ColorButton
import dev.whiteboard.ui.CustomColorDialog
import dev.whiteboard.ui.WhiteboardTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ColorPickerComposeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun saturationCanBeChangedWithoutATouchDragAndApplied() {
        val original = 0xFF808080.toInt()
        var chosen: Int? = null
        compose.setContent { WhiteboardTheme(ThemeMode.LIGHT) { CustomColorDialog(original, emptyList(), {}, {}, { chosen = it }) } }
        val actions = compose.onNodeWithContentDescription(
            "Saturation and brightness"
        ).fetchSemanticsNode().config[SemanticsActions.CustomActions]
        compose.runOnIdle { assertTrue(actions.first { it.label == "Increase saturation" }.action()) }
        compose.onNodeWithText("Apply").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.runOnIdle {
            assertNotNull(chosen)
            assertNotEquals(original, chosen)
        }
    }

    @Test fun paletteButtonsExposeSelectionAndAtLeast48DpTargets() {
        compose.setContent {
            WhiteboardTheme(ThemeMode.LIGHT) {
                Row {
                    ColorButton(0xFF123456.toInt(), true) {}
                    ColorButton(0xFFABCDEF.toInt(), false) {}
                }
            }
        }
        compose.onAllNodes(isSelected()).assertCountEquals(1)
        compose.onNodeWithContentDescription("Color #123456").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
    }

    @Test fun invalidInputDisablesApplyWithoutChangingTheSelectedColor() {
        var selected: Int? = null
        compose.setContent {
            WhiteboardTheme(ThemeMode.LIGHT) {
                CustomColorDialog(0xFF123456.toInt(), emptyList(), {}, {}, { selected = it })
            }
        }
        compose.onNode(hasSetTextAction()).performTextReplacement("GGGGGG")
        compose.onNodeWithText("Apply").assertIsNotEnabled()
        compose.onNodeWithText("Enter 6 hexadecimal digits, for example 12AB34.").assertExists()
        compose.runOnIdle { assertNull(selected) }
    }

    @Test fun cancellationDoesNotPersistStagedFavorites() {
        var palette: List<Int> = emptyList()
        var submitted = false
        var dismissed = false
        compose.setContent {
            WhiteboardTheme(ThemeMode.LIGHT) {
                CustomColorDialog(0xFF123456.toInt(), palette, { palette = it }, { dismissed = true }, { submitted = true })
            }
        }
        compose.onNodeWithText("Save color").performScrollTo().performClick()
        compose.onNodeWithText("Remove saved color").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle {
            assertTrue(dismissed)
            assertFalse(submitted)
            assertTrue(palette.isEmpty())
        }
    }

    @Test fun applyCommitsExactHexAndFavoriteTogether() {
        var selected: Int? = null
        var palette: List<Int> = emptyList()
        compose.setContent {
            WhiteboardTheme(ThemeMode.LIGHT) {
                CustomColorDialog(0xFF123456.toInt(), palette, { palette = it }, {}, { selected = it })
            }
        }
        compose.onNode(hasSetTextAction()).performTextReplacement("12ab34")
        compose.onNodeWithText("Save color").performScrollTo().performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.onNodeWithText("Remove saved color").assertExists()
        compose.onNodeWithText("Apply").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.runOnIdle {
            assertEquals(0xFF12AB34.toInt(), selected)
            assertEquals(listOf(0xFF12AB34.toInt()), palette)
        }
    }
}
