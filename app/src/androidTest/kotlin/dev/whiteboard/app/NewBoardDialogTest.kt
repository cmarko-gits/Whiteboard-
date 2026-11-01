package dev.whiteboard.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.whiteboard.app.ui.NewBoardDialog
import dev.whiteboard.core.model.ThemeMode
import dev.whiteboard.ui.WhiteboardTheme
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NewBoardDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test fun doneClearsDialogFocusWithoutSubmittingOrLosingTheName() {
        var submitted = false
        var dismissed = false
        compose.setContent {
            WhiteboardTheme(ThemeMode.LIGHT) {
                NewBoardDialog({ dismissed = true }) { _, _ -> submitted = true }
            }
        }
        val input = compose.onNode(hasSetTextAction())
        input.performClick().performTextInput("Draft name")
        input.assertIsFocused().performImeAction()
        input.assertIsNotFocused().assertTextContains("Draft name")
        compose.onNodeWithText("Confirm").assertIsEnabled().assertIsFocused()
        compose.runOnIdle {
            assertFalse(submitted)
            assertFalse(dismissed)
        }
    }
}
