package dev.whiteboard.app.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.whiteboard.app.workspace.*
import dev.whiteboard.core.model.BoardSummary
import dev.whiteboard.core.model.FolderTree
import dev.whiteboard.ui.*
import dev.whiteboard.ui.R
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun LibraryScreen(state: LibraryUi, viewModel: LibraryViewModel, workspace: WorkspaceViewModel, onSettings: () -> Unit) {
    val closeKeyboard = rememberInputCloser()
    val openSettings = {
        closeKeyboard()
        onSettings()
    }
    var section by rememberSaveable { mutableStateOf(0) }
    var search by rememberSaveable { mutableStateOf("") }
    var create by rememberSaveable { mutableStateOf(false) }
    var rename by remember { mutableStateOf<BoardSummary?>(null) }
    var deleting by remember { mutableStateOf<BoardSummary?>(null) }
    var showFolders by rememberSaveable { mutableStateOf(false) }
    var selectedFolder by rememberSaveable { mutableStateOf<String?>(null) }
    var moving by remember { mutableStateOf<BoardSummary?>(null) }
    LaunchedEffect(search) { viewModel.search(search) }
    LaunchedEffect(state.folders, state.loading) {
        if (!state.loading && state.folders.none { it.id == selectedFolder }) selectedFolder = null
    }
    val searchPending = search.isNotBlank() && state.searchQuery != search.trim()
    val resultsLabel = stringResource(if (search.isNotBlank() && !searchPending) R.string.search_results else R.string.all_boards)
    val visible = state.boards.filter {
        (if (section == 2) it.trashedAt != null else it.trashedAt == null && (section != 1 || it.favorite)) &&
            (selectedFolder == null || section == 2 || it.folderId == selectedFolder) &&
            (search.isBlank() || state.searchQuery == search.trim() && it.id in state.searchMatches)
    }
    BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
        val wide = maxWidth >= 800.dp
        val compact = maxHeight < 700.dp
        Row(Modifier.fillMaxSize()) {
            if (wide) {
                Column(
                    Modifier.width(
                        218.dp
                    ).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)).padding(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 8.dp, bottom = if (compact) 8.dp else 44.dp)
                    ) {
                        Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(12.dp)) {
                            Icon(Icons.Outlined.Gesture, null, Modifier.padding(10.dp), tint = MaterialTheme.colorScheme.onPrimary)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text("Whiteboard", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    }
                    if (!compact) {
                        Text(
                            stringResource(R.string.workspace),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    LibraryDestination(
                        stringResource(R.string.all_boards),
                        Icons.Outlined.Dashboard,
                        section == 0 && selectedFolder == null
                    ) {
                        section =
                            0
                        selectedFolder = null
                    }
                    LibraryDestination(stringResource(R.string.favorites), Icons.Outlined.StarOutline, section == 1) { section = 1 }
                    LibraryDestination(stringResource(R.string.trash), Icons.Outlined.DeleteOutline, section == 2) { section = 2 }
                    LibraryDestination(stringResource(R.string.folders), Icons.Outlined.FolderOpen, selectedFolder != null) {
                        showFolders =
                            true
                    }
                    Spacer(Modifier.weight(1f))
                    LibraryDestination(stringResource(R.string.settings), Icons.Outlined.Settings, false, openSettings)
                    if (!compact) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.padding(top = 20.dp)
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Icon(Icons.Outlined.CloudOff, null)
                                Spacer(Modifier.height(10.dp))
                                Text(stringResource(R.string.local_workspace), fontWeight = FontWeight.Medium)
                                Text(
                                    stringResource(R.string.local_description),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = if (wide) 36.dp else 20.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(top = if (compact) 4.dp else 24.dp, bottom = if (compact) 4.dp else 28.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.offline_ready),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    if (!wide) IconButton(onClick = openSettings) { Icon(Icons.Outlined.Settings, stringResource(R.string.settings)) }
                }
                if (!compact) {
                    Text(
                        stringResource(R.string.library_title),
                        fontSize = if (wide) {
                            40.sp
                        } else {
                            32.sp
                        },
                        lineHeight = if (wide) {
                            46.sp
                        } else {
                            38.sp
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (!compact) {
                    Text(
                        stringResource(R.string.library_subtitle),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp, bottom = 24.dp)
                    )
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        search,
                        {
                            search = it.take(512)
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        placeholder = { Text(stringResource(R.string.search_boards)) },
                        leadingIcon = { Icon(Icons.Outlined.Search, null) }
                    )
                    Spacer(Modifier.width(12.dp))
                    Button(
                        onClick = {
                            closeKeyboard()
                            create = true
                        },
                        enabled = !state.loading,
                        shape = RoundedCornerShape(
                            14.dp
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp)
                    ) {
                        Icon(Icons.Outlined.Add, stringResource(R.string.new_board))
                        if (wide) {
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.new_board))
                        }
                    }
                }
                if (!wide) {
                    Row(
                        Modifier.padding(top = if (compact) 4.dp else 14.dp).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(section == 0 && selectedFolder == null, {
                            section = 0
                            selectedFolder = null
                        }, { Text(stringResource(R.string.all_boards)) })
                        FilterChip(section == 1, { section = 1 }, { Text(stringResource(R.string.favorites)) })
                        FilterChip(section == 2, { section = 2 }, { Text(stringResource(R.string.trash)) })
                        FilterChip(selectedFolder != null, { showFolders = true }, { Text(stringResource(R.string.folders)) })
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = if (compact) 4.dp else 24.dp, bottom = if (compact) 4.dp else 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selectedFolder != null && section != 2) {
                        TextButton(onClick = { showFolders = true }, modifier = Modifier.weight(1f, fill = false)) {
                            Text(FolderTree(state.folders).path(selectedFolder), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Text(
                        stringResource(
                            if (section ==
                                2
                            ) {
                                R.string.trash
                            } else if (section ==
                                1
                            ) {
                                R.string.favorites
                            } else {
                                R.string.recent_boards
                            }
                        ),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "${visible.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (state.loading || searchPending) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (visible.isEmpty() &&
                    !state.loading && !searchPending
                ) {
                    Column(
                        Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(
                            stringResource(if (search.isNotBlank()) R.string.no_results else R.string.empty_boards),
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 18.dp)
                        )
                        if (section == 0 &&
                            search.isBlank()
                        ) {
                            TextButton(onClick = {
                                closeKeyboard()
                                create = true
                            }) { Text(stringResource(R.string.create_first)) }
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        GridCells.Adaptive(
                            if (compact) {
                                320.dp
                            } else if (wide) {
                                240.dp
                            } else {
                                155.dp
                            }
                        ),
                        modifier = Modifier.weight(1f).semantics { contentDescription = resultsLabel },
                        contentPadding = PaddingValues(bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        items(visible, key = { it.id }) { board ->
                            BoardCard(
                                board,
                                compact = compact,
                                loadPreview = viewModel::preview,
                                open = {
                                    if (section != 2 &&
                                        !state.loading
                                    ) {
                                        closeKeyboard()
                                        workspace.open(board.id, search.takeIf { it.isNotBlank() })
                                    }
                                },
                                favorite = { viewModel.favorite(board) },
                                rename = {
                                    closeKeyboard()
                                    rename = board
                                },
                                trash = { viewModel.trash(board.id) },
                                restore = { viewModel.restore(board.id) },
                                move = { moving = board },
                                delete = {
                                    deleting =
                                        board
                                }
                            )
                        }
                    }
                }
            }
        }
    }
    if (create) {
        NewBoardDialog({ create = false }) { name, template ->
            workspace.create(name, selectedFolder, template)
            create = false
        }
    }
    rename?.let { board ->
        NameDialog(stringResource(R.string.rename_board), board.name, { rename = null }) {
            viewModel.rename(board.id, it)
            rename =
                null
        }
    }
    deleting?.let { board ->
        AlertDialog(
            onDismissRequest = {
                deleting = null
            },
            title = {
                Text(stringResource(R.string.delete_forever))
            },
            text = { Text(stringResource(R.string.delete_description, board.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.permanentlyDelete(board.id)
                    deleting = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
    if (showFolders) {
        FoldersDialog(state.folders, selectedFolder, viewModel, { showFolders = false }) {
            selectedFolder = it
            section = 0
            showFolders = false
        }
    }
    moving?.let { board ->
        FolderPickerDialog(state.folders, board.folderId, { moving = null }) {
            viewModel.assignBoard(board.id, it)
            moving = null
        }
    }
}

@Composable
private fun LibraryDestination(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface.copy(alpha = 0f),
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun BoardCard(
    board: BoardSummary,
    compact: Boolean,
    loadPreview: suspend (String) -> ByteArray?,
    open: () -> Unit,
    favorite: () -> Unit,
    rename: () -> Unit,
    trash: () -> Unit,
    restore: () -> Unit,
    move: () -> Unit,
    delete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, board.thumbnail) {
        val bytes = board.thumbnail?.let { loadPreview(it) }
        value = withContext(Dispatchers.Default) {
            bytes?.let {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(it, 0, it.size, bounds)
                if (bounds.outWidth in 1..1024 && bounds.outHeight in 1..1024) {
                    BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap()
                } else {
                    null
                }
            }
        }
    }
    val preview: @Composable (Modifier) -> Unit = { previewModifier ->
        Surface(
            onClick = open,
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = previewModifier.aspectRatio(1.45f)
        ) {
            Box(Modifier.fillMaxSize()) {
                bitmap?.let {
                    Image(
                        it,
                        stringResource(R.string.board_preview),
                        Modifier.fillMaxSize().padding(10.dp),
                        contentScale = ContentScale.Fit
                    )
                }
                    ?: Icon(
                        Icons.Outlined.Gesture,
                        null,
                        Modifier.align(Alignment.Center).size(42.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                    )
                if (board.trashedAt ==
                    null
                ) {
                    IconToggleButton(checked = board.favorite, onCheckedChange = {
                        favorite()
                    }, modifier = Modifier.align(Alignment.TopEnd)) {
                        Icon(
                            if (board.favorite) Icons.Outlined.Star else Icons.Outlined.StarOutline,
                            stringResource(R.string.favorite),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
    val details: @Composable RowScope.() -> Unit = {
        Column(Modifier.weight(1f).heightIn(min = 48.dp).clickable(onClick = open), verticalArrangement = Arrangement.Center) {
            Text(board.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Text(
                DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(board.updatedAt)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.board_actions, board.name)) }
            DropdownMenu(menu, { menu = false }) {
                if (board.trashedAt == null) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.move_to_folder)) }, onClick = {
                        menu = false
                        move()
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.rename_board)) }, onClick = {
                        menu = false
                        rename()
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.move_to_trash)) }, onClick = {
                        menu = false
                        trash()
                    })
                } else {
                    DropdownMenuItem(text = { Text(stringResource(R.string.restore)) }, onClick = {
                        menu = false
                        restore()
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.delete_forever)) }, onClick = {
                        menu = false
                        delete()
                    })
                }
            }
        }
    }
    BoxWithConstraints {
        val horizontal = compact && maxWidth >= 320.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
        if (horizontal) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                preview(Modifier.width(92.dp))
                Spacer(Modifier.width(12.dp))
                details()
            }
        } else {
            Column {
                preview(Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, content = details)
            }
        }
    }
}
