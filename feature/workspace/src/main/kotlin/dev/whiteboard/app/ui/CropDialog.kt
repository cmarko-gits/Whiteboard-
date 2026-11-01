package dev.whiteboard.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.whiteboard.core.model.*
import dev.whiteboard.ui.*
import dev.whiteboard.ui.R

@Composable
fun CropDialog(media: MediaElement, dismiss: () -> Unit, submit: (Bounds) -> Unit) {
    var left by rememberSaveable(media.id) { mutableFloatStateOf(media.crop.left) }
    var top by rememberSaveable(media.id) { mutableFloatStateOf(media.crop.top) }
    var right by rememberSaveable(media.id) { mutableFloatStateOf(media.crop.right) }
    var bottom by rememberSaveable(media.id) { mutableFloatStateOf(media.crop.bottom) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(R.string.crop)) },
        text = {
            Column {
                val horizontalLabel = stringResource(R.string.crop_horizontal)
                val verticalLabel = stringResource(R.string.crop_vertical)
                Text(horizontalLabel)
                RangeSlider(
                    left..right,
                    {
                        left = it.start
                        right = maxOf(it.endInclusive, it.start + 0.05f).coerceAtMost(1f)
                    },
                    valueRange =
                    0f..1f,
                    modifier = Modifier.semantics { contentDescription = horizontalLabel }
                )
                Text(verticalLabel)
                RangeSlider(
                    top..bottom,
                    {
                        top = it.start
                        bottom = maxOf(it.endInclusive, it.start + 0.05f).coerceAtMost(1f)
                    },
                    valueRange =
                    0f..1f,
                    modifier = Modifier.semantics { contentDescription = verticalLabel }
                )
                TextButton(onClick = {
                    left = 0f
                    top = 0f
                    right = 1f
                    bottom = 1f
                }) { Text(stringResource(R.string.reset)) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                submit(Bounds(left, top, right, bottom))
            }, enabled = right > left && bottom > top) { Text(stringResource(R.string.apply)) }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
