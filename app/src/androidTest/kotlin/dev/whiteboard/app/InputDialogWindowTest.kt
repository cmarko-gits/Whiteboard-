package dev.whiteboard.app

import android.os.SystemClock
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import dev.whiteboard.core.model.ThemeMode
import dev.whiteboard.ui.InputAlertDialog
import dev.whiteboard.ui.InputDialog
import dev.whiteboard.ui.WhiteboardTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises real IME ownership across the Activity and a separate Compose dialog window. */
@RunWith(AndroidJUnit4::class)
class InputDialogWindowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun ownerKeyboardDoesNotReturnAfterInputDialogConfirmation() = assertWindowRelease(alert = false)

    @Test fun ownerKeyboardDoesNotReturnAfterAlertDialogConfirmation() = assertWindowRelease(alert = true)

    private fun assertWindowRelease(alert: Boolean) {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        var visible by mutableStateOf(false)
        compose.setContent {
            WhiteboardTheme(ThemeMode.LIGHT) {
                var owner by remember { mutableStateOf("Owner") }
                var child by remember { mutableStateOf("") }
                Column {
                    OutlinedTextField(owner, { owner = it }, modifier = Modifier.testTag("owner-input"))
                    TextButton(onClick = { visible = true }) { Text("Open dialog") }
                }
                if (visible) {
                    val input: @Composable () -> Unit = {
                        OutlinedTextField(child, { child = it }, modifier = Modifier.testTag("dialog-input"))
                    }
                    val confirm: @Composable (() -> Unit) -> Unit = { close ->
                        TextButton(onClick = {
                            close()
                            visible = false
                        }) { Text("Apply dialog") }
                    }
                    if (alert) {
                        InputAlertDialog(
                            onDismissRequest = { visible = false },
                            title = { Text("Input window") },
                            text = { input() },
                            confirmButton = { confirm(it) },
                            dismissButton = {}
                        )
                    } else {
                        InputDialog({ visible = false }) { close ->
                            Surface {
                                Column {
                                    input()
                                    confirm(close)
                                }
                            }
                        }
                    }
                }
            }
        }
        try {
            compose.onNodeWithTag("owner-input").performClick()
            val deadline = SystemClock.uptimeMillis() + 10_000
            val shown = Regex("\\bmInputShown=true\\b")
            while (!shown.containsMatchIn(device.executeShellCommand("dumpsys input_method")) && SystemClock.uptimeMillis() < deadline) {
                SystemClock.sleep(50)
            }
            check(shown.containsMatchIn(device.executeShellCommand("dumpsys input_method"))) { "Owner keyboard never became visible" }
            compose.onNodeWithText("Open dialog").performClick()
            compose.onNodeWithTag("dialog-input").performClick().performTextInput("Child")
            compose.onNodeWithTag("dialog-input").assertIsFocused()
            compose.onNodeWithText("Apply dialog").performClick()
            compose.onNodeWithTag("dialog-input").assertDoesNotExist()
            compose.waitUntil(10_000) {
                !compose.onNodeWithTag("owner-input").fetchSemanticsNode().config[SemanticsProperties.Focused]
            }
            compose.onNodeWithTag("owner-input").assertIsNotFocused()
            awaitTestKeyboardHidden(device)
        } catch (error: Throwable) {
            CaptureUiFailure().snapshot("input-window-${if (alert) "alert" else "plain"}")
            throw error
        }
    }
}
