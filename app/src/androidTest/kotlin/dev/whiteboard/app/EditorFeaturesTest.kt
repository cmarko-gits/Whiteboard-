package dev.whiteboard.app

import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import dev.whiteboard.core.model.*
import dev.whiteboard.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditorFeaturesTest {
    @get:Rule val failureCapture = CaptureUiFailure()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = Room.databaseBuilder(context, WhiteboardDatabase::class.java, "whiteboard.db")
        .addMigrations(DatabaseMigrations.FROM_1_TO_2, DatabaseMigrations.FROM_2_TO_3, DatabaseMigrations.FROM_3_TO_4).build()
    private val repository = LocalBoardRepository(database)
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private fun text(value: String): UiObject2 = checkNotNull(device.wait(Until.findObject(By.text(value)), 10_000)) { value }
    private fun control(value: String): UiObject2 = checkNotNull(device.wait(Until.findObject(By.desc(value)), 10_000)) { value }
    private fun home() {
        context.startActivity(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        device.waitForIdle()
        device.findObject(By.text("OK"))?.click()
        if (!device.hasObject(By.desc("New board"))) {
            device.wait(Until.findObject(By.text("Start creating")), 10_000)?.click()
        }
        control("New board")
    }

    @After fun close() = database.close()

    @Test fun openSourceNoticesAreAvailableLocally() {
        home()
        control("Settings").click()
        val scroll = checkNotNull(device.wait(Until.findObject(By.scrollable(true)), 5_000))
        repeat(3) { if (!device.hasObject(By.text("Open source licenses"))) scroll.scroll(Direction.DOWN, 1f) }
        text("Open source licenses").click()
        assertTrue(device.wait(Until.hasObject(By.textContains("Whiteboard — open source notices")), 5_000))
        text("Done").click()
    }

    @Test fun rotationKeepsUnsavedObjectsAndZoom(): Unit = runBlocking {
        val board = Board(name = "Rotation ${System.currentTimeMillis()}")
        repository.save(board)
        home()
        text(board.name).click()
        control("Pen").click()
        device.waitForIdle()
        val canvas = checkNotNull(device.wait(Until.findObject(By.descContains("Drawing canvas")), 10_000)).visibleBounds
        device.swipe(canvas.left + canvas.width() / 3, canvas.top + 120, canvas.left + canvas.width() * 2 / 3, canvas.top + 300, 20)
        text("Unsaved changes")
        val original = withTimeout(10_000) {
            var draft = repository.draft(board.id)
            while (draft == null) {
                delay(100)
                draft = repository.draft(board.id)
            }
            draft
        }
        control("Zoom in").click()
        device.waitForIdle()
        try {
            device.setOrientationLeft()
            device.waitForIdle()
            text("Unsaved changes")
            assertTrue(control("Undo").isEnabled)
            control("Save").click()
            text("Saved on this device")
            val saved = repository.load(board.id)
            assertEquals(original.activePage.elements, saved.activePage.elements)
            assertEquals(1.25f, saved.activePage.viewport.scale, 0.001f)
            device.executeShellCommand("screencap -p /sdcard/whiteboard-landscape-editor.png")
        } finally {
            device.setOrientationNatural()
            device.unfreezeRotation()
        }
    }

    @Test fun rectangleSelectionCopiesBothObjectsToAnotherPage(): Unit = runBlocking {
        val original = Board(
            name = "Selection ${System.currentTimeMillis()}",
            pages = listOf(
                Page(
                    elements = listOf(
                        Shape(bounds = Bounds(100f, 100f, 300f, 300f), kind = Tool.RECTANGLE, style = InkStyle()),
                        Shape(bounds = Bounds(400f, 100f, 600f, 300f), kind = Tool.ELLIPSE, style = InkStyle())
                    )
                )
            )
        )
        repository.save(original)
        home()
        text(original.name).click()
        control("Select").click()
        device.waitForIdle()
        val bounds = checkNotNull(device.wait(Until.findObject(By.descContains("Drawing canvas")), 10_000)).visibleBounds
        device.swipe(bounds.left + 80, bounds.top + 30, bounds.left + 700, bounds.top + 400, 25)
        device.waitForIdle()
        device.executeShellCommand("screencap -p /sdcard/whiteboard-selection.png")
        text("2 selected")
        control("Selection actions").click()
        text("Group objects").click()
        text("1 selected")
        control("Selection actions").click()
        text("Ungroup objects").click()
        text("2 selected")
        control("Selection actions").click()
        text("Copy within Whiteboard").click()
        control("More actions").click()
        text("Pages").click()
        text("Add page").click()
        text("Done").click()
        control("More actions").click()
        text("Paste objects").click()
        text("2 selected")
        control("Undo").click()
        assertTrue(device.wait(Until.gone(By.text("2 selected")), 5_000))
        control("Redo").click()
        device.waitForIdle()
        control("Save").click()
        text("Saved on this device")
        val saved = repository.load(original.id)
        assertEquals(2, saved.pages.size)
        assertEquals(2, saved.activePage.elements.size)
        assertEquals(2, saved.pages.first().elements.size)
        assertTrue(saved.activePage.elements.none { copy -> original.activePage.elements.any { it.id == copy.id } })
    }

    @Test fun textSearchOpensMatchingPageAndRetainsLibraryQuery(): Unit = runBlocking {
        val needle = "Needle${System.currentTimeMillis()}"
        val first = Page()
        val second = Page(name = "Search result", elements = listOf(Note(bounds = Bounds(800f, 900f, 1000f, 1100f), text = needle)))
        val board = Board(name = "Search ${System.currentTimeMillis()}", pages = listOf(first, second))
        repository.save(board)
        home()
        checkNotNull(device.findObject(By.clazz("android.widget.EditText"))).text = needle
        device.waitForIdle()
        control("Search results")
        text(board.name).click()
        device.waitForIdle()
        device.executeShellCommand("screencap -p /sdcard/whiteboard-search.png")
        text("1 selected")
        assertTrue(device.wait(Until.hasObject(By.textContains("2/2")), 5_000))
        control("Back to boards").click()
        assertEquals(needle, checkNotNull(device.wait(Until.findObject(By.clazz("android.widget.EditText")), 10_000)).text)
        text(board.name)
        Unit
    }

    @Test fun folderAndGridTemplateCanBeCreatedThroughInterface(): Unit = runBlocking {
        val prefix = "000 Folder ${System.currentTimeMillis()}"
        val folder = "$prefix Z"
        val name = "Template ${System.currentTimeMillis()}"
        val folders = LocalFolderRepository(database)
        try {
            repeat(24) { folders.create("$prefix $it", null) }
            home()
            text("Folders").click()
            // The primary action must stay visible even when the folder list overflows.
            text("New folder").click()
            checkNotNull(device.findObjects(By.clazz("android.widget.EditText")).lastOrNull()).text = folder
            text("Confirm").click()
            val until = android.os.SystemClock.uptimeMillis() + 10_000
            while (!device.hasObject(By.text(folder)) && android.os.SystemClock.uptimeMillis() < until) {
                // The folder list is populated asynchronously after the dialog closes.
                val scroll = device.wait(Until.findObject(By.scrollable(true)), 1_000)
                if (scroll != null) scroll.scroll(Direction.DOWN, 0.8f)
            }
            text(folder).click()
            control("New board").click()
            checkNotNull(device.findObjects(By.clazz("android.widget.EditText")).lastOrNull()).text = name
            text("Grid").click()
            text("Confirm").click()
            control("Save").click()
            text("Saved on this device")
            control("Back to boards").click()
            text(folder)
            text(name)
            val board = repository.savedBoards().single { it.name == name }
            assertNotNull(board.folderId)
            assertEquals(Background.GRID, board.activePage.background)
        } finally {
            repository.savedBoards().filter { it.name == name }.forEach { repository.deletePermanently(it.id) }
            folders.observeFolders().first().filter { it.name.startsWith(prefix) }.forEach { folders.deleteKeepingContents(it.id) }
        }
    }
}
