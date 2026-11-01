package dev.whiteboard.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.whiteboard.app.ui.TextEditorDialog
import dev.whiteboard.canvas.CanvasEvent
import dev.whiteboard.core.model.*
import dev.whiteboard.ui.WhiteboardTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TextEditorStateTest {
    @get:Rule val compose = createComposeRule()

    @Test fun unsentMultilingualTextSurvivesSavedInstanceStateRestoration() {
        val restoration = StateRestorationTester(compose)
        val request = CanvasEvent.EditText(Point(20f, 30f))
        var submitted: String? = null
        restoration.setContent {
            WhiteboardTheme(ThemeMode.LIGHT) {
                TextEditorDialog(request, 0xFF123456.toInt(), { text, _, _, _, _, _ -> submitted = text }, {}, emptyList(), {})
            }
        }
        val value = "Sačuvaj tekst — сачувај белешку"
        compose.onNode(hasSetTextAction()).performTextReplacement(value)
        restoration.emulateSavedInstanceStateRestore()
        compose.onNode(hasSetTextAction()).assertTextContains(value)
        compose.onNodeWithText("Apply").performClick()
        compose.runOnIdle { assertEquals(value, submitted) }
    }
}
