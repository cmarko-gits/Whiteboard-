package dev.whiteboard.app

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import dagger.hilt.android.AndroidEntryPoint
import dev.whiteboard.app.ui.LocalWhiteboardApp
import dev.whiteboard.app.workspace.LibraryViewModel
import dev.whiteboard.app.workspace.LocalMediaStore
import dev.whiteboard.app.workspace.WorkspaceViewModel
import dev.whiteboard.ui.R
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject lateinit var media: LocalMediaStore
    private val workspace: WorkspaceViewModel by viewModels()
    private val library: LibraryViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LocalWhiteboardApp(workspace, library, media) { file ->
                val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    clipData = android.content.ClipData.newUri(contentResolver, "Whiteboard PNG", uri)
                }
                startActivity(Intent.createChooser(intent, getString(R.string.share_board)))
            }
        }
    }
    override fun onStop() {
        workspace.flushRecovery()
        super.onStop()
    }
    override fun onKeyShortcut(keyCode: Int, event: KeyEvent): Boolean {
        val editor = workspace.state.value.editor
        if (editor == null || editor.saving || !(event.isCtrlPressed || event.isMetaPressed) || event.isAltPressed) {
            return super.onKeyShortcut(keyCode, event)
        }
        val action: (() -> Unit)? = when {
            keyCode == KeyEvent.KEYCODE_S && editor.dirty -> ({ workspace.save() })
            keyCode == KeyEvent.KEYCODE_Z && event.isShiftPressed && editor.canRedo -> workspace::redo
            keyCode == KeyEvent.KEYCODE_Z && !event.isShiftPressed && editor.canUndo -> workspace::undo
            keyCode == KeyEvent.KEYCODE_Y && editor.canRedo -> workspace::redo
            keyCode == KeyEvent.KEYCODE_A && editor.page.elements.isNotEmpty() -> workspace::selectAll
            keyCode == KeyEvent.KEYCODE_C && editor.selectedIds.isNotEmpty() -> ({ workspace.copySelected() })
            keyCode == KeyEvent.KEYCODE_X && editor.selectedIds.isNotEmpty() -> ({ workspace.copySelected(cut = true) })
            keyCode == KeyEvent.KEYCODE_V && editor.canPaste -> workspace::paste
            else -> null
        }
        if (action == null) return super.onKeyShortcut(keyCode, event)
        action()
        return true
    }
}
