package dev.whiteboard.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.whiteboard.app.workspace.AccountScreen
import dev.whiteboard.app.workspace.AccountViewModel
import dev.whiteboard.ui.*
import dev.whiteboard.ui.R

@Composable
fun AccountDialog(viewModel: AccountViewModel, dismiss: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val account by viewModel.account.collectAsStateWithLifecycle()
    var email by rememberSaveable { mutableStateOf("") }
    // Password/action-code fields are deliberately excluded from saved instance state.
    var password by remember(state.formRevision) { mutableStateOf("") }
    var code by remember(state.formRevision) { mutableStateOf("") }
    val submitLabel = stringResource(R.string.account_continue)
    val title = when (state.screen) {
        AccountScreen.LOGIN -> if (account != null) R.string.account_title else R.string.account_sign_in
        AccountScreen.REGISTER -> R.string.account_register
        AccountScreen.VERIFY -> R.string.account_verify
        AccountScreen.FORGOT -> R.string.account_forgot
        AccountScreen.RESET -> R.string.account_reset
        AccountScreen.DELETE -> R.string.account_delete
    }
    AlertDialog(
        onDismissRequest = { if (!state.busy) dismiss() },
        title = { Text(stringResource(title)) },
        text = {
            Column(
                Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!viewModel.available) {
                    Text(stringResource(R.string.account_unavailable))
                } else if (account != null && state.screen != AccountScreen.DELETE) {
                    Text(account!!.email, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.account_local_separate))
                    TextButton(onClick = viewModel::logout, enabled = !state.busy) { Text(stringResource(R.string.account_sign_out)) }
                    TextButton(onClick = { viewModel.screen(AccountScreen.DELETE) }, enabled = !state.busy) {
                        Text(stringResource(R.string.account_delete), color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    when (state.screen) {
                        AccountScreen.REGISTER -> Text(stringResource(R.string.account_register_help))
                        AccountScreen.VERIFY -> Text(stringResource(R.string.account_verify_help))
                        AccountScreen.DELETE -> Text(stringResource(R.string.account_delete_help))
                        else -> Unit
                    }
                    if (state.screen in setOf(AccountScreen.LOGIN, AccountScreen.REGISTER, AccountScreen.FORGOT, AccountScreen.VERIFY)) {
                        OutlinedTextField(
                            email,
                            { email = it.take(254) },
                            label = { Text(stringResource(R.string.account_email)) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.busy,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                        )
                    }
                    if (state.screen in setOf(AccountScreen.VERIFY, AccountScreen.RESET)) {
                        OutlinedTextField(
                            code,
                            { code = it.take(64) },
                            label = { Text(stringResource(R.string.account_code)) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.busy,
                            singleLine = true
                        )
                    }
                    if (state.screen in setOf(AccountScreen.LOGIN, AccountScreen.REGISTER, AccountScreen.RESET, AccountScreen.DELETE)) {
                        OutlinedTextField(
                            password,
                            { password = it.take(128) },
                            label = { Text(stringResource(R.string.account_password)) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.busy,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            visualTransformation = PasswordVisualTransformation()
                        )
                        if (state.screen in
                            setOf(AccountScreen.REGISTER, AccountScreen.RESET)
                        ) {
                            Text(stringResource(R.string.account_password_help), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (state.screen == AccountScreen.LOGIN) {
                        TextButton(onClick = {
                            viewModel.screen(AccountScreen.REGISTER)
                        }, enabled = !state.busy) { Text(stringResource(R.string.account_register)) }
                        TextButton(onClick = {
                            viewModel.screen(AccountScreen.FORGOT)
                        }, enabled = !state.busy) { Text(stringResource(R.string.account_forgot)) }
                        TextButton(onClick = {
                            viewModel.screen(AccountScreen.VERIFY)
                        }, enabled = !state.busy) { Text(stringResource(R.string.account_verify)) }
                    }
                    if (state.screen == AccountScreen.VERIFY) {
                        TextButton(onClick = {
                            viewModel.resend(email)
                        }, enabled = !state.busy && email.isNotBlank()) { Text(stringResource(R.string.account_resend)) }
                    }
                    if (state.screen != AccountScreen.LOGIN) {
                        TextButton(onClick = {
                            viewModel.screen(AccountScreen.LOGIN)
                        }, enabled = !state.busy) { Text(stringResource(R.string.account_back)) }
                    }
                }
                state.message?.let {
                    Text(
                        stringResource(it),
                        color = if (state.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            if (viewModel.available && (account == null || state.screen == AccountScreen.DELETE)) {
                val valid = when (state.screen) {
                    AccountScreen.LOGIN -> email.isNotBlank() && password.isNotEmpty()
                    AccountScreen.REGISTER -> email.isNotBlank() && password.length >= 12
                    AccountScreen.VERIFY -> code.isNotBlank()
                    AccountScreen.FORGOT -> email.isNotBlank()
                    AccountScreen.RESET -> code.isNotBlank() && password.length >= 12
                    AccountScreen.DELETE -> password.isNotEmpty()
                }
                TextButton(onClick = {
                    viewModel.submit(email, password, code)
                }, enabled = valid && !state.busy, modifier = Modifier.semantics { contentDescription = submitLabel }) {
                    Text(stringResource(title))
                }
            }
        },
        dismissButton = { TextButton(onClick = dismiss, enabled = !state.busy) { Text(stringResource(R.string.done)) } }
    )
}
