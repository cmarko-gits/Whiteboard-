package dev.whiteboard.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.whiteboard.core.editor.ObjectAlignment
import dev.whiteboard.ui.R

@Composable
internal fun AlignmentDialog(busy: Boolean, sequence: Long, dismiss: () -> Unit, submit: (ObjectAlignment) -> Unit) {
    var submitted by rememberSaveable { mutableStateOf(false) }
    val processing = busy || submitted
    val initialSequence = rememberSaveable { sequence }
    LaunchedEffect(sequence) { if (sequence != initialSequence) dismiss() }
    AlertDialog(
        onDismissRequest = { if (!processing) dismiss() },
        title = { Text(stringResource(R.string.alignment)) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(stringResource(if (processing) R.string.aligning else R.string.alignment_help))
                ObjectAlignment.entries.forEach { alignment ->
                    val label = when (alignment) {
                        ObjectAlignment.LEFT -> R.string.align_left
                        ObjectAlignment.HORIZONTAL_CENTER -> R.string.align_horizontal_center
                        ObjectAlignment.RIGHT -> R.string.align_right
                        ObjectAlignment.TOP -> R.string.align_top
                        ObjectAlignment.VERTICAL_CENTER -> R.string.align_vertical_center
                        ObjectAlignment.BOTTOM -> R.string.align_bottom
                    }
                    TextButton(
                        enabled = !processing,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        onClick = {
                            submitted = true
                            submit(alignment)
                        }
                    ) { Text(stringResource(label)) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !processing, onClick = dismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
