package dev.whiteboard.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.whiteboard.app.workspace.EditorUi
import dev.whiteboard.app.workspace.WorkspaceViewModel
import dev.whiteboard.core.editor.LayerOrdering
import dev.whiteboard.core.editor.LayerStep
import dev.whiteboard.core.model.*
import dev.whiteboard.ui.*
import dev.whiteboard.ui.R

@Composable
internal fun SelectionActions(editor: EditorUi, viewModel: WorkspaceViewModel, onProperties: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var alignment by rememberSaveable { mutableStateOf(false) }
    val workspace by viewModel.state.collectAsState()
    val transforming = workspace.rotating || workspace.aligning
    Text(pluralStringResource(R.plurals.objects_selected, editor.selectedIds.size, editor.selectedIds.size))
    Box {
        IconButton(onClick = { expanded = true }) { Icon(Icons.Outlined.Tune, stringResource(R.string.selection_actions)) }
        DropdownMenu(expanded, { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.rotate)) },
                enabled = !transforming,
                onClick = {
                    expanded = false
                    viewModel.rotateSelected()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.alignment)) },
                enabled = !transforming && editor.page.elements.count { it.id in editor.selectedIds } >= 2,
                onClick = {
                    expanded = false
                    alignment = true
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.bring_forward)) },
                enabled = LayerOrdering.canStep(editor.page.elements, editor.selectedIds, LayerStep.FORWARD),
                onClick = {
                    expanded = false
                    viewModel.stepSelectedLayer(LayerStep.FORWARD)
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.send_backward)) },
                enabled = LayerOrdering.canStep(editor.page.elements, editor.selectedIds, LayerStep.BACKWARD),
                onClick = {
                    expanded = false
                    viewModel.stepSelectedLayer(LayerStep.BACKWARD)
                }
            )
            DropdownMenuItem(text = { Text(stringResource(R.string.group)) }, enabled = editor.selectedIds.size > 1, onClick = {
                expanded = false
                viewModel.groupSelected()
            })
            DropdownMenuItem(
                text = { Text(stringResource(R.string.ungroup)) },
                enabled = editor.page.elements.any { it.id in editor.selectedIds && it is ElementGroup },
                onClick = {
                    expanded = false
                    viewModel.ungroupSelected()
                }
            )
            DropdownMenuItem(text = { Text(stringResource(R.string.copy)) }, onClick = {
                expanded = false
                viewModel.copySelected()
            })
            DropdownMenuItem(text = { Text(stringResource(R.string.cut)) }, onClick = {
                expanded = false
                viewModel.copySelected(cut = true)
            })
            DropdownMenuItem(text = { Text(stringResource(R.string.bring_front)) }, onClick = {
                expanded = false
                viewModel.reorderSelected(true)
            })
            DropdownMenuItem(text = { Text(stringResource(R.string.send_back)) }, onClick = {
                expanded = false
                viewModel.reorderSelected(false)
            })
            DropdownMenuItem(
                text = { Text(stringResource(R.string.properties)) },
                enabled = editor.page.elements.filter { it.id in editor.selectedIds }.flatMap { it.leaves() }
                    .map { it.content() }.any { it is Stroke || it is Shape || it is Line },
                onClick = {
                    expanded = false
                    onProperties()
                }
            )
        }
    }
    if (alignment) {
        AlignmentDialog(transforming, workspace.alignmentSequence, { alignment = false }) { viewModel.alignSelected(it) }
    }
}

@Composable
internal fun ObjectStyleDialog(
    elements: List<Element>,
    dismiss: () -> Unit,
    favoriteColors: List<Int>,
    saveFavorites: (List<Int>) -> Unit,
    apply: (InkStyle, Int?) -> Unit
) {
    val original = elements.firstNotNullOfOrNull {
        when (it) {
            is Stroke -> it.style
            is Shape -> it.style
            is Line -> it.style
            else -> null
        }
    } ?: InkStyle()
    val saver = remember {
        listSaver<InkStyle, Int>(
            save = { listOf(it.color, it.width.toBits(), it.opacity.toBits()) },
            restore = { InkStyle(it[0], Float.fromBits(it[1]), Float.fromBits(it[2])) }
        )
    }
    var ink by rememberSaveable(stateSaver = saver) { mutableStateOf(original) }
    var fill by rememberSaveable { mutableStateOf(elements.filterIsInstance<Shape>().firstOrNull()?.fill) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(R.string.properties)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ColorChoiceRow(ink.color, inkColors, favoriteColors, saveFavorites) { ink = ink.copy(color = it) }
                val widthLabel = stringResource(R.string.width)
                val opacityLabel = stringResource(R.string.opacity)
                Text(widthLabel, Modifier.padding(top = 12.dp))
                Slider(
                    ink.width.coerceIn(0.1f, 200f),
                    {
                        ink = ink.copy(width = it)
                    },
                    valueRange = 0.1f..200f,
                    modifier = Modifier.semantics {
                        contentDescription =
                            widthLabel
                    }
                )
                Text(opacityLabel)
                Slider(
                    ink.opacity,
                    { ink = ink.copy(opacity = it) },
                    valueRange = 0f..1f,
                    modifier = Modifier.semantics {
                        contentDescription =
                            opacityLabel
                    }
                )
                if (elements.any { it is Shape }) {
                    Row {
                        Checkbox(fill != null, { fill = if (it) ink.color else null })
                        Text(stringResource(R.string.fill_shape), Modifier.padding(top = 12.dp))
                    }
                    if (fill != null) {
                        ColorChoiceRow(fill ?: ink.color, noteColors, favoriteColors, saveFavorites) { fill = it }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { apply(ink, fill) }) { Text(stringResource(R.string.confirm)) } },
        dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
