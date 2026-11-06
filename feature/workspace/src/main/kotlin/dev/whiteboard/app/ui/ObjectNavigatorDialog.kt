package dev.whiteboard.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.whiteboard.canvas.describeCanvasElement
import dev.whiteboard.core.model.*
import dev.whiteboard.ui.InputAlertDialog
import dev.whiteboard.ui.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Virtualized full-page alternative to selecting and finding objects by gestures. */
@Composable
fun ObjectNavigatorDialog(page: Page, dismiss: () -> Unit, select: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val elements by produceState(page.visibleElements, page.visibleElements, query, context) {
        value = withContext(Dispatchers.Default) {
            val search = query.trim()
            val calculation = currentCoroutineContext()
            if (search.isEmpty()) {
                page.visibleElements
            } else {
                page.visibleElements.filter { element ->
                    calculation.ensureActive()
                    describeCanvasElement(context, element).contains(search, ignoreCase = true) ||
                        element.leaves().map { it.content() }.any { leaf ->
                            when (leaf) {
                                is Note -> leaf.text.contains(search, ignoreCase = true)
                                is TextElement -> leaf.text.contains(search, ignoreCase = true)
                                else -> false
                            }
                        }
                }
            }
        }
    }
    InputAlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(R.string.objects)) },
        text = { closeKeyboard ->
            Column {
                Text(stringResource(R.string.objects_help))
                OutlinedTextField(
                    query,
                    {
                        query = it.take(512)
                    },
                    label = {
                        Text(stringResource(R.string.find_object))
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        closeKeyboard()
                    })
                )
                LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 360.dp)) {
                    items(elements, key = { it.id }) { element ->
                        ListItem(
                            headlineContent = { Text(describeCanvasElement(context, element)) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button) {
                                closeKeyboard()
                                select(element.id)
                            }
                        )
                    }
                    if (elements.isEmpty()) item { Text(stringResource(R.string.no_objects), Modifier.padding(vertical = 16.dp)) }
                }
            }
        },
        confirmButton = { closeKeyboard ->
            TextButton(onClick = {
                closeKeyboard()
                dismiss()
            }) { Text(stringResource(R.string.done)) }
        },
        dismissButton = {}
    )
}
