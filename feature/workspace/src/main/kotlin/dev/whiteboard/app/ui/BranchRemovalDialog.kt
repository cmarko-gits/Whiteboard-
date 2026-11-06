package dev.whiteboard.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.whiteboard.app.workspace.BranchRemovalRequest
import dev.whiteboard.ui.InputAlertDialog
import dev.whiteboard.ui.R

@Composable
internal fun BranchRemovalDialog(request: BranchRemovalRequest, dismiss: () -> Unit, confirm: () -> Unit) {
    val action = stringResource(if (request.cut) R.string.branch_cut else R.string.branch_delete)
    InputAlertDialog(
        onDismissRequest = dismiss,
        title = { Text(action) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(
                        if (request.cut) R.string.branch_cut_message else R.string.branch_remove_message,
                        request.nodeCount,
                        request.objectCount
                    )
                )
            }
        },
        confirmButton = { closeKeyboard ->
            TextButton(onClick = {
                closeKeyboard()
                confirm()
            }) { Text(action) }
        },
        dismissButton = { closeKeyboard ->
            TextButton(onClick = {
                closeKeyboard()
                dismiss()
            }) { Text(stringResource(R.string.cancel)) }
        }
    )
}
