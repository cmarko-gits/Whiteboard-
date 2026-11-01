package dev.whiteboard.ui

import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private class DialogInputCloser {
    var close: () -> Unit = {}
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
        val focus = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        val view = LocalView.current
        val closeKeyboard = {
            focus.clearFocus(force = true)
            keyboard?.hide()
            // Compose may queue hiding until a frame in which this window is already gone.
            view.windowToken?.let { token ->
                view.context.getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(token, 0)
            }
            Unit
        }
        SideEffect { input.close = closeKeyboard }
        DisposableEffect(input) { onDispose { input.close = {} } }
        content(closeKeyboard)
    }
}
