package dev.whiteboard.ui

import android.view.View
import android.view.ViewTreeObserver
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

/** One-shot cleanup after Android restores the owner's focus, owned by its attached View. */
private class OwnerInputReturn(private val view: View, private val closeInput: () -> Unit) :
    ViewTreeObserver.OnWindowFocusChangeListener,
    View.OnAttachStateChangeListener {
    private var observer: ViewTreeObserver? = null
    private val finish = Runnable {
        view.removeOnAttachStateChangeListener(this)
        if (view.isAttachedToWindow && view.hasWindowFocus()) closeInput()
    }

    fun close() {
        closeInput()
        if (!view.isAttachedToWindow || view.hasWindowFocus() || observer != null) return
        observer = view.viewTreeObserver.also { it.addOnWindowFocusChangeListener(this) }
        view.addOnAttachStateChangeListener(this)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        if (!hasFocus) return
        observer?.takeIf { it.isAlive }?.removeOnWindowFocusChangeListener(this)
        observer = null
        // The platform restores its focused editor during window focus dispatch. Release
        // it after that dispatch, rather than clearing only the outgoing dialog's state.
        view.postOnAnimation(finish)
    }

    private fun detach() {
        observer?.takeIf { it.isAlive }?.removeOnWindowFocusChangeListener(this)
        observer = null
        view.removeOnAttachStateChangeListener(this)
    }

    override fun onViewAttachedToWindow(view: View) = Unit
    override fun onViewDetachedFromWindow(view: View) {
        detach()
        view.removeCallbacks(finish)
    }
}

@Composable
private fun rememberOwnerInputCloser(): () -> Unit {
    val view = LocalView.current
    val currentClose by rememberUpdatedState(rememberInputCloser())
    val relay = remember(view) { OwnerInputReturn(view) { currentClose() } }
    return remember(relay) { { relay.close() } }
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
    val ownerClose = rememberOwnerInputCloser()
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
    val ownerClose = rememberOwnerInputCloser()
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
