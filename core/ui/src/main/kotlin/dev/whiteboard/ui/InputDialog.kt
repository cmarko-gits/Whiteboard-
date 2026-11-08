package dev.whiteboard.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.SoftwareKeyboardControllerCompat

private class DialogInputCloser {
    var close: () -> Unit = {}
}

/** Call from the window that owns the input, including an AlertDialog content/button slot. */
@Composable
fun rememberInputCloser(): () -> Unit {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val view = LocalView.current
    val platformKeyboard = remember(view) { SoftwareKeyboardControllerCompat(view) }
    return remember(focus, keyboard, platformKeyboard) {
        {
            focus.clearFocus(force = true)
            keyboard?.hide()
            // Android 11+ owns IME visibility through this window's insets controller;
            // AndroidX also handles legacy windows and unavailable insets control.
            platformKeyboard.hide()
        }
    }
}

/** Clear both windows' requested IME state before focus returns to the owner. */
@Composable
private fun rememberDialogInputCloser(ownerClose: () -> Unit): () -> Unit {
    val dialogClose = rememberInputCloser()
    return remember(dialogClose, ownerClose) {
        {
            dialogClose()
            ownerClose()
        }
    }
}

/** Clears the dialog's own input session before its window is removed. */
@Composable
fun InputDialog(dismiss: () -> Unit, content: @Composable (closeKeyboard: () -> Unit) -> Unit) {
    val input = remember { DialogInputCloser() }
    val ownerClose = rememberInputCloser()
    Dialog(
        onDismissRequest = {
            input.close()
            dismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        val closeKeyboard = rememberDialogInputCloser(ownerClose)
        SideEffect { input.close = closeKeyboard }
        DisposableEffect(input, ownerClose) {
            onDispose {
                ownerClose()
                input.close = {}
            }
        }
        content(closeKeyboard)
    }
}

/** Material alert variant with close actions bound to the alert's own input window. */
@Composable
fun InputAlertDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable (closeKeyboard: () -> Unit) -> Unit,
    confirmButton: @Composable (closeKeyboard: () -> Unit) -> Unit,
    dismissButton: @Composable (closeKeyboard: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    properties: DialogProperties = DialogProperties()
) {
    val input = remember { DialogInputCloser() }
    val ownerClose = rememberInputCloser()
    AlertDialog(
        onDismissRequest = {
            input.close()
            onDismissRequest()
        },
        modifier = modifier,
        properties = properties,
        title = title,
        text = {
            val closeKeyboard = rememberDialogInputCloser(ownerClose)
            SideEffect { input.close = closeKeyboard }
            DisposableEffect(input, ownerClose) {
                onDispose {
                    ownerClose()
                    input.close = {}
                }
            }
            text(closeKeyboard)
        },
        confirmButton = { confirmButton(rememberDialogInputCloser(ownerClose)) },
        dismissButton = { dismissButton(rememberDialogInputCloser(ownerClose)) }
    )
}
