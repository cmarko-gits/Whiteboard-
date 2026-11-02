package dev.whiteboard.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.whiteboard.ui.AdaptiveDialogContent
import dev.whiteboard.ui.InputDialog
import dev.whiteboard.ui.R
import kotlin.math.abs

@Composable
internal fun RotationDialog(busy: Boolean, sequence: Long, dismiss: () -> Unit, submit: (Double) -> Unit) {
    var angle by rememberSaveable { mutableStateOf("90") }
    var submitted by rememberSaveable { mutableStateOf(false) }
    val processing = busy || submitted
    val initialSequence = rememberSaveable { sequence }
    val degrees = angle.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && abs(it) <= 36_000 }
    val applyFocus = remember { FocusRequester() }
    val cancelFocus = remember { FocusRequester() }
    InputDialog({
        if (!processing) dismiss()
    }) { closeKeyboard ->
        LaunchedEffect(sequence) {
            if (sequence != initialSequence) {
                // Close the dialog's current input session after the busy-state transition,
                // while its window token is still attached, before removing the dialog.
                closeKeyboard()
                withFrameNanos { }
                closeKeyboard()
                dismiss()
            }
        }
        BoxWithConstraints(Modifier.systemBarsPadding().imePadding().padding(horizontal = 24.dp, vertical = 8.dp)) {
            val compact = maxHeight < 240.dp
            Surface(Modifier.widthIn(max = 560.dp).fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
                Column(Modifier.padding(if (compact) 8.dp else 24.dp)) {
                    if (!compact) {
                        Text(stringResource(R.string.rotate_selection), style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(12.dp))
                    }
                    AdaptiveDialogContent(
                        compact = compact,
                        input = {
                            OutlinedTextField(
                                value = angle,
                                onValueChange = { angle = it.take(18) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !processing,
                                singleLine = true,
                                isError = degrees == null,
                                label = { Text(stringResource(R.string.rotation_degrees)) },
                                supportingText = if (degrees == null &&
                                    !compact
                                ) {
                                    ({ Text(stringResource(R.string.rotation_invalid)) })
                                } else {
                                    null
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = {
                                    if (!processing) {
                                        closeKeyboard()
                                        if (degrees != null) applyFocus.requestFocus() else cancelFocus.requestFocus()
                                    }
                                })
                            )
                        },
                        actions = {
                            TextButton(
                                onClick = {
                                    closeKeyboard()
                                    dismiss()
                                },
                                enabled = !processing,
                                modifier = Modifier.weight(1f, fill = false).focusProperties {
                                    canFocus = !processing
                                }.focusRequester(cancelFocus)
                            ) { Text(stringResource(R.string.cancel)) }
                            Spacer(Modifier.width(8.dp))
                            TextButton(
                                onClick = {
                                    degrees?.let {
                                        submitted = true
                                        closeKeyboard()
                                        submit(it)
                                    }
                                },
                                enabled = degrees != null && !processing,
                                modifier = Modifier.weight(1f, fill = false).focusProperties {
                                    canFocus = degrees != null && !processing
                                }.focusRequester(applyFocus)
                            ) { Text(stringResource(if (processing) R.string.rotating else R.string.apply)) }
                        },
                        body = {
                            if (!compact) {
                                Column(
                                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(stringResource(R.string.rotation_help), style = MaterialTheme.typography.bodySmall)
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf(-90, 45, 90).forEach { value ->
                                            FilterChip(
                                                selected = degrees == value.toDouble(),
                                                onClick = {
                                                    angle = value.toString()
                                                },
                                                enabled = !processing,
                                                label = { Text("$value°") }
                                            )
                                        }
                                    }
                                    if (processing) LinearProgressIndicator(Modifier.fillMaxWidth())
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}
