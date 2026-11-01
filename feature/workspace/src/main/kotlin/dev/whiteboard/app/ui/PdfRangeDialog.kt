package dev.whiteboard.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.whiteboard.ui.*
import dev.whiteboard.ui.R

@Composable
fun PdfRangeDialog(count: Int, apply: (Int, Int) -> Unit, dismiss: () -> Unit) {
    var first by remember { mutableStateOf("1") }
    var last by remember { mutableStateOf(count.toString()) }
    val from = first.toIntOrNull()
    val to = last.toIntOrNull()
    val valid = from != null && to != null && from >= 1 && to >= from && to <= count
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(R.string.pdf_choose_pages)) },
        text = {
            Column {
                Text(stringResource(R.string.pdf_page_count, count))
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        first,
                        { first = it.filter(Char::isDigit).take(3) },
                        Modifier.weight(1f),
                        singleLine = true,
                        label = {
                            Text(stringResource(R.string.first_page))
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        last,
                        { last = it.filter(Char::isDigit).take(3) },
                        Modifier.weight(1f),
                        singleLine = true,
                        label = {
                            Text(stringResource(R.string.last_page))
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                apply(requireNotNull(from), requireNotNull(to))
            }, enabled = valid) { Text(stringResource(R.string.apply)) }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
