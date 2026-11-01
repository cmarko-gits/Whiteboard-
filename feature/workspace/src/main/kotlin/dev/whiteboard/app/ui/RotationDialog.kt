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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
    val initialSequence = rememberSaveable { sequence }
    val degrees = angle.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && abs(it) <= 36_000 }
    val applyFocus = remember { FocusRequester() }
    val cancelFocus = remember { FocusRequester() }
    LaunchedEffect(sequence) { if (sequence != initialSequence) dismiss() }
    InputDialog({
        if (!busy) dismiss()
    }) { closeKeyboard ->
        val focus = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
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
                                enabled = !busy,
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
                                    if (!busy) {
                                        focus.clearFocus(force = true)
                                        if (degrees != null) applyFocus.requestFocus() else cancelFocus.requestFocus()
                                        keyboard?.hide()
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
                                enabled = !busy,
                                modifier = Modifier.weight(1f, fill = false).focusProperties {
                                    canFocus = !busy
                                }.focusRequester(cancelFocus)
                            ) { Text(stringResource(R.string.cancel)) }
                            Spacer(Modifier.width(8.dp))
                            TextButton(
                                onClick = {
                                    degrees?.let {
                                        closeKeyboard()
                                        submit(it)
                                    }
                                },
                                enabled = degrees != null && !busy,
                                modifier = Modifier.weight(1f, fill = false).focusProperties {
                                    canFocus = degrees != null && !busy
                                }.focusRequester(applyFocus)
                            ) { Text(stringResource(if (busy) R.string.rotating else R.string.apply)) }
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
                                                enabled = !busy,
                                                label = { Text("$value°") }
                                            )
                                        }
                                    }
                                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}
