package dev.whiteboard.app

import android.content.Context
import android.os.SystemClock
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.*
import dev.whiteboard.core.model.*
import dev.whiteboard.data.*
import dev.whiteboard.ui.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MindMapBranchRemovalFlowTest {
    @get:Rule val capture = CaptureUiFailure()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val originalFontScale = device.executeShellCommand("settings get system font_scale").trim().toFloatOrNull()
    private val database = Room.databaseBuilder(context, WhiteboardDatabase::class.java, "whiteboard.db")
        .addMigrations(DatabaseMigrations.FROM_1_TO_2, DatabaseMigrations.FROM_2_TO_3, DatabaseMigrations.FROM_3_TO_4).build()
    private val repository = LocalBoardRepository(database)
    private val owned = mutableListOf<String>()
    private var completed = false
    private fun text(value: String) = checkNotNull(device.wait(Until.findObject(By.text(value)), 10_000)) { value }
    private fun control(value: String) = checkNotNull(device.wait(Until.findObject(By.desc(value).enabled(true)), 10_000)) { value }
    private fun button(value: String): UiObject2 {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            try {
                for (label in device.findObjects(By.text(value))) {
                    var owner: UiObject2? = label
                    var ancestors = 0
                    while (owner != null && !owner.isClickable && ancestors++ < 16) owner = owner.parent
                    if (owner?.isClickable == true && owner.isEnabled && !owner.visibleBounds.isEmpty) return owner
                }
            } catch (_: StaleObjectException) {
                // Re-read the connected hierarchy after a Compose update; retain no old handle.
            }
            SystemClock.sleep(50)
        }
        error("Enabled clickable dialog action was not exposed: $value")
    }
    private fun selectAll() {
        control("More actions").click()
        text("Select all objects").click()
    }

    @After fun cleanup(): Unit = runBlocking {
        try {
            if (!completed) capture.snapshot("mind-map-branch-removal-before-cleanup")
        } finally {
            try {
                device.setOrientationNatural()
                device.unfreezeRotation()
            } finally {
                try {
                    restoreFontScale()
                    launchTestLibrary(device)
                    owned.forEach { repository.deletePermanently(it) }
                } finally {
                    database.close()
                }
            }
        }
    }
    private fun restoreFontScale() {
        device.executeShellCommand(originalFontScale?.let { "settings put system font_scale $it" } ?: "settings delete system font_scale")
    }

    private suspend fun saveAndRead(board: Board, predicate: (Page) -> Boolean): Page {
        text("Unsaved changes")
        control("Save").click()
        text("Saved on this device")
        return withTimeout(10_000) {
            var saved = repository.load(board.id).activePage
            while (!predicate(saved)) {
                delay(50)
                saved = repository.load(board.id).activePage
            }
            saved
        }
    }

    @Test fun hiddenBranchDeletionAndCutRequireConfirmationAndKeepExactUndoSaveAndReopen(): Unit = runBlocking {
        val root = Note(id = "root", bounds = Bounds(80f, 100f, 300f, 240f), text = "Visible root")
        val child = root.copy(id = "child", bounds = Bounds(450f, 100f, 670f, 240f), text = "Hidden editable child")
        val edge = Connector("edge", ConnectorEnd.Bound(root.id, ConnectionPort.RIGHT), ConnectorEnd.Bound(child.id, ConnectionPort.LEFT))
        val tree = MindMapTree("map", listOf(MindMapNode(root.id, collapsed = true), MindMapNode(child.id, root.id, edge.id)))
        val source = Page(elements = listOf(root, edge, child), mindMaps = listOf(tree))
        val board = Board(name = "Branch confirmation ${newId()}", pages = listOf(source))
        owned += board.id
        repository.save(board)
        launchTestLibrary(device)
        openTestBoard(device, board.name)
        selectAll()
        control("Delete").click()
        button("Delete branch")
        assertNotNull(device.wait(Until.findObject(By.textContains("Nodes: 2. Objects: 3.")), 10_000))
        assertEquals(source, repository.load(board.id).activePage)
        button("Cancel").click()
        text("Saved on this device")
        assertEquals(source, repository.load(board.id).activePage)

        control("Delete").click()
        button("Delete branch")
        device.executeShellCommand("settings put system font_scale 2.0")
        device.setOrientationLeft()
        device.waitForIdle()
        withTimeout(10_000) {
            var scale = 0f
            while (scale != 2f) {
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    scale = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                        .filterIsInstance<MainActivity>().firstOrNull()?.resources?.configuration?.fontScale ?: 0f
                }
                if (scale != 2f) delay(50)
            }
        }
        button("Delete branch").click()
        saveAndRead(board) { it.elements.isEmpty() && it.mindMaps.isEmpty() }
        control("Undo").click()
        val restored = saveAndRead(board) { it.elements == source.elements && it.mindMaps == source.mindMaps }
        assertEquals(listOf(root), restored.visibleElements)
        restoreFontScale()
        device.setOrientationNatural()
        device.waitForIdle()

        selectAll()
        control("Selection actions").click()
        clickTestDialogText(device, context.getString(R.string.cut))
        button("Cut branch")
        assertEquals(source.elements, repository.load(board.id).activePage.elements)
        button("Cancel").click()
        text("Saved on this device")
        control("Selection actions").click()
        clickTestDialogText(device, context.getString(R.string.cut))
        button("Cut branch").click()
        text("Unsaved changes")
        control("More actions").click()
        clickTestDialogText(device, context.getString(R.string.paste))
        val pasted = saveAndRead(board) { it.elements.size == 3 && it.mindMaps.size == 1 && it.mindMaps.single().id != tree.id }
        assertEquals(1, pasted.visibleElements.size)
        assertEquals(listOf(root.text, child.text), pasted.elements.filterIsInstance<Note>().map { it.text })
        assertTrue(pasted.elements.none { it.id in source.elements.map { old -> old.id } })
        control("Undo").click()
        saveAndRead(board) { it.elements.isEmpty() && it.mindMaps.isEmpty() }
        control("Undo").click()
        saveAndRead(board) { it.elements == source.elements && it.mindMaps == source.mindMaps }
        control("Redo").click()
        saveAndRead(board) { it.elements.isEmpty() && it.mindMaps.isEmpty() }
        control("Redo").click()
        saveAndRead(board) { it.elements == pasted.elements && it.mindMaps == pasted.mindMaps }
        control("Back to boards").click()
        openTestBoard(device, board.name)
        text("Saved on this device")
        assertEquals(pasted.elements, repository.load(board.id).activePage.elements)
        assertEquals(pasted.mindMaps, repository.load(board.id).activePage.mindMaps)
        completed = true
    }
}
