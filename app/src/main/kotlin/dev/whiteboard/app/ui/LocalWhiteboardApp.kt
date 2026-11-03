package dev.whiteboard.app.ui

import android.content.ActivityNotFoundException
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.whiteboard.app.BuildConfig
import dev.whiteboard.app.workspace.LibraryViewModel
import dev.whiteboard.app.workspace.LocalMediaStore
import dev.whiteboard.app.workspace.WorkspaceUi
import dev.whiteboard.app.workspace.WorkspaceViewModel
import dev.whiteboard.core.model.AppLanguage
import dev.whiteboard.ui.R
import dev.whiteboard.ui.WhiteboardTheme
import java.io.File
import java.io.IOException

/** Local composition root: lifecycle state, navigation and explicit system document actions. */
@Composable
fun LocalWhiteboardApp(workspace: WorkspaceViewModel, library: LibraryViewModel, media: LocalMediaStore, share: (File) -> Unit) {
    val state by workspace.state.collectAsStateWithLifecycle()
    val boards by library.state.collectAsStateWithLifecycle()
    val savedScreens = rememberSaveableStateHolder()
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val language = remember(configuration) { AppLanguage.fromTags(AppCompatDelegate.getApplicationLocales().toLanguageTags()) }
    val documents = rememberLocalDocumentActions(workspace, media)
    val snackbar = remember { SnackbarHostState() }
    val notice = state.message ?: boards.message
    val workspaceNotice = state.message != null
    val retry = stringResource(R.string.retry_operation)
    val message = notice?.let {
        val count = it.count
        if (count == null) stringResource(it.resource) else stringResource(it.resource, count)
    }
    LaunchedEffect(notice?.id, message) {
        if (notice != null && message != null) {
            val result = snackbar.showSnackbar(message, if (notice.retry) retry else null)
            if (workspaceNotice) {
                if (result == SnackbarResult.ActionPerformed) workspace.retryFailedOperation()
                workspace.dismissMessage(notice.id)
            } else {
                if (result == SnackbarResult.ActionPerformed) library.retry()
                library.dismissMessage(notice.id)
            }
        }
    }
    val leave: () -> Unit = {
        if (state.editor?.dirty == true) confirmLeave = true else workspace.leave()
    }
    BackHandler(state.editor != null) { if (state.editor?.saving != true) leave() }
    WhiteboardTheme(state.settings.theme) {
        Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { insets ->
            Box(Modifier.fillMaxSize().padding(insets)) {
                val editor = state.editor
                if (editor == null) {
                    savedScreens.SaveableStateProvider("local-library") {
                        LibraryScreen(
                            boards.copy(loading = boards.loading || state.loading || !state.settingsLoaded),
                            library,
                            workspace,
                            onSettings = { settingsOpen = true }
                        )
                    }
                } else {
                    EditorScreen(
                        editor = editor,
                        fingerDrawing = state.settings.fingerDrawing,
                        snap = state.settings.snap,
                        eraserMode = state.settings.eraserMode,
                        brushes = state.settings.brushes,
                        favoriteColors = state.settings.favoriteColors,
                        exporting = state.exporting,
                        rotating = state.rotating,
                        rotationSequence = state.rotationSequence,
                        viewModel = workspace,
                        assets = media,
                        onBack = leave,
                        onExport = documents.png,
                        onShare = { workspace.sharePng(share) },
                        onImage = documents.image,
                        onCamera = documents.camera,
                        onPdf = documents.pdf,
                        onJpegExport = documents.jpeg,
                        onPdfExport = documents.exportPdf,
                        onSettings = { settingsOpen = true }
                    )
                }
            }
        }
        if (settingsOpen) {
            SettingsDialog(
                state.settings,
                workspace,
                BuildConfig.VERSION_NAME,
                onBackup = {
                    settingsOpen = false
                    documents.backup()
                },
                onRestore = {
                    settingsOpen = false
                    documents.restore()
                },
                canRestore = state.editor == null,
                language = language,
                onLanguage = { choice ->
                    val locales = LocaleListCompat.forLanguageTags(choice.tag)
                    if (locales != AppCompatDelegate.getApplicationLocales()) {
                        workspace.flushRecovery()
                        AppCompatDelegate.setApplicationLocales(locales)
                    }
                },
                dismiss = { settingsOpen = false }
            )
        }
        if (state.settingsLoaded && !state.settings.onboarded) OnboardingDialog(workspace::finishOnboarding)
        if (confirmLeave && state.editor != null) {
            AlertDialog(
                onDismissRequest = { confirmLeave = false },
                title = { Text(stringResource(R.string.unsaved_changes)) },
                text = { Text(stringResource(R.string.unsaved_description)) },
                confirmButton = {
                    TextButton(onClick = {
                        confirmLeave = false
                        workspace.save(closeAfter = true)
                    }) {
                        Text(stringResource(R.string.save_and_close))
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = {
                            confirmLeave = false
                            workspace.leave(discard = true)
                        }) {
                            Text(stringResource(R.string.discard))
                        }
                        TextButton(onClick = { confirmLeave = false }) { Text(stringResource(R.string.cancel)) }
                    }
                }
            )
        }
        LocalWorkspaceDialogs(state, workspace)
    }
}

@Composable
private fun LocalWorkspaceDialogs(state: WorkspaceUi, workspace: WorkspaceViewModel) {
    state.recovery?.let { recovery ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.recover_title)) },
            text = { Text(stringResource(R.string.recover_description, recovery.saved.name)) },
            confirmButton = { TextButton(onClick = { workspace.recover(true) }) { Text(stringResource(R.string.recover)) } },
            dismissButton = { TextButton(onClick = { workspace.recover(false) }) { Text(stringResource(R.string.use_saved)) } }
        )
    }
    state.textRequest?.let {
        TextEditorDialog(
            it,
            state.editor?.ink?.color ?: 0xFF202621.toInt(),
            workspace::applyText,
            workspace::dismissText,
            state.settings.favoriteColors,
            workspace::favoriteColors
        )
    }
    state.pdfImport?.let { PdfRangeDialog(it.pages.size, workspace::applyPdfRange, workspace::dismissPdf) }
}

private data class LocalDocumentActions(
    val png: () -> Unit,
    val jpeg: () -> Unit,
    val exportPdf: () -> Unit,
    val backup: () -> Unit,
    val restore: () -> Unit,
    val image: () -> Unit,
    val pdf: () -> Unit,
    val camera: () -> Unit
)

@Composable
private fun rememberLocalDocumentActions(workspace: WorkspaceViewModel, media: LocalMediaStore): LocalDocumentActions {
    val png =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { it?.toString()?.let(workspace::exportPng) }
    val jpeg =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) {
            it?.toString()?.let(workspace::exportJpeg)
        }
    val exportPdf =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) {
            it?.toString()?.let(workspace::exportPdf)
        }
    val backup =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) {
            it?.toString()?.let(workspace::backup)
        }
    val restore =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.toString()?.let(workspace::restoreBackup) }
    val image = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.toString()?.let(workspace::importImage) }
    val pdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.toString()?.let(workspace::importPdf) }
    var photo by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val captured = photo
        photo = null
        if (success && captured != null) workspace.importImage(captured)
    }
    return LocalDocumentActions(
        png = { png.launch("Whiteboard.png") },
        jpeg = { jpeg.launch("Whiteboard.jpg") },
        exportPdf = { exportPdf.launch("Whiteboard.pdf") },
        backup = { backup.launch("Whiteboard-backup.zip") },
        restore = { restore.launch(arrayOf("application/zip", "application/octet-stream")) },
        image = { image.launch(arrayOf("image/*")) },
        pdf = { pdf.launch(arrayOf("application/pdf")) },
        camera = {
            try {
                val uri = media.cameraUri()
                photo = uri.toString()
                camera.launch(uri)
            } catch (failure: Exception) {
                if (failure !is ActivityNotFoundException && failure !is SecurityException && failure !is IOException) throw failure
                photo = null
                workspace.cameraUnavailable()
            }
        }
    )
}
