package dev.whiteboard.app.ui

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.whiteboard.ui.*
import dev.whiteboard.ui.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun NoticesDialog(dismiss: () -> Unit) {
    val context = LocalContext.current
    val paragraphs by produceState<List<String>?>(null) {
        value = withContext(Dispatchers.IO) {
            context.assets.open("open-source-notices.txt").bufferedReader().use { it.readText() }
                .split(Regex("""\r?\n\s*\r?\n""")).filter { it.isNotBlank() }
        }
    }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(R.string.open_source_licenses)) },
        text = {
            if (paragraphs == null) {
                CircularProgressIndicator()
            } else {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(paragraphs.orEmpty()) { Text(it, Modifier.padding(bottom = 12.dp), style = MaterialTheme.typography.bodySmall) }
                }
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.done)) } }
    )
}
