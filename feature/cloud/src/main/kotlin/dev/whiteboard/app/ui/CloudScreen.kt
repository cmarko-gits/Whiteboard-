package dev.whiteboard.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.whiteboard.app.workspace.CloudViewModel
import dev.whiteboard.canvas.WhiteboardView
import dev.whiteboard.core.cloud.*
import dev.whiteboard.core.model.*
import dev.whiteboard.ui.*
import dev.whiteboard.ui.R

@Composable
fun CloudScreen(viewModel: CloudViewModel, onAccount: () -> Unit, onCopy: (Board) -> Unit, dismiss: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val account by viewModel.account.collectAsStateWithLifecycle()
    val compact = LocalConfiguration.current.screenHeightDp < 500
    val visible = state.takeIf { it.accountId == account?.id }
    val preview = visible?.preview
    var confirmCopy by remember(preview?.working?.id) { mutableStateOf(false) }
    LaunchedEffect(state.accountId) { if (state.accountId != null) viewModel.refresh() }
    BackHandler { if (preview != null) viewModel.closePreview() else dismiss() }
    Dialog(
        onDismissRequest = { if (preview != null) viewModel.closePreview() else dismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (preview != null) viewModel.closePreview() else dismiss() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back))
                    }
                    Text(
                        preview?.working?.name ?: stringResource(R.string.cloud_boards),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(
                        onClick = { if (preview == null) viewModel.refresh() else viewModel.open(preview.working.id) },
                        enabled = account != null && visible?.busy != true
                    ) {
                        Icon(Icons.Outlined.Refresh, stringResource(R.string.cloud_refresh))
                    }
                }
                if (visible?.busy == true) LinearProgressIndicator(Modifier.fillMaxWidth())
                visible?.background?.takeIf {
                    account != null && it.state in setOf(
                        SyncWorkState.WAITING,
                        SyncWorkState.RUNNING,
                        SyncWorkState.NEEDS_ATTENTION
                    )
                }?.let { background ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(
                                when (background.state) {
                                    SyncWorkState.RUNNING -> R.string.cloud_background_running
                                    SyncWorkState.NEEDS_ATTENTION -> if (background.reason == SyncStopReason.CONFLICT) {
                                        R.string.cloud_conflict
                                    } else {
                                        R.string.cloud_background_attention
                                    }
                                    else -> R.string.cloud_background_waiting
                                }
                            ),
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (background.state == SyncWorkState.NEEDS_ATTENTION) {
                            TextButton(onClick = viewModel::retryBackground) {
                                Text(stringResource(R.string.cloud_background_retry))
                            }
                        }
                    }
                }
                visible?.message?.takeIf { !compact || visible.error }?.let { message ->
                    Surface(
                        color = if (visible.error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(message), Modifier.weight(1f).padding(vertical = 12.dp))
                            IconButton(onClick = viewModel::dismissMessage) {
                                Icon(Icons.Outlined.Close, stringResource(R.string.cloud_dismiss_notice))
                            }
                        }
                    }
                }
                when {
                    account == null -> Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(stringResource(if (viewModel.available) R.string.cloud_sign_in_help else R.string.account_unavailable))
                        if (viewModel.available) Button(onClick = onAccount) { Text(stringResource(R.string.account_sign_in)) }
                    }
                    visible != null && preview != null -> {
                        if (visible.offlinePreview) {
                            Text(
                                stringResource(R.string.cloud_offline_preview),
                                Modifier.padding(horizontal = 16.dp)
                            )
                        }
                        CloudPreview(preview, Modifier.weight(1f))
                        if (!compact) {
                            Text(
                                stringResource(R.string.cloud_read_only),
                                Modifier.padding(horizontal = 16.dp),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Button(
                            onClick = { confirmCopy = true },
                            enabled = !visible.busy,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = if (compact) 4.dp else 16.dp)
                        ) { Text(stringResource(R.string.cloud_make_local_copy)) }
                    }
                    visible != null -> {
                        val rows = (
                            visible.remote + visible.cached.map {
                                CloudBoardSummary(it.id, it.name, it.revision, it.role)
                            }
                            ).distinctBy { it.id }
                        val cached = visible.cached.associateBy { it.id }
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item { Text(account!!.email, style = MaterialTheme.typography.labelLarge) }
                            item { Text(stringResource(R.string.cloud_library_help), style = MaterialTheme.typography.bodySmall) }
                            items(visible.pending, key = { "pending:${it.sourceId}" }) { request ->
                                OutlinedCard(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(16.dp)) {
                                        Text(request.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Text(stringResource(R.string.cloud_waiting_upload), style = MaterialTheme.typography.bodySmall)
                                        TextButton(onClick = { viewModel.retry(request.sourceId) }, enabled = !visible.busy) {
                                            Text(stringResource(R.string.cloud_retry_upload))
                                        }
                                    }
                                }
                            }
                            items(rows, key = { it.id }) { board ->
                                OutlinedCard(Modifier.fillMaxWidth().clickable(enabled = !visible.busy) { viewModel.open(board.id) }) {
                                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Outlined.Cloud, null)
                                        Spacer(Modifier.width(16.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                board.name.ifBlank {
                                                    stringResource(R.string.cloud_unnamed)
                                                },
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(stringResource(roleLabel(board.role)), style = MaterialTheme.typography.labelSmall)
                                            cached[board.id]?.let {
                                                Text(
                                                    stringResource(
                                                        when {
                                                            it.conflicted -> R.string.cloud_conflict
                                                            it.pending > 0 -> R.string.cloud_waiting_changes
                                                            else -> R.string.cloud_downloaded
                                                        }
                                                    ),
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            if (rows.isEmpty() && visible.pending.isEmpty() &&
                                !visible.busy
                            ) {
                                item { Text(stringResource(R.string.cloud_empty)) }
                            }
                            if (visible.next != null) {
                                item {
                                    TextButton(onClick = {
                                        viewModel.refresh(more = true)
                                    }, enabled = !visible.busy) { Text(stringResource(R.string.cloud_more)) }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (confirmCopy && preview != null) {
            AlertDialog(
                onDismissRequest = { confirmCopy = false },
                title = { Text(stringResource(R.string.cloud_make_local_copy)) },
                text = { Text(stringResource(R.string.cloud_copy_description)) },
                confirmButton = {
                    TextButton(onClick = {
                        confirmCopy = false
                        viewModel.selectedCopy()?.let(onCopy)
                    }) { Text(stringResource(R.string.confirm)) }
                },
                dismissButton = { TextButton(onClick = { confirmCopy = false }) { Text(stringResource(R.string.cancel)) } }
            )
        }
    }
}

@Composable
private fun CloudPreview(state: SyncedDocument, modifier: Modifier = Modifier) {
    val board = state.working
    var pageIndex by rememberSaveable(board.id) { mutableIntStateOf(0) }
    val index = pageIndex.coerceIn(0, board.pages.lastIndex)
    val page = board.pages[index]
    val description = stringResource(R.string.cloud_preview_canvas)
    var native by remember { mutableStateOf<WhiteboardView?>(null) }
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { pageIndex = index - 1 }, enabled = index > 0) { Text(stringResource(R.string.cloud_previous_page)) }
            Text("${index + 1} / ${board.pages.size}")
            TextButton(onClick = {
                pageIndex = index + 1
            }, enabled = index < board.pages.lastIndex) { Text(stringResource(R.string.cloud_next_page)) }
            IconButton(onClick = { native?.fitContent() }) { Icon(Icons.Outlined.FitScreen, stringResource(R.string.fit_content)) }
        }
        key(board.id, page.id) {
            AndroidView(
                factory = { context ->
                    WhiteboardView(context).also {
                        it.contentDescription = description
                        it.update(page, Tool.PAN, InkStyle(), emptySet())
                        it.requestInitialFit()
                        native = it
                    }
                },
                update = { it.update(page, Tool.PAN, InkStyle(), emptySet()) },
                modifier = Modifier.fillMaxWidth().weight(1f),
                onRelease = {
                    it.onEvent = {}
                    if (native === it) native = null
                }
            )
        }
    }
}

private fun roleLabel(role: CloudBoardRole) = when (role) {
    CloudBoardRole.OWNER -> R.string.cloud_role_owner
    CloudBoardRole.EDITOR -> R.string.cloud_role_editor
    CloudBoardRole.COMMENTER -> R.string.cloud_role_commenter
    CloudBoardRole.VIEWER -> R.string.cloud_role_viewer
}
