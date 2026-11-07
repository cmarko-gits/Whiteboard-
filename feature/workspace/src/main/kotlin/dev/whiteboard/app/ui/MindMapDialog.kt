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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
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
    val dismiss: () -> Unit = {
        if (viewModel.mindMaps.state.value.request?.id == request.id) viewModel.mindMaps.dismiss()
    }
    val apply: (MindMapAction) -> Job = { viewModel.mindMaps.apply(it, request.id) }
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
    val scope = rememberCoroutineScope()
    val doneFocus = remember { FocusRequester() }
    var actionsPlaced by remember { mutableStateOf(false) }
    var editing by rememberSaveable(title) { mutableStateOf(false) }
    var settlingFocus by remember(title) { mutableStateOf(true) }
    var closingInput by remember { mutableStateOf(false) }
    fun finishSearch(closeKeyboard: () -> Unit, closePanel: Boolean) {
        if (closingInput) return
        editing = false
        closingInput = true
        scope.launch {
            try {
                releaseMapInput(closeKeyboard)
                doneFocus.requestFocus()
                if (closePanel) dismiss()
            } finally {
                closingInput = false
            }
        }
    }
    MindMapFormDialog(
        title,
        dismiss,
        input = { closeKeyboard, _ ->
            Column(Modifier.fillMaxWidth()) {
                MapError(error)
                OutlinedTextField(
                    query,
                    { query = it.take(512) },
                    label = { Text(stringResource(R.string.mind_map_search)) },
                    singleLine = true,
                    enabled = !busy && !closingInput,
                    modifier = Modifier.fillMaxWidth().focusProperties { canFocus = !settlingFocus || editing }
                        .onFocusChanged { if (it.isFocused) editing = true },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        finishSearch(closeKeyboard, closePanel = false)
                    })
                )
            }
        },
        actions = { closeKeyboard ->
            LaunchedEffect(actionsPlaced) {
                if (actionsPlaced) {
                    withFrameNanos { }
                    if (!editing) {
                        closeKeyboard()
                        doneFocus.requestFocus()
                    }
                    settlingFocus = false
                }
            }
            if (create != null) {
                TextButton(
                    onClick = {
                        closeKeyboard()
                        create()
                    },
                    enabled = !busy && !closingInput,
                    modifier = Modifier.weight(1f, fill = false)
                ) { Text(stringResource(R.string.mind_map_new)) }
            }
            TextButton(
                onClick = {
                    if (busy) dismiss() else finishSearch(closeKeyboard, closePanel = !editing)
                },
                modifier = Modifier.weight(1f, fill = false).focusProperties { canFocus = true }
                    .focusRequester(doneFocus).onGloballyPositioned {
                        actionsPlaced = it.isAttached && it.size.width > 0 && it.size.height > 0
                    }
            ) { Text(stringResource(R.string.done)) }
        },
        body = { closeKeyboard ->
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                if (rows.isEmpty()) item { Text(stringResource(R.string.mind_map_empty)) }
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
                        modifier = Modifier.padding(start = (row.depth.coerceAtMost(6) * 12).dp)
                            .fillMaxWidth().heightIn(min = 56.dp).clickable(enabled = !busy && !closingInput, role = Role.Button) {
                                scope.launch {
                                    releaseMapInput(closeKeyboard)
                                    select(row)
                                }
                            }
                    )
                }
                if (rows.isNotEmpty() && filtered.isEmpty()) item { Text(stringResource(R.string.no_objects)) }
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
    submit: (String, Int) -> Job
) {
    var title by rememberSaveable { mutableStateOf(initial.take(20_000)) }
    var color by rememberSaveable { mutableIntStateOf(initialColor) }
    var tooLong by rememberSaveable { mutableStateOf(false) }
    val oversized = initial.length > 20_000
    val commit = rememberMapCommit()
    val processing = state.busy || commit.pending
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
        { if (!processing) dismiss() },
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
                    singleLine = true,
                    maxLines = 1,
                    isError = message != null,
                    supportingText = if (message !=
                        null
                    ) {
                        ({ Text(message, maxLines = if (compact) 1 else 3, overflow = TextOverflow.Ellipsis) })
                    } else {
                        null
                    },
                    enabled = !processing && !oversized,
                    modifier = Modifier.weight(1f).focusRequester(focus).onGloballyPositioned {
                        placed = it.isAttached && it.size.width > 0 && it.size.height > 0
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (!processing && !oversized && !tooLong && title.isNotBlank()) {
                            commit.submit(closeKeyboard) { submit(title, color) }
                        }
                    })
                )
                ColorPickerButton(color, favorites, saveFavorites, enabled = !processing && !oversized) { color = it }
            }
        },
        actions = { closeKeyboard ->
            TextButton(onClick = {
                closeKeyboard()
                dismiss()
            }, enabled = !processing, modifier = Modifier.weight(1f, fill = false)) { Text(stringResource(R.string.cancel)) }
            TextButton(
                onClick = {
                    commit.submit(closeKeyboard) { submit(title, color) }
                },
                enabled = !processing && !oversized && !tooLong && title.isNotBlank(),
                modifier = Modifier.weight(1f, fill = false)
            ) { Text(stringResource(R.string.confirm)) }
        },
        body = { _ -> }
    )
}

@Composable
private fun MindMapMoveDialog(state: MindMapEditorUi, dismiss: () -> Unit, submit: (Point) -> Job) {
    var x by rememberSaveable { mutableStateOf("240") }
    var y by rememberSaveable { mutableStateOf("0") }
    fun coordinate(value: String): Float? = value.toFloatOrNull()?.takeIf { it.isFinite() && it in -1_000_000_000f..1_000_000_000f }
    val commit = rememberMapCommit()
    val processing = state.busy || commit.pending
    val dx = coordinate(x)
    val dy = coordinate(y)
    MindMapFormDialog(
        R.string.mind_map_move,
        { if (!processing) dismiss() },
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
                        enabled = !processing,
                        isError = dx == null
                    )
                    OutlinedTextField(
                        y,
                        { y = it.take(32) },
                        label = { Text(stringResource(R.string.mind_map_dy)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        modifier = Modifier.weight(1f),
                        enabled = !processing,
                        isError = dy == null
                    )
                }
            }
        },
        actions = { closeKeyboard ->
            TextButton(onClick = {
                closeKeyboard()
                dismiss()
            }, enabled = !processing, modifier = Modifier.weight(1f, fill = false)) { Text(stringResource(R.string.cancel)) }
            TextButton(
                onClick = {
                    commit.submit(closeKeyboard) { submit(Point(checkNotNull(dx), checkNotNull(dy))) }
                },
                enabled = !processing && dx != null && dy != null && (dx != 0f || dy != 0f),
                modifier = Modifier.weight(1f, fill = false)
            ) { Text(stringResource(R.string.confirm)) }
        },
        body = { _ ->
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) { Text(stringResource(R.string.mind_map_move_hint)) }
        }
    )
}

/** The input and actions retain their parents when IME changes the available height. */
@Composable
private fun MindMapFormDialog(
    title: Int,
    dismiss: () -> Unit,
    input: @Composable (closeKeyboard: () -> Unit, compact: Boolean) -> Unit,
    actions: @Composable RowScope.(closeKeyboard: () -> Unit) -> Unit,
    body: @Composable (closeKeyboard: () -> Unit) -> Unit
) {
    InputDialog(dismiss) { closeKeyboard ->
        BoxWithConstraints(Modifier.systemBarsPadding().imePadding().padding(horizontal = 24.dp, vertical = 8.dp)) {
            val compact = maxHeight < 240.dp
            val inline = compact && WindowInsets.ime.getBottom(LocalDensity.current) > 0
            Surface(Modifier.widthIn(max = 560.dp).fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
                Column(Modifier.padding(if (compact) 8.dp else 24.dp)) {
                    if (!compact) {
                        Text(stringResource(title), style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(12.dp))
                    }
                    AdaptiveDialogContent(
                        compact = inline,
                        input = { input(closeKeyboard, compact) },
                        actions = { actions(closeKeyboard) },
                        body = {
                            if (!inline) body(closeKeyboard)
                        }
                    )
                }
            }
        }
    }
}

/** Commit after the dialog has released its own IME session, while its token is attached. */
private class MapCommit(private val scope: CoroutineScope) {
    var pending by mutableStateOf(false)
        private set
    fun submit(closeKeyboard: () -> Unit, action: () -> Job) {
        if (pending) return
        pending = true
        scope.launch {
            try {
                releaseMapInput(closeKeyboard)
                action().join()
            } finally {
                pending = false
            }
        }
    }
}

@Composable
private fun rememberMapCommit(): MapCommit {
    val scope = rememberCoroutineScope()
    return remember(scope) { MapCommit(scope) }
}

private suspend fun releaseMapInput(closeKeyboard: () -> Unit) {
    closeKeyboard()
    withFrameNanos { }
    closeKeyboard()
}
