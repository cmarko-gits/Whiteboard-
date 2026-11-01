package dev.whiteboard.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.whiteboard.app.workspace.EditorUi
import dev.whiteboard.app.workspace.WorkspaceViewModel
import dev.whiteboard.core.model.*
import dev.whiteboard.ui.*
import dev.whiteboard.ui.R

@Composable
fun PagesDialog(editor: EditorUi, viewModel: WorkspaceViewModel, dismiss: () -> Unit) {
    var renameId by rememberSaveable { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(stringResource(R.string.pages)) }, text = {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            editor.pages.forEachIndexed { index, page ->
                var menu by remember(page.id) { mutableStateOf(false) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    RadioButton(page.id == editor.page.id, onClick = { viewModel.selectPage(page.id) })
                    Text(page.name, Modifier.weight(1f).clickable { viewModel.selectPage(page.id) })
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.more)) }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.rename_page)) }, onClick = {
                                menu = false
                                renameId = page.id
                            })
                            DropdownMenuItem(text = {
                                Text(stringResource(R.string.duplicate))
                            }, enabled = editor.pages.size < 1000, onClick = {
                                menu = false
                                viewModel.duplicatePage(page.id)
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.move_up)) }, enabled = index > 0, onClick = {
                                menu =
                                    false
                                viewModel.movePage(page.id, -1)
                            })
                            DropdownMenuItem(text = {
                                Text(stringResource(R.string.move_down))
                            }, enabled = index < editor.pages.lastIndex, onClick = {
                                menu =
                                    false
                                viewModel.movePage(page.id, 1)
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, enabled = editor.pages.size > 1, onClick = {
                                menu =
                                    false
                                viewModel.deletePage(page.id)
                            })
                        }
                    }
                }
            }
            TextButton(onClick = { viewModel.addPage() }, enabled = editor.pages.size < 1000) {
                Icon(Icons.Outlined.Add, null)
                Text(stringResource(R.string.add_page))
            }
        }
    }, confirmButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.done)) } })
    editor.pages.firstOrNull { it.id == renameId }?.let { page ->
        NameDialog(stringResource(R.string.rename_page), page.name, { renameId = null }) { name ->
            viewModel.renamePage(page.id, name)
            renameId =
                null
        }
    }
}

@Composable
fun BackgroundDialog(page: Page, viewModel: WorkspaceViewModel, favoriteColors: List<Int>, dismiss: () -> Unit) {
    var pattern by rememberSaveable(page.id) { mutableStateOf(page.background) }
    var color by rememberSaveable(page.id) { mutableIntStateOf(page.backgroundColor) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(R.string.background)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Background.entries.forEach { value ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(pattern == value, { pattern = value })
                        Text(
                            stringResource(
                                when (value) {
                                    Background.PLAIN -> R.string.plain
                                    Background.GRID -> R.string.grid
                                    Background.DOTS -> R.string.dots
                                    Background.RULED -> R.string.ruled
                                }
                            )
                        )
                    }
                }
                ColorChoiceRow(
                    color,
                    listOf(
                        0xFFFAFBF7.toInt(),
                        0xFFFFFFFF.toInt(),
                        0xFF202621.toInt(),
                        0xFFE6E8E1.toInt(),
                        0xFFFFF8E5.toInt()
                    ),
                    favoriteColors,
                    viewModel::favoriteColors
                ) { color = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                viewModel.background(pattern, color)
                dismiss()
            }) { Text(stringResource(R.string.apply)) }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
