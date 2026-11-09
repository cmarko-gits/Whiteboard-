package dev.whiteboard.ui

import android.os.Build
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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

private class DialogInputCloser {
    var close: () -> Unit = {}
}

/** Own the window-focus and IME-control handoffs until cleanup or owner detachment. */
@Suppress("DEPRECATION") // The owner can be another Dialog; its View identifies the correct controller.
private class OwnerInputReturn(private val view: View, private val closeInput: () -> Unit, private val hideReturnedInput: () -> Unit) :
    ViewTreeObserver.OnWindowFocusChangeListener,
    View.OnAttachStateChangeListener,
    WindowInsetsControllerCompat.OnControllableInsetsChangedListener {
    private var observer: ViewTreeObserver? = null
    private var controller: WindowInsetsControllerCompat? = null
    private var returned = false
    private var controlReceived = false
    private val finish = Runnable {
        if (!view.isAttachedToWindow || !view.hasWindowFocus() || view.rootView.findFocus()?.onCheckIsTextEditor() == true) {
            detach()
        } else {
            // Do not clear fresh focus or enqueue another Compose input command here.
            hideReturnedInput()
            if (Build.VERSION.SDK_INT >= 30 && !controlReceived) {
                if (controller == null) {
                    controller = ViewCompat.getWindowInsetsController(view)
                    controller?.addOnControllableInsetsChangedListener(this) ?: detach()
                }
            } else {
                detach()
            }
        }
    }

    fun close() {
        closeInput()
        if (!view.isAttachedToWindow || view.hasWindowFocus() || observer != null) return
        returned = false
        controlReceived = false
        observer = view.viewTreeObserver.also { it.addOnWindowFocusChangeListener(this) }
        view.addOnAttachStateChangeListener(this)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        if (!hasFocus) {
            if (returned) detach()
            return
        }
        returned = true
        // The platform restores its focused editor during window focus dispatch. Release
        // it after that dispatch, rather than clearing only the outgoing dialog's state.
        view.postOnAnimation(finish)
    }

    override fun onControllableInsetsChanged(controller: WindowInsetsControllerCompat, typeMask: Int) {
        if (this.controller == null || (typeMask and WindowInsetsCompat.Type.ime()) == 0) return
        controlReceived = true
        clearController()
        // Run after this control transaction is applied. API 30 can otherwise restore
        // an old requested-visible state after the preceding window-focus hide.
        view.post(finish)
    }

    private fun clearController() {
        val previous = controller
        controller = null
        previous?.removeOnControllableInsetsChangedListener(this)
    }

    private fun detach() {
        observer?.takeIf { it.isAlive }?.removeOnWindowFocusChangeListener(this)
        observer = null
        clearController()
        view.removeOnAttachStateChangeListener(this)
        view.removeCallbacks(finish)
    }

    override fun onViewAttachedToWindow(view: View) = Unit
    override fun onViewDetachedFromWindow(view: View) {
        detach()
    }
}

@Composable
private fun rememberOwnerInputCloser(): () -> Unit {
    val view = LocalView.current
    val currentClose by rememberUpdatedState(rememberInputCloser())
    val keyboard = remember(view) { SoftwareKeyboardControllerCompat(view) }
    val relay = remember(view, keyboard) { OwnerInputReturn(view, { currentClose() }, keyboard::hide) }
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
