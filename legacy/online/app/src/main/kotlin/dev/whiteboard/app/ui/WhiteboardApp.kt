package dev.whiteboard.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.whiteboard.app.BuildConfig
import dev.whiteboard.app.workspace.AccountViewModel
import dev.whiteboard.app.workspace.CloudViewModel
import dev.whiteboard.app.workspace.LibraryViewModel
import dev.whiteboard.app.workspace.LocalMediaStore
import dev.whiteboard.app.workspace.WorkspaceViewModel
import dev.whiteboard.core.model.BoardSummary
import dev.whiteboard.ui.*
import dev.whiteboard.ui.R
import java.io.File

@Composable
fun WhiteboardApp(
    viewModel: WorkspaceViewModel,
    library: LibraryViewModel,
    account: AccountViewModel,
    cloud: CloudViewModel,
    media: LocalMediaStore,
    share: (File) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val libraryState by library.state.collectAsStateWithLifecycle()
    val identity by account.account.collectAsStateWithLifecycle()
    val cloudState by cloud.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val screenState = rememberSaveableStateHolder()
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showAccount by rememberSaveable { mutableStateOf(false) }
    var showCloud by rememberSaveable { mutableStateOf(false) }
    var publishSource by remember { mutableStateOf<BoardSummary?>(null) }
    val copySuffix = stringResource(R.string.cloud_copy_suffix)
    var confirmExit by rememberSaveable { mutableStateOf(false) }
    val export =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
            uri?.toString()?.let(viewModel::exportPng)
        }
    val backup =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
            uri?.toString()?.let(viewModel::backup)
        }
    val restore =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.toString()?.let(viewModel::restoreBackup) }
    val imagePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.toString()?.let(viewModel::importImage) }
    val pdfPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.toString()?.let(viewModel::importPdf) }
    val jpegExport =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { uri ->
            uri?.toString()?.let(viewModel::exportJpeg)
        }
    val pdfExport =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
            uri?.toString()?.let(viewModel::exportPdf)
        }
    var pendingPhoto by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) pendingPhoto?.let { viewModel.importImage(it) }
        pendingPhoto = null
    }
    val notice = state.message ?: libraryState.message
    val workspaceNotice = state.message != null
    val retryLabel = stringResource(R.string.retry_operation)
    val noticeText = notice?.let {
        val count = it.count
        if (count == null) stringResource(it.resource) else stringResource(it.resource, count)
    }
    LaunchedEffect(notice?.id, noticeText) {
        noticeText?.let {
            val result = snackbar.showSnackbar(it, actionLabel = if (notice.retry) retryLabel else null)
            if (result == SnackbarResult.ActionPerformed && workspaceNotice) viewModel.retryFailedOperation()
            if (workspaceNotice) viewModel.dismissMessage(notice.id) else library.dismissMessage(notice.id)
        }
    }
    val leave: () -> Unit = {
        if (state.editor?.dirty == true) confirmExit = true else viewModel.leave()
    }
    BackHandler(enabled = state.editor != null) { if (state.editor?.saving != true) leave() }
    WhiteboardTheme(state.settings.theme) {
        Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { insets ->
            Box(Modifier.fillMaxSize().padding(insets)) {
                val editor = state.editor
                if (editor == null) {
                    screenState.SaveableStateProvider("library") {
                        LibraryScreen(
                            libraryState.copy(
                                loading = libraryState.loading || state.loading || !state.settingsLoaded
                            ),
                            library,
                            viewModel,
                            onAccount = { showAccount = true },
                            onCloud = { showCloud = true },
                            onPublish = { source -> if (identity == null) showAccount = true else publishSource = source },
                            onSettings = {
                                showSettings =
                                    true
                            }
                        )
                    }
                } else {
                    EditorScreen(
                        editor = editor,
                        fingerDrawing = state.settings.fingerDrawing,
                        snap = state.settings.snap,
                        brushes = state.settings.brushes,
                        favoriteColors = state.settings.favoriteColors,
                        exporting = state.exporting,
                        viewModel = viewModel,
                        assets = media,
                        onBack = leave,
                        onExport = { export.launch("${editor.name.replace(Regex("[^\\p{L}\\p{N} _-]"), "_")}.png") },
                        onShare = { viewModel.sharePng(share) },
                        onImage = { imagePicker.launch(arrayOf("image/*")) },
                        onCamera = {
                            val uri = media.cameraUri()
                            pendingPhoto = uri.toString()
                            try {
                                camera.launch(uri)
                            } catch (
                                _: android.content.ActivityNotFoundException
                            ) {
                                pendingPhoto = null
                                viewModel.cameraUnavailable()
                            }
                        },
                        onPdf = { pdfPicker.launch(arrayOf("application/pdf")) },
                        onJpegExport = { jpegExport.launch("Whiteboard.jpg") },
                        onPdfExport = { pdfExport.launch("Whiteboard.pdf") },
                        onSettings = { showSettings = true }
                    )
                }
            }
        }
        if (showSettings) {
            SettingsDialog(
                state.settings,
                viewModel,
                versionName = BuildConfig.VERSION_NAME,
                onBackup = {
                    showSettings = false
                    backup.launch("Whiteboard-backup.zip")
                },
                onRestore = {
                    showSettings = false
                    restore.launch(arrayOf("application/zip", "application/octet-stream"))
                },
                canRestore = state.editor == null,
                onAccount = {
                    showSettings = false
                    showAccount = true
                },
                dismiss = { showSettings = false }
            )
        }
        if (showCloud && state.editor == null) {
            CloudScreen(
                cloud,
                onAccount = { showAccount = true },
                onCopy = { source ->
                    showCloud = false
                    cloud.closePreview()
                    viewModel.createPrivateCopy(source, source.name.take(120 - copySuffix.length) + copySuffix)
                },
                dismiss = { showCloud = false }
            )
        }
        publishSource?.let { source ->
            AlertDialog(
                onDismissRequest = { publishSource = null },
                title = { Text(stringResource(R.string.cloud_publish)) },
                text = { Text(stringResource(R.string.cloud_publish_description, source.name, identity?.email.orEmpty())) },
                confirmButton = {
                    TextButton(onClick = {
                        publishSource = null
                        showCloud = true
                        cloud.publish(source.id)
                    }, enabled = identity != null && !cloudState.busy) { Text(stringResource(R.string.cloud_publish_confirm)) }
                },
                dismissButton = { TextButton(onClick = { publishSource = null }) { Text(stringResource(R.string.cancel)) } }
            )
        }
        if (showAccount) AccountDialog(account) { showAccount = false }
        if (state.settingsLoaded && !state.settings.onboarded) OnboardingDialog(viewModel::finishOnboarding)
        if (confirmExit) {
            AlertDialog(
                onDismissRequest = { confirmExit = false },
                title = { Text(stringResource(R.string.unsaved_changes)) },
                text = { Text(stringResource(R.string.unsaved_description)) },
                confirmButton = {
                    TextButton(onClick = {
                        confirmExit = false
                        viewModel.save(closeAfter = true)
                    }) { Text(stringResource(R.string.save_and_close)) }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = {
                            confirmExit = false
                            viewModel.leave(discard = true)
                        }) { Text(stringResource(R.string.discard)) }
                        TextButton(onClick = { confirmExit = false }) { Text(stringResource(R.string.cancel)) }
                    }
                }
            )
        }
        state.recovery?.let {
            AlertDialog(
                onDismissRequest = {},
                title = { Text(stringResource(R.string.recover_title)) },
                text = { Text(stringResource(R.string.recover_description, it.saved.name)) },
                confirmButton = { TextButton(onClick = { viewModel.recover(true) }) { Text(stringResource(R.string.recover)) } },
                dismissButton = { TextButton(onClick = { viewModel.recover(false) }) { Text(stringResource(R.string.use_saved)) } }
            )
        }
        state.textRequest?.let {
            TextEditorDialog(
                it,
                state.editor?.ink?.color ?: 0xFF202621.toInt(),
                viewModel::applyText,
                viewModel::dismissText,
                state.settings.favoriteColors,
                viewModel::favoriteColors
            )
        }
        state.pdfImport?.let { PdfRangeDialog(it.pages.size, viewModel::applyPdfRange) { viewModel.dismissPdf() } }
    }
}
