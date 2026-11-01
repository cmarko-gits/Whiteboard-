package dev.whiteboard.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import dev.whiteboard.canvas.PageExporter
import dev.whiteboard.core.model.*
import dev.whiteboard.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PartialEraserFlowTest {
    @get:Rule val capture = CaptureUiFailure()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val database = Room.databaseBuilder(context, WhiteboardDatabase::class.java, "whiteboard.db")
        .addMigrations(DatabaseMigrations.FROM_1_TO_2, DatabaseMigrations.FROM_2_TO_3, DatabaseMigrations.FROM_3_TO_4).build()
    private val repository = LocalBoardRepository(database)
    private val preferences = AppSettings(context)
    private val originalSettings = runBlocking { preferences.values.first() }
    private val owned = mutableListOf<String>()
    private var completed = false
    private fun text(value: String) = checkNotNull(device.wait(Until.findObject(By.text(value)), 10_000)) { value }
    private fun control(value: String) = checkNotNull(device.wait(Until.findObject(By.desc(value).enabled(true)), 10_000)) { value }
    private fun home() = launchTestLibrary(device)
    private fun scrolledText(value: String): UiObject2 {
        repeat(6) {
            device.findObject(By.text(value))?.let { return it }
            device.findObjects(By.scrollable(true)).lastOrNull()?.scroll(Direction.DOWN, 0.8f)
            device.waitForIdle()
        }
        return text(value)
    }
    private fun save() {
        control("Save").click()
        text("Saved on this device")
        control("Back to boards")
        device.waitForIdle()
    }

    private fun choosePartialMode() {
        text("Appearance")
        device.waitForIdle()
        // Scroll through connected nodes: old framework caches may retain pre-scroll touch coordinates.
        repeat(6) {
            val queue = ArrayDeque<android.view.accessibility.AccessibilityNodeInfo>()
            InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow?.let(queue::add)
            var scroll: android.view.accessibility.AccessibilityNodeInfo? = null
            var remaining = 1000
            while (queue.isNotEmpty() && remaining-- > 0) {
                val node = queue.removeFirst()
                if (!node.refresh()) continue
                if (node.isScrollable && node.isVisibleToUser) scroll = node
                if (node.text?.toString() == "Partial stroke" && node.isVisibleToUser) {
                    var target: android.view.accessibility.AccessibilityNodeInfo? = node
                    while (target != null && !target.isClickable) target = target.parent
                    check(target?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK) == true) {
                        "Partial mode did not expose an actionable accessibility target"
                    }
                    return
                }
                repeat(node.childCount) { node.getChild(it)?.let(queue::add) }
            }
            check(scroll?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) == true) {
                "Partial stroke setting could not be reached by scrolling"
            }
            device.waitForIdle()
        }
        error("Missing connected Partial stroke setting")
    }

    @After fun cleanup(): Unit = runBlocking {
        // TestWatcher executes after @After, so preserve the original editor/dialog first.
        if (!completed) capture.snapshot("partial-before-cleanup")
        home()
        preferences.eraserMode(originalSettings.eraserMode)
        preferences.lastBrush(originalSettings.lastTool, originalSettings.lastInk)
        owned.forEach { repository.deletePermanently(it) }
        database.close()
    }

    @Test fun settingsGestureUndoRedoSaveAndPngExportKeepTheCutAndTheOtherGroupChild(): Unit = runBlocking {
        preferences.eraserMode(EraserMode.STROKE)
        val stroke = Stroke(points = listOf(Point(100f, 200f), Point(600f, 200f)), style = InkStyle(width = 6f))
        val note = Note(bounds = Bounds(100f, 400f, 300f, 550f), text = "Keep this note")
        val group = ElementGroup(children = listOf(stroke, note))
        val board = Board(name = "Partial ${newId()}", pages = listOf(Page(background = Background.PLAIN, elements = listOf(group))))
        owned += board.id
        repository.save(board)
        home()
        openTestBoard(device, board.name)
        control("More actions").click()
        scrolledText("Settings").click()
        val modeStart = android.os.SystemClock.uptimeMillis()
        choosePartialMode()
        val until = android.os.SystemClock.uptimeMillis() + 10_000
        var selected = false
        do {
            selected = device.findObjects(By.checkable(true).checked(true))
                .any { it.findObject(By.text("Partial stroke")) != null }
            if (selected) break
            device.waitForIdle(100)
        } while (android.os.SystemClock.uptimeMillis() < until)
        assertTrue("Partial mode did not become selected in Settings", selected)
        val persisted = withTimeout(10_000) { preferences.values.first() }
        assertEquals("Selected eraser mode must be persisted", EraserMode.PARTIAL, persisted.eraserMode)
        android.util.Log.i("WhiteboardQA", "Partial mode persisted after ${android.os.SystemClock.uptimeMillis() - modeStart} ms")
        text("Done").click()
        device.waitForIdle()
        repeat(4) {
            if (!device.hasObject(By.desc("Eraser"))) {
                device.findObjects(By.scrollable(true)).firstOrNull()?.scroll(Direction.RIGHT, 0.8f)
                device.waitForIdle()
            }
        }
        control("Eraser").click()
        val canvas = checkNotNull(device.wait(Until.findObject(By.descContains("Drawing canvas")), 10_000)).visibleBounds
        device.swipe(canvas.left + 350, canvas.top + 100, canvas.left + 350, canvas.top + 300, 12)
        text("Unsaved changes")
        save()
        val split = repository.load(board.id).activePage
        val splitGroup = split.elements.single() as ElementGroup
        assertEquals(group.id, splitGroup.id)
        assertEquals(note, splitGroup.children.last())
        assertEquals(2, splitGroup.children.filterIsInstance<Stroke>().size)
        val bitmap = PageExporter().bitmap(split)
        try {
            val bounds = checkNotNull(split.contentBounds).expand(32f)
            val scale = bitmap.width / bounds.width
            assertEquals(
                split.backgroundColor,
                bitmap.getPixel(((350f - bounds.left) * scale).toInt(), ((200f - bounds.top) * scale).toInt())
            )
        } finally {
            bitmap.recycle()
        }
        control("Undo").click()
        save()
        assertEquals(listOf(group), repository.load(board.id).activePage.elements)
        control("Redo").click()
        save()
        assertEquals(split.elements, repository.load(board.id).activePage.elements)
        control("Back to boards").click()
        control("New board")
        assertEquals(EraserMode.PARTIAL, AppSettings(context).values.first().eraserMode)
        completed = true
    }
}
