package dev.whiteboard.app.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.whiteboard.app.workspace.ConnectionRequest
import dev.whiteboard.canvas.describeCanvasElement
import dev.whiteboard.core.model.*
import dev.whiteboard.ui.InputAlertDialog
import dev.whiteboard.ui.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

@Composable
internal fun ConnectionDialog(
    request: ConnectionRequest,
    busy: Boolean,
    error: Int?,
    dismiss: () -> Unit,
    apply: (ConnectorEnd, ConnectorEnd, Boolean) -> Unit
) {
    var startTarget by rememberSaveable(request.id) { mutableStateOf((request.start as? ConnectorEnd.Bound)?.targetId) }
    var endTarget by rememberSaveable(request.id) { mutableStateOf((request.end as? ConnectorEnd.Bound)?.targetId) }
    var startPort by rememberSaveable(request.id) { mutableStateOf((request.start as? ConnectorEnd.Bound)?.port ?: ConnectionPort.RIGHT) }
    var endPort by rememberSaveable(request.id) { mutableStateOf((request.end as? ConnectorEnd.Bound)?.port ?: ConnectionPort.LEFT) }
    var startX by rememberSaveable(request.id) { mutableStateOf(request.startPosition.x.toString()) }
    var startY by rememberSaveable(request.id) { mutableStateOf(request.startPosition.y.toString()) }
    var endX by rememberSaveable(request.id) { mutableStateOf(request.endPosition.x.toString()) }
    var endY by rememberSaveable(request.id) { mutableStateOf(request.endPosition.y.toString()) }
    var arrow by rememberSaveable(request.id) { mutableStateOf(request.arrow) }
    var choosing by rememberSaveable(request.id) { mutableStateOf<Int?>(null) }
    val context = LocalContext.current
    fun endpoint(target: String?, port: ConnectionPort, x: String, y: String): ConnectorEnd? {
        if (target != null && target !in request.targetIndex) return null
        return try {
            if (target == null) {
                val first = coordinate(x) ?: return null
                val second = coordinate(y) ?: return null
                ConnectorEnd.Free(Point(first, second))
            } else {
                ConnectorEnd.Bound(target, port)
            }
        } catch (_: IllegalArgumentException) {
            null
        }
    }
    if (choosing != null) {
        ConnectionTargetDialog(request, { choosing = null }) { target ->
            if (choosing == 0) {
                if (target == null && startTarget != null && startTarget in request.targetIndex) {
                    val point = request.page.geometry.connectionPorts().point(checkNotNull(startTarget), startPort)
                    startX = point.x.toString()
                    startY = point.y.toString()
                }
                startTarget = target
            } else {
                if (target == null && endTarget != null && endTarget in request.targetIndex) {
                    val point = request.page.geometry.connectionPorts().point(checkNotNull(endTarget), endPort)
                    endX = point.x.toString()
                    endY = point.y.toString()
                }
                endTarget = target
            }
            choosing = null
        }
        return
    }
    val start = endpoint(startTarget, startPort, startX, startY)
    val end = endpoint(endTarget, endPort, endX, endY)
    val different = start !is ConnectorEnd.Bound || end !is ConnectorEnd.Bound || start.targetId != end.targetId
    val valid = start != null && end != null && different
    InputAlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(if (request.existing == null) R.string.add_connection else R.string.edit_connection)) },
        text = { close ->
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.connection_help))
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
                ConnectionEndFields(
                    stringResource(R.string.connection_start), targetLabel(context, request, startTarget), startTarget == null,
                    startPort, startX, startY, !busy, {
                        close()
                        choosing = 0
                    }, { startPort = it }, { startX = it }, { startY = it }
                )
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                ConnectionEndFields(
                    stringResource(R.string.connection_end), targetLabel(context, request, endTarget), endTarget == null,
                    endPort, endX, endY, !busy, {
                        close()
                        choosing = 1
                    }, { endPort = it }, { endX = it }, { endY = it }
                )
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics(mergeDescendants = true) {}
                        .toggleable(arrow, enabled = !busy, role = Role.Switch) { arrow = it }
                ) {
                    Text(stringResource(R.string.connection_arrow), Modifier.weight(1f).padding(top = 12.dp))
                    Switch(arrow, null, enabled = !busy)
                }
                val message = error ?: if (!valid) R.string.connection_invalid else null
                if (message != null) {
                    Text(
                        stringResource(message),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = { close ->
            TextButton(enabled = valid && !busy, onClick = {
                val currentStart = endpoint(startTarget, startPort, startX, startY)
                val currentEnd = endpoint(endTarget, endPort, endX, endY)
                if (currentStart != null && currentEnd != null &&
                    (
                        currentStart !is ConnectorEnd.Bound || currentEnd !is ConnectorEnd.Bound ||
                            currentStart.targetId != currentEnd.targetId
                        )
                ) {
                    close()
                    apply(currentStart, currentEnd, arrow)
                }
            }) { Text(stringResource(R.string.apply)) }
        },
        dismissButton = { close ->
            TextButton(onClick = {
                close()
                dismiss()
            }) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun ConnectionEndFields(
    title: String,
    target: String,
    free: Boolean,
    port: ConnectionPort,
    x: String,
    y: String,
    enabled: Boolean,
    choose: () -> Unit,
    changePort: (ConnectionPort) -> Unit,
    changeX: (String) -> Unit,
    changeY: (String) -> Unit
) {
    Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
    OutlinedButton(
        onClick = choose,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().semantics {
            contentDescription =
                "$title: $target"
        }
    ) {
        Text(target)
    }
    if (free) {
        val labelX = "$title: ${stringResource(R.string.connection_x)}"
        val labelY = "$title: ${stringResource(R.string.connection_y)}"
        val sign = stringResource(R.string.connection_sign)
        Text(stringResource(R.string.connection_coordinate_help), style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            x, { changeX(it.take(32)) }, enabled = enabled, singleLine = true, isError = coordinate(x) == null,
            label = {
                Text(labelX)
            }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                IconButton(
                    onClick = { changeX(changeSign(x)) },
                    enabled = enabled,
                    modifier = Modifier.semantics {
                        contentDescription =
                            "$labelX: $sign"
                    }
                ) {
                    Text("±")
                }
            }
        )
        OutlinedTextField(
            y, { changeY(it.take(32)) }, enabled = enabled, singleLine = true, isError = coordinate(y) == null,
            label = {
                Text(labelY)
            }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                IconButton(
                    onClick = { changeY(changeSign(y)) },
                    enabled = enabled,
                    modifier = Modifier.semantics {
                        contentDescription =
                            "$labelY: $sign"
                    }
                ) {
                    Text("±")
                }
            }
        )
    } else {
        var expanded by rememberSaveable { mutableStateOf(false) }
        val attachment = stringResource(R.string.connection_attachment)
        val label = stringResource(portLabel(port))
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "$title: $attachment: $label" }
            ) { Text("$attachment: $label") }
            DropdownMenu(expanded, { expanded = false }) {
                ConnectionPort.entries.forEach { value ->
                    DropdownMenuItem(text = { Text(stringResource(portLabel(value))) }, onClick = {
                        changePort(value)
                        expanded = false
                    })
                }
            }
        }
    }
}

@Composable
private fun ConnectionTargetDialog(request: ConnectionRequest, dismiss: () -> Unit, choose: (String?) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val filtered by produceState(request.targets, request.id, query, context) {
        value = withContext(Dispatchers.Default) {
            val calculation = currentCoroutineContext()
            val search = query.trim()
            if (search.isEmpty()) {
                request.targets
            } else {
                request.targets.filter {
                    calculation.ensureActive()
                    describeCanvasElement(context, it).contains(search, ignoreCase = true)
                }
            }
        }
    }
    InputAlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(R.string.connection_find_target)) },
        text = { close ->
            Column {
                OutlinedTextField(
                    query,
                    { query = it.take(512) },
                    singleLine = true,
                    label = { Text(stringResource(R.string.connection_find_target)) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { close() })
                )
                LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 320.dp)) {
                    item {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.connection_free)) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button) {
                                close()
                                choose(null)
                            }
                        )
                    }
                    items(filtered, key = { it.id }) { element ->
                        ListItem(
                            headlineContent = { Text(targetLabel(context, request, element.id)) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button) {
                                close()
                                choose(element.id)
                            }
                        )
                    }
                    if (filtered.isEmpty()) {
                        item {
                            Text(stringResource(R.string.connection_no_targets), Modifier.padding(vertical = 12.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { close ->
            TextButton(onClick = {
                close()
                dismiss()
            }) { Text(stringResource(R.string.done)) }
        },
        dismissButton = {}
    )
}

private fun targetLabel(context: Context, request: ConnectionRequest, id: String?): String {
    if (id == null) return context.getString(R.string.connection_free)
    val index = request.targetIndex[id] ?: return context.getString(R.string.connection_find_target)
    return "${index + 1}. ${describeCanvasElement(context, request.targets[index])}"
}

private fun coordinate(text: String): Float? = text.trim().replace(',', '.').toFloatOrNull()
    ?.takeIf { it.isFinite() && it in -1_000_000_000f..1_000_000_000f }

private fun changeSign(text: String): String = text.trim().let { if (it.startsWith('-')) it.drop(1) else "-$it" }

private fun portLabel(port: ConnectionPort): Int = when (port) {
    ConnectionPort.LEFT -> R.string.connection_left
    ConnectionPort.TOP -> R.string.connection_top
    ConnectionPort.RIGHT -> R.string.connection_right
    ConnectionPort.BOTTOM -> R.string.connection_bottom
    ConnectionPort.CENTER -> R.string.connection_center
}
