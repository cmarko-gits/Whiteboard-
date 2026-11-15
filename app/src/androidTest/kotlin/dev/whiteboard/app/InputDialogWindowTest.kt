package dev.whiteboard.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextRange
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
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
        var replaced by mutableStateOf(false)
        compose.setContent {
            WhiteboardTheme(ThemeMode.LIGHT) {
                var owner by remember { mutableStateOf("Owner") }
                var child by remember { mutableStateOf("") }
                Column {
                    OutlinedTextField(owner, { owner = it }, modifier = Modifier.testTag("owner-input"))
                    TextButton(onClick = { visible = true }) { Text("Open dialog") }
                }
                if (visible) {
                    val currentAlert = alert xor replaced
                    val title = if (currentAlert) "Alert input window" else "Plain input window"
                    val input: @Composable () -> Unit = {
                        OutlinedTextField(child, { child = it }, modifier = Modifier.testTag("dialog-input"))
                    }
                    val confirm: @Composable (() -> Unit) -> Unit = { close ->
                        Column {
                            TextButton(onClick = {
                                close()
                                replaced = !replaced
                            }) { Text("Replace dialog") }
                            TextButton(onClick = {
                                close()
                                visible = false
                            }) { Text("Apply dialog") }
                        }
                    }
                    if (currentAlert) {
                        InputAlertDialog(
                            onDismissRequest = { visible = false },
                            title = { Text(title) },
                            text = { input() },
                            confirmButton = { confirm(it) },
                            dismissButton = {}
                        )
                    } else {
                        InputDialog({ visible = false }) { close ->
                            // InputDialog callers own insets, just like the production map panels.
                            Surface(Modifier.imePadding()) {
                                Column {
                                    Text(title)
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
            awaitKeyboardShown(device)
            compose.onNodeWithText("Open dialog").performClick()
            compose.onNodeWithTag("dialog-input").performClick().performTextInput("Child")
            compose.onNodeWithTag("dialog-input").assertIsFocused()
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            check(!activateConnectedUiControl(instrumentation.uiAutomation, "dev.whiteboard.other", "Replace dialog")) {
                "A different package must not receive a dialog action"
            }
            compose.waitForIdle()
            repeat(4) {
                clickConnectedUiControl(instrumentation.uiAutomation, instrumentation.targetContext.packageName, "Replace dialog")
                // UiAutomator does not advance the Compose rule's controlled clock.
                compose.waitForIdle()
                val title = if (alert xor (it % 2 == 0)) "Alert input window" else "Plain input window"
                check(device.wait(Until.hasObject(By.text(title)), 10_000)) { "Replacement window did not open" }
                checkNotNull(device.wait(Until.findObject(By.text("Child").clazz("android.widget.EditText")), 10_000)).click()
                compose.waitForIdle()
                awaitKeyboardShown(device)
                compose.onNodeWithTag("dialog-input").assertIsFocused().assertTextContains("Child")
            }
            compose.onNodeWithText("Apply dialog").performClick()
            compose.onNodeWithTag("dialog-input").assertDoesNotExist()
            // Legacy View focus restoration can legitimately retain this field's focus;
            // visibility and explicit reactivation are the cross-version input contract.
            compose.onNodeWithTag("owner-input").assertTextContains("Owner")
            awaitTestKeyboardHidden(device)
            compose.onNodeWithTag("owner-input").performClick()
            awaitKeyboardShown(device)
            compose.onNodeWithTag("owner-input").performTextInputSelection(TextRange("Owner".length))
            compose.onNodeWithTag("owner-input").performTextInput(" resumed")
            compose.onNodeWithTag("owner-input").assertTextContains("Owner resumed")
        } catch (error: Throwable) {
            CaptureUiFailure().snapshot("input-window-${if (alert) "alert" else "plain"}", includeWindowState = true)
            throw error
        }
    }

    private fun awaitKeyboardShown(device: UiDevice) {
        val shown = Regex("\\bmInputShown=true\\b")
        // Window focus/input callbacks arrive outside Compose. A physical click can
        // enqueue Compose input work after waitForIdle returns; keep advancing the
        // controlled clock while also yielding to the real platform IME.
        compose.waitUntil("Explicit input must show the real platform keyboard", timeoutMillis = 10_000) {
            shown.containsMatchIn(device.executeShellCommand("dumpsys input_method"))
        }
    }
}
