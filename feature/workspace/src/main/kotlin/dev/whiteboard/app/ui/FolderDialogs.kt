package dev.whiteboard.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.whiteboard.app.workspace.LibraryViewModel
import dev.whiteboard.core.model.Folder
import dev.whiteboard.core.model.FolderTree
import dev.whiteboard.ui.*
import dev.whiteboard.ui.R

@Composable
fun FolderPickerDialog(
    folders: List<Folder>,
    initial: String?,
    dismiss: () -> Unit,
    allowed: (String?) -> Boolean = { true },
    submit: (String?) -> Unit
) {
    var selected by remember { mutableStateOf(initial) }
    val tree = remember(folders) { FolderTree(folders) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(R.string.move_to_folder)) },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected == null, onClick = { selected = null }, enabled = allowed(null))
                    Text(stringResource(R.string.root_folder), Modifier.clickable(enabled = allowed(null)) { selected = null })
                }
                folders.forEach { folder ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected == folder.id, { selected = folder.id }, enabled = allowed(folder.id))
                        Text(tree.path(folder.id), Modifier.clickable(enabled = allowed(folder.id)) { selected = folder.id })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { submit(selected) }, enabled = allowed(selected)) { Text(stringResource(R.string.confirm)) }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
fun FoldersDialog(folders: List<Folder>, current: String?, viewModel: LibraryViewModel, dismiss: () -> Unit, select: (String?) -> Unit) {
    var adding by remember { mutableStateOf(false) }
    var addingParent by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<Folder?>(null) }
    var moving by remember { mutableStateOf<Folder?>(null) }
    var deleting by remember { mutableStateOf<Folder?>(null) }
    val tree = remember(folders) { FolderTree(folders) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(stringResource(R.string.folders)) }, text = {
        Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
            TextButton(onClick = { select(null) }) { Text(stringResource(R.string.all_boards)) }
            folders.forEach { folder ->
                var menu by remember(folder.id) { mutableStateOf(false) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(current == folder.id, { select(folder.id) })
                    Text(tree.path(folder.id), Modifier.weight(1f).clickable { select(folder.id) })
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.more)) }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.new_subfolder)) }, onClick = {
                                menu = false
                                addingParent =
                                    folder.id
                                adding = true
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.rename_folder)) }, onClick = {
                                menu = false
                                renaming =
                                    folder
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.move_to_folder)) }, onClick = {
                                menu = false
                                moving =
                                    folder
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = {
                                menu = false
                                deleting = folder
                            })
                        }
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.done)) } }, dismissButton = {
        TextButton(onClick = {
            addingParent = null
            adding = true
        }) {
            Icon(Icons.Outlined.Add, null)
            Text(stringResource(R.string.new_folder))
        }
    })
    if (adding) {
        NameDialog(stringResource(R.string.new_folder), "", { adding = false }, nameLabel = R.string.folder_name) {
            viewModel.createFolder(it, addingParent)
            adding = false
        }
    }
    renaming?.let { folder ->
        NameDialog(stringResource(R.string.rename_folder), folder.name, { renaming = null }, nameLabel = R.string.folder_name) {
            viewModel.renameFolder(folder.id, it)
            renaming = null
        }
    }
    moving?.let { folder ->
        FolderPickerDialog(folders, folder.parentId, { moving = null }, allowed = { tree.canMove(folder.id, it) }) {
            viewModel.moveFolder(folder.id, it)
            moving = null
        }
    }
    deleting?.let { folder ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.delete_folder)) },
            text = { Text(stringResource(R.string.delete_folder_description)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteFolder(folder.id)
                    deleting = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}
