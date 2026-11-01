package dev.whiteboard.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import dev.whiteboard.core.model.BoardTemplate
import dev.whiteboard.ui.*
import dev.whiteboard.ui.R

@Composable
fun NewBoardDialog(dismiss: () -> Unit, create: (String, BoardTemplate) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf(BoardTemplate.BLANK) }
    val confirmFocus = remember { FocusRequester() }
    val cancelFocus = remember { FocusRequester() }
    InputAlertDialog(
        onDismissRequest = dismiss,
        modifier = Modifier.systemBarsPadding().imePadding(),
        properties = DialogProperties(decorFitsSystemWindows = false),
        title = { Text(stringResource(R.string.new_board)) },
        text = { closeKeyboard ->
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    name,
                    { name = it.take(120) },
                    label = { Text(stringResource(R.string.board_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        closeKeyboard()
                        // Give keyboard focus a stable destination, including on older Android dialogs.
                        if (name.isNotBlank()) confirmFocus.requestFocus() else cancelFocus.requestFocus()
                    })
                )
                Text(
                    stringResource(R.string.template),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 16.dp)
                )
                BoardTemplate.entries.forEach { template ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .selectable(selected == template, role = Role.RadioButton) { selected = template },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected == template, onClick = null)
                        Text(stringResource(templateLabel(template)))
                    }
                }
            }
        },
        confirmButton = { closeKeyboard ->
            TextButton(
                onClick = {
                    closeKeyboard()
                    create(name.trim(), selected)
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.focusProperties {
                    canFocus = name.isNotBlank()
                }.focusRequester(confirmFocus)
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = { closeKeyboard ->
            TextButton(onClick = {
                closeKeyboard()
                dismiss()
            }, modifier = Modifier.focusProperties { canFocus = true }.focusRequester(cancelFocus)) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

private fun templateLabel(template: BoardTemplate) = when (template) {
    BoardTemplate.BLANK -> R.string.plain
    BoardTemplate.GRID -> R.string.grid
    BoardTemplate.DOTS -> R.string.dots
    BoardTemplate.RULED -> R.string.ruled
    BoardTemplate.BRAINSTORM -> R.string.brainstorm
    BoardTemplate.KANBAN -> R.string.kanban
    BoardTemplate.WEEKLY -> R.string.weekly
    BoardTemplate.STUDY -> R.string.study
    BoardTemplate.MIND_MAP -> R.string.mind_map
    BoardTemplate.FLOWCHART -> R.string.flowchart
    BoardTemplate.ARCHITECTURE -> R.string.architecture
}
