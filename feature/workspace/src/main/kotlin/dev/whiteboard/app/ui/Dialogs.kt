package dev.whiteboard.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import dev.whiteboard.app.workspace.WorkspaceViewModel
import dev.whiteboard.canvas.CanvasEvent
import dev.whiteboard.core.model.*
import dev.whiteboard.core.model.ThemeMode
import dev.whiteboard.core.model.UserSettings
import dev.whiteboard.ui.*
import dev.whiteboard.ui.R

@Composable
fun NameDialog(title: String, initial: String, dismiss: () -> Unit, nameLabel: Int = R.string.board_name, submit: (String) -> Unit) {
    var name by rememberSaveable(initial) { mutableStateOf(initial) }
    InputAlertDialog(
        onDismissRequest = dismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(name, { name = it.take(120) }, label = { Text(stringResource(nameLabel)) }, singleLine = true)
        },
        confirmButton = { closeKeyboard ->
            TextButton(onClick = {
                closeKeyboard()
                submit(name.trim())
            }, enabled = name.isNotBlank()) { Text(stringResource(R.string.confirm)) }
        },
        dismissButton = { closeKeyboard ->
            TextButton(onClick = {
                closeKeyboard()
                dismiss()
            }) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
fun TextEditorDialog(
    request: CanvasEvent.EditText,
    defaultColor: Int,
    submit: (String, Float, Boolean, Boolean, TextAlignment, Int) -> Unit,
    dismiss: () -> Unit,
    favoriteColors: List<Int>,
    saveFavorites: (List<Int>) -> Unit
) {
    val existing = request.existing?.content()
    var text by rememberSaveable(request) {
        mutableStateOf(
            when (existing) {
                is Note -> existing.text
                is TextElement -> existing.text
                else -> ""
            }
        )
    }
    var size by rememberSaveable(request) {
        mutableFloatStateOf(
            when (existing) {
                is Note -> existing.fontSize
                is TextElement -> existing.fontSize
                else -> 24f
            }
        )
    }
    var bold by rememberSaveable(request) { mutableStateOf((existing as? TextElement)?.bold ?: false) }
    var italic by rememberSaveable(request) { mutableStateOf((existing as? TextElement)?.italic ?: false) }
    var alignment by rememberSaveable(request) { mutableStateOf((existing as? TextElement)?.alignment ?: TextAlignment.LEFT) }
    var color by rememberSaveable(request) {
        mutableIntStateOf(
            when (existing) {
                is Note -> existing.color
                is TextElement -> existing.color
                else -> if (request.note) noteColors.first() else defaultColor
            }
        )
    }
    InputAlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(if (request.note) R.string.note else R.string.text)) },
        modifier = Modifier.systemBarsPadding().imePadding(),
        properties = DialogProperties(decorFitsSystemWindows = false),
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                OutlinedTextField(text, {
                    text = it.take(20_000)
                }, modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp, max = 240.dp), label = {
                    Text(stringResource(R.string.enter_text))
                })
                Text(
                    "${stringResource(R.string.font_size)}: ${size.toInt()}",
                    modifier = Modifier.padding(top = 14.dp),
                    style = MaterialTheme.typography.labelMedium
                )
                val fontSizeLabel = stringResource(R.string.font_size)
                Slider(size, { size = it }, valueRange = 8f..72f, modifier = Modifier.semantics { contentDescription = fontSizeLabel })
                if (!request.note) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val boldLabel = stringResource(R.string.bold_text)
                        val italicLabel = stringResource(R.string.italic_text)
                        FilterChip(
                            bold,
                            { bold = !bold },
                            { Text("B", fontWeight = FontWeight.Bold) },
                            modifier = Modifier.semantics {
                                contentDescription =
                                    boldLabel
                            }
                        )
                        FilterChip(
                            italic,
                            {
                                italic = !italic
                            },
                            { Text("I", fontStyle = androidx.compose.ui.text.font.FontStyle.Italic) },
                            modifier = Modifier.semantics {
                                contentDescription =
                                    italicLabel
                            }
                        )
                        FilterChip(alignment == TextAlignment.CENTER, {
                            alignment =
                                if (alignment ==
                                    TextAlignment.CENTER
                                ) {
                                    TextAlignment.LEFT
                                } else {
                                    TextAlignment.CENTER
                                }
                        }, { Text(stringResource(R.string.center)) })
                        FilterChip(alignment == TextAlignment.RIGHT, {
                            alignment =
                                if (alignment ==
                                    TextAlignment.RIGHT
                                ) {
                                    TextAlignment.LEFT
                                } else {
                                    TextAlignment.RIGHT
                                }
                        }, { Text(stringResource(R.string.right)) })
                    }
                }
                ColorChoiceRow(color, if (request.note) noteColors else inkColors, favoriteColors, saveFavorites) { color = it }
            }
        },
        confirmButton = { closeKeyboard ->
            TextButton(onClick = {
                closeKeyboard()
                submit(text, size, bold, italic, alignment, color)
            }, enabled = text.isNotBlank()) { Text(stringResource(R.string.apply)) }
        },
        dismissButton = { closeKeyboard ->
            TextButton(onClick = {
                closeKeyboard()
                dismiss()
            }) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
fun SettingsDialog(
    settings: UserSettings,
    viewModel: WorkspaceViewModel,
    versionName: String,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    canRestore: Boolean,
    language: AppLanguage?,
    onLanguage: (AppLanguage) -> Unit,
    dismiss: () -> Unit
) {
    var notices by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(stringResource(R.string.settings)) }, text = {
        Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
            LanguagePicker(language, onLanguage)
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Text(stringResource(R.string.appearance), fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(settings.theme == mode, { viewModel.theme(mode) }, {
                        Text(
                            stringResource(
                                when (mode) {
                                    ThemeMode.SYSTEM -> R.string.system
                                    ThemeMode.LIGHT -> R.string.light
                                    ThemeMode.DARK -> R.string.dark
                                }
                            )
                        )
                    })
                }
            }
            SettingsSwitch(stringResource(R.string.finger_drawing), settings.fingerDrawing, viewModel::fingerDrawing)
            Text(stringResource(R.string.gesture_help), style = MaterialTheme.typography.bodySmall)
            SettingsSwitch(stringResource(R.string.smart_shapes), settings.smartShapes, viewModel::smartShapes)
            Text(stringResource(R.string.smart_shapes_help), style = MaterialTheme.typography.bodySmall)
            SettingsSwitch(stringResource(R.string.snap), settings.snap, viewModel::snap)
            SettingsSwitch(stringResource(R.string.autosave), settings.autosave, viewModel::autosave)
            Text(stringResource(R.string.eraser_mode), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
            Column(Modifier.selectableGroup()) {
                EraserMode.entries.forEach { mode ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(
                            selected = settings.eraserMode == mode,
                            role = Role.RadioButton,
                            onClick = { viewModel.eraserMode(mode) }
                        ),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        RadioButton(settings.eraserMode == mode, onClick = null)
                        Text(stringResource(if (mode == EraserMode.STROKE) R.string.eraser_whole else R.string.eraser_partial))
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 18.dp))
            Text(stringResource(R.string.backup_title), fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.backup_description), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onBackup) { Text(stringResource(R.string.export_backup)) }
            TextButton(onClick = onRestore, enabled = canRestore) { Text(stringResource(R.string.import_backup)) }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Text(stringResource(R.string.privacy), fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(R.string.privacy_description),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text("Whiteboard $versionName", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 18.dp))
            TextButton(onClick = { notices = true }) { Text(stringResource(R.string.open_source_licenses)) }
        }
    }, confirmButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.done)) } })
    if (notices) NoticesDialog { notices = false }
}

@Composable
fun OnboardingDialog(start: () -> Unit) {
    AlertDialog(
        onDismissRequest = {
            start()
        },
        icon = { Icon(Icons.Outlined.CloudOff, null) },
        title = { Text(stringResource(R.string.onboarding_title)) },
        text = {
            Text(stringResource(R.string.onboarding_description))
        },
        confirmButton = { Button(onClick = start) { Text(stringResource(R.string.start_creating)) } }
    )
}
