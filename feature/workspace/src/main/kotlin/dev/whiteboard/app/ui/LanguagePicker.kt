package dev.whiteboard.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.whiteboard.core.model.AppLanguage
import dev.whiteboard.ui.R

@Composable
fun LanguagePicker(selected: AppLanguage?, choose: (AppLanguage) -> Unit) {
    Text(stringResource(R.string.app_language), fontWeight = FontWeight.SemiBold)
    Text(stringResource(R.string.app_language_help), style = MaterialTheme.typography.bodySmall)
    Column(Modifier.selectableGroup()) {
        AppLanguage.entries.forEach { language ->
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .selectable(selected = selected == language, role = Role.RadioButton, onClick = { choose(language) })
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected == language, onClick = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(
                        when (language) {
                            AppLanguage.SYSTEM -> R.string.app_language_system
                            AppLanguage.ENGLISH -> R.string.app_language_english
                            AppLanguage.SERBIAN_LATIN -> R.string.app_language_serbian
                        }
                    )
                )
            }
        }
    }
}
