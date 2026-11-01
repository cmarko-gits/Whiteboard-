package dev.whiteboard.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.whiteboard.core.model.BrushPreset
import dev.whiteboard.ui.*
import dev.whiteboard.ui.R

@Composable
fun BrushDialog(
    presets: List<BrushPreset>,
    canSave: Boolean,
    dismiss: () -> Unit,
    onSelect: (BrushPreset) -> Unit,
    onSave: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = dismiss, title = { Text(stringResource(R.string.favorite_tools)) }, text = {
        Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
            presets.forEach { preset ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.size(20.dp).background(Color(preset.ink.color), CircleShape))
                    Text(preset.name, Modifier.weight(1f).clickable { onSelect(preset) }.padding(12.dp))
                    IconButton(onClick = { onDelete(preset.id) }) { Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.delete)) }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Text(stringResource(R.string.save_current_tool), style = MaterialTheme.typography.labelMedium)
            OutlinedTextField(name, { name = it.take(40) }, label = { Text(stringResource(R.string.preset_name)) }, singleLine = true)
            TextButton(onClick = {
                onSave(name)
                name = ""
            }, enabled = canSave && name.isNotBlank() && presets.size < 12) {
                Text(stringResource(R.string.save))
            }
        }
    }, confirmButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.done)) } })
}
