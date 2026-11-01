package dev.whiteboard.ui

import android.view.inputmethod.InputMethodManager
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private class DialogInputCloser {
    var close: () -> Unit = {}
}

/** Call from the window that owns the input, including an AlertDialog content/button slot. */
@Composable
fun rememberInputCloser(): () -> Unit {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val view = LocalView.current
    return remember(focus, keyboard, view) {
        {
            focus.clearFocus(force = true)
            keyboard?.hide()
            view.windowToken?.let { token ->
                view.context.getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(token, 0)
            }
            Unit
        }
    }
}

/** Clears the dialog's own input session before its window is removed. */
@Composable
fun InputDialog(dismiss: () -> Unit, content: @Composable (closeKeyboard: () -> Unit) -> Unit) {
    val input = remember { DialogInputCloser() }
    Dialog(
        onDismissRequest = {
            input.close()
            dismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        val closeKeyboard = rememberInputCloser()
        SideEffect { input.close = closeKeyboard }
        DisposableEffect(input) { onDispose { input.close = {} } }
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
    AlertDialog(
        onDismissRequest = {
            input.close()
            onDismissRequest()
        },
        modifier = modifier,
        properties = properties,
        title = title,
        text = {
            val closeKeyboard = rememberInputCloser()
            SideEffect { input.close = closeKeyboard }
            DisposableEffect(input) { onDispose { input.close = {} } }
            text(closeKeyboard)
        },
        confirmButton = { confirmButton(rememberInputCloser()) },
        dismissButton = { dismissButton(rememberInputCloser()) }
    )
}
