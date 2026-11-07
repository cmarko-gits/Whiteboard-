package dev.whiteboard.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.whiteboard.app.workspace.*
import dev.whiteboard.core.model.*
import dev.whiteboard.ui.*
import dev.whiteboard.ui.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

private enum class MapPanel { BROWSE, NODE, CREATE, CHILD, SIBLING, EDIT, PARENT, MOVE }

@Composable
internal fun MindMapDialog(
    request: MindMapEditorRequest,
    state: MindMapEditorUi,
    viewModel: WorkspaceViewModel,
    favoriteColors: List<Int>
) {
    var nodeId by rememberSaveable(request.id) { mutableStateOf(request.selectedNodeId) }
    var panel by rememberSaveable(request.id) { mutableStateOf(if (request.selectedNodeId == null) MapPanel.BROWSE else MapPanel.NODE) }
    val row = nodeId?.let(request.byId::get)
    val dismiss: () -> Unit = { viewModel.mindMaps.dismiss() }
    val apply: (MindMapAction) -> Unit = { viewModel.mindMaps.apply(it) }
    val back = { panel = if (row == null) MapPanel.BROWSE else MapPanel.NODE }
    when (panel) {
        MapPanel.BROWSE, MapPanel.PARENT -> {
            val candidates = if (panel == MapPanel.PARENT &&
                row != null
            ) {
                request.rows.filter { it.mapId == row.mapId && it.node.id != row.node.id }
            } else {
                request.rows
            }
            MindMapNodeList(
                candidates,
                if (panel == MapPanel.PARENT) R.string.mind_map_reparent else R.string.mind_maps,
                state.busy,
                state.error,
                dismiss = { if (panel == MapPanel.PARENT) back() else dismiss() },
                create = if (panel == MapPanel.BROWSE) ({ panel = MapPanel.CREATE }) else null
            ) { selected ->
                if (panel == MapPanel.PARENT && row != null) {
                    apply(MindMapAction.Reparent(row.node.id, selected.node.id))
                } else {
                    nodeId = selected.node.id
                    panel = MapPanel.NODE
                }
            }
        }
        MapPanel.NODE -> if (row != null) {
            InputAlertDialog(
                onDismissRequest = { dismiss() },
                title = { Text(row.note.text.take(120), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                text = { closeKeyboard ->
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                        MapError(state.error)
                        if (!row.visible) Text(stringResource(R.string.mind_map_hidden))
                        @Composable fun action(label: Int, enabled: Boolean = true, click: () -> Unit) {
                            TextButton(onClick = {
                                closeKeyboard()
                                click()
                            }, enabled = enabled && !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                Text(stringResource(label))
                            }
                        }
                        action(R.string.mind_map_edit) { panel = MapPanel.EDIT }
                        action(R.string.mind_map_child) { panel = MapPanel.CHILD }
                        action(R.string.mind_map_sibling, row.node.parentId != null) { panel = MapPanel.SIBLING }
                        action(if (row.node.collapsed) R.string.mind_map_expand else R.string.mind_map_collapse, row.childCount > 0) {
                            apply(MindMapAction.Collapse(row.node.id, !row.node.collapsed))
                        }
                        action(R.string.mind_map_select) { apply(MindMapAction.Focus(row.node.id)) }
                        action(R.string.mind_map_select_all) { apply(MindMapAction.SelectMap(row.node.id)) }
                        action(R.string.mind_map_reparent, row.node.parentId != null) { panel = MapPanel.PARENT }
                        action(R.string.mind_map_move) { panel = MapPanel.MOVE }
                        action(R.string.branch_cut) { viewModel.removeMindMapBranch(request, row.node.id, cut = true) }
                        action(R.string.branch_delete) { viewModel.removeMindMapBranch(request, row.node.id) }
                    }
                },
                confirmButton = { closeKeyboard ->
                    TextButton(onClick = {
                        closeKeyboard()
                        dismiss()
                    }) { Text(stringResource(R.string.done)) }
                },
                dismissButton = { closeKeyboard ->
                    TextButton(onClick = {
                        closeKeyboard()
                        panel = MapPanel.BROWSE
                    }, enabled = !state.busy) { Text(stringResource(R.string.mind_maps)) }
                }
            )
        }
        MapPanel.CREATE, MapPanel.CHILD, MapPanel.SIBLING, MapPanel.EDIT -> {
            val label = when (panel) {
                MapPanel.CREATE -> R.string.mind_map_new
                MapPanel.CHILD -> R.string.mind_map_child
                MapPanel.SIBLING -> R.string.mind_map_sibling
                else -> R.string.mind_map_edit
            }
            key(panel, nodeId) {
                MindMapTitleDialog(
                    label,
                    if (panel == MapPanel.EDIT) row?.note?.text.orEmpty() else "",
                    row?.note?.color ?: 0xFFD5E7CF.toInt(),
                    state,
                    favoriteColors,
                    viewModel::favoriteColors,
                    back
                ) { title, color ->
                    val action = when (panel) {
                        MapPanel.CREATE -> MindMapAction.Create(title, color)
                        MapPanel.CHILD -> MindMapAction.Child(checkNotNull(row).node.id, title, color)
                        MapPanel.SIBLING -> MindMapAction.Sibling(checkNotNull(row).node.id, title, color)
                        else -> MindMapAction.Edit(checkNotNull(row).node.id, title, color)
                    }
                    apply(action)
                }
            }
        }
        MapPanel.MOVE -> if (row != null) MindMapMoveDialog(state, back) { apply(MindMapAction.Move(row.node.id, it)) }
    }
}

@Composable
private fun MapError(error: Int?) {
    if (error !=
        null
    ) {
        Text(
            stringResource(error),
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics {
                liveRegion =
                    LiveRegionMode.Polite
            }
        )
    }
}

@Composable
private fun MindMapNodeList(
    rows: List<MindMapRow>,
    title: Int,
    busy: Boolean,
    error: Int?,
    dismiss: () -> Unit,
    create: (() -> Unit)?,
    select: (MindMapRow) -> Unit
) {
    var query by rememberSaveable(title) { mutableStateOf("") }
    val filtered by produceState(rows, rows, query) {
        value = withContext(Dispatchers.Default) {
            val calculation = currentCoroutineContext()
            val key = normalizeForSearch(query.trim())
            if (key.isEmpty()) {
                rows
            } else {
                rows.filter {
                    calculation.ensureActive()
                    normalizeForSearch(it.note.text).contains(key)
                }
            }
        }
    }
    InputAlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(title)) },
        text = { closeKeyboard ->
            Column(Modifier.fillMaxWidth()) {
                MapError(error)
                OutlinedTextField(query, {
                    query = it.take(512)
                }, label = { Text(stringResource(R.string.mind_map_search)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (rows.isEmpty()) Text(stringResource(R.string.mind_map_empty))
                LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 360.dp)) {
                    items(filtered, key = { it.node.id }) { row ->
                        ListItem(
                            headlineContent = { Text(row.note.text.take(200), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            supportingContent = {
                                Text(
                                    listOfNotNull(
                                        stringResource(R.string.mind_map_number, row.mapNumber),
                                        stringResource(R.string.mind_map_level, row.depth + 1),
                                        if (!row.visible) stringResource(R.string.mind_map_hidden) else null,
                                        if (row.node.collapsed) stringResource(R.string.mind_map_collapsed) else null
                                    ).joinToString(" · ")
                                )
                            },
                            modifier = Modifier.padding(
                                start = (row.depth.coerceAtMost(6) * 12).dp
                            ).fillMaxWidth().heightIn(min = 56.dp).clickable(enabled = !busy, role = Role.Button) {
                                closeKeyboard()
                                select(row)
                            }
                        )
                    }
                    if (rows.isNotEmpty() && filtered.isEmpty()) item { Text(stringResource(R.string.no_objects)) }
                }
            }
        },
        confirmButton = { closeKeyboard ->
            TextButton(onClick = {
                closeKeyboard()
                dismiss()
            }) { Text(stringResource(R.string.done)) }
        },
        dismissButton = { closeKeyboard ->
            if (create !=
                null
            ) {
                TextButton(onClick = {
                    closeKeyboard()
                    create()
                }, enabled = !busy) { Text(stringResource(R.string.mind_map_new)) }
            }
        }
    )
}

@Composable
private fun MindMapTitleDialog(
    titleLabel: Int,
    initial: String,
    initialColor: Int,
    state: MindMapEditorUi,
    favorites: List<Int>,
    saveFavorites: (List<Int>) -> Unit,
    dismiss: () -> Unit,
    submit: (String, Int) -> Unit
) {
    var title by rememberSaveable { mutableStateOf(initial.take(20_000)) }
    var color by rememberSaveable { mutableIntStateOf(initialColor) }
    var tooLong by rememberSaveable { mutableStateOf(false) }
    val oversized = initial.length > 20_000
    val focus = remember { FocusRequester() }
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(placed) {
        if (placed && !oversized) {
            withFrameNanos { }
            focus.requestFocus()
        }
    }
    val message = if (oversized || tooLong) stringResource(R.string.mind_map_title_limit) else state.error?.let { stringResource(it) }
    MindMapFormDialog(
        titleLabel,
        dismiss,
        input = { closeKeyboard, compact ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    title,
                    {
                        tooLong = it.length > 20_000
                        if (!tooLong) title = it
                    },
                    label = { Text(stringResource(R.string.mind_map_node_title)) },
                    singleLine = compact,
                    maxLines = if (compact) 1 else 4,
                    isError = message != null,
                    supportingText = if (message !=
                        null
                    ) {
                        ({ Text(message, maxLines = if (compact) 1 else 3, overflow = TextOverflow.Ellipsis) })
                    } else {
                        null
                    },
                    enabled = !state.busy && !oversized,
                    modifier = Modifier.weight(1f).focusRequester(focus).onGloballyPositioned {
                        placed = it.isAttached && it.size.width > 0 && it.size.height > 0
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (!state.busy && !oversized && !tooLong && title.isNotBlank()) {
                            closeKeyboard()
                            submit(title, color)
                        }
                    })
                )
                ColorPickerButton(color, favorites, saveFavorites, enabled = !state.busy && !oversized) { color = it }
            }
        },
        actions = { closeKeyboard ->
            TextButton(onClick = {
                closeKeyboard()
                dismiss()
            }, modifier = Modifier.weight(1f, fill = false)) { Text(stringResource(R.string.cancel)) }
            TextButton(
                onClick = {
                    closeKeyboard()
                    submit(title, color)
                },
                enabled = !state.busy && !oversized && !tooLong && title.isNotBlank(),
                modifier = Modifier.weight(1f, fill = false)
            ) { Text(stringResource(R.string.confirm)) }
        },
        body = { }
    )
}

@Composable
private fun MindMapMoveDialog(state: MindMapEditorUi, dismiss: () -> Unit, submit: (Point) -> Unit) {
    var x by rememberSaveable { mutableStateOf("240") }
    var y by rememberSaveable { mutableStateOf("0") }
    fun coordinate(value: String): Float? = value.toFloatOrNull()?.takeIf { it.isFinite() && it in -1_000_000_000f..1_000_000_000f }
    val dx = coordinate(x)
    val dy = coordinate(y)
    MindMapFormDialog(
        R.string.mind_map_move,
        dismiss,
        input = { _, _ ->
            Column(Modifier.fillMaxWidth()) {
                MapError(state.error)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        x,
                        { x = it.take(32) },
                        label = { Text(stringResource(R.string.mind_map_dx)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        modifier = Modifier.weight(1f),
                        enabled = !state.busy,
                        isError = dx == null
                    )
                    OutlinedTextField(
                        y,
                        { y = it.take(32) },
                        label = { Text(stringResource(R.string.mind_map_dy)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        modifier = Modifier.weight(1f),
                        enabled = !state.busy,
                        isError = dy == null
                    )
                }
            }
        },
        actions = { closeKeyboard ->
            TextButton(onClick = {
                closeKeyboard()
                dismiss()
            }, modifier = Modifier.weight(1f, fill = false)) { Text(stringResource(R.string.cancel)) }
            TextButton(
                onClick = {
                    closeKeyboard()
                    submit(Point(checkNotNull(dx), checkNotNull(dy)))
                },
                enabled = !state.busy && dx != null && dy != null && (dx != 0f || dy != 0f),
                modifier = Modifier.weight(1f, fill = false)
            ) { Text(stringResource(R.string.confirm)) }
        },
        body = { Text(stringResource(R.string.mind_map_move_hint)) }
    )
}

/** The input and actions retain their parents when IME changes the available height. */
@Composable
private fun MindMapFormDialog(
    title: Int,
    dismiss: () -> Unit,
    input: @Composable (closeKeyboard: () -> Unit, compact: Boolean) -> Unit,
    actions: @Composable RowScope.(closeKeyboard: () -> Unit) -> Unit,
    body: @Composable () -> Unit
) {
    InputDialog(dismiss) { closeKeyboard ->
        BoxWithConstraints(Modifier.systemBarsPadding().imePadding().padding(horizontal = 24.dp, vertical = 8.dp)) {
            val compact = maxHeight < 240.dp
            Surface(Modifier.widthIn(max = 560.dp).fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
                Column(Modifier.padding(if (compact) 8.dp else 24.dp)) {
                    if (!compact) {
                        Text(stringResource(title), style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(12.dp))
                    }
                    AdaptiveDialogContent(
                        compact = compact,
                        input = { input(closeKeyboard, compact) },
                        actions = { actions(closeKeyboard) },
                        body = {
                            if (!compact) Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) { body() }
                        }
                    )
                }
            }
        }
    }
}
