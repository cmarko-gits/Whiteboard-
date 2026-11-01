package dev.whiteboard.app

import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import dev.whiteboard.canvas.R as CanvasResources
import dev.whiteboard.canvas.WhiteboardView
import dev.whiteboard.core.model.*
import dev.whiteboard.data.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Uses connected framework nodes, rather than assuming the helper's virtual IDs. */
@RunWith(AndroidJUnit4::class)
class CanvasAccessibilityTest {
    @get:Rule val capture = CaptureUiFailure()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrument = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrument)
    private val database = Room.databaseBuilder(context, WhiteboardDatabase::class.java, "whiteboard.db")
        .addMigrations(DatabaseMigrations.FROM_1_TO_2, DatabaseMigrations.FROM_2_TO_3, DatabaseMigrations.FROM_3_TO_4).build()
    private val repository = LocalBoardRepository(database)
    private val owned = mutableListOf<String>()

    @Test fun keyboardFocusRemainsVisibleOnLightDarkAndGreenPages() {
        val note = Note(bounds = Bounds(100f, 100f, 300f, 260f), text = "Keyboard contrast", color = 0xFF315D4C.toInt())
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var view: WhiteboardView? = null
            scenario.onActivity { activity ->
                view = WhiteboardView(activity)
                activity.setContentView(view)
            }
            listOf(0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFF315D4C.toInt()).forEach { background ->
                scenario.onActivity {
                    view!!.update(Page(backgroundColor = background, elements = listOf(note)), Tool.SELECT, InkStyle(), emptySet())
                    view!!.requestFocus()
                }
                instrument.waitForIdleSync()
                val target = node("Note: Keyboard contrast")
                // Older framework versions may focus the first virtual child with the host.
                if (!target.isFocused) assertTrue(target.performAction(AccessibilityNodeInfo.ACTION_FOCUS))
                instrument.waitForIdleSync()
                assertTrue(target.refresh())
                assertTrue(target.isFocused)
                val focusBounds = android.graphics.Rect().also(target::getBoundsInParent)
                scenario.onActivity {
                    val canvasView = checkNotNull(view)
                    assertFalse(canvasView.defaultFocusHighlightEnabled)
                    val bitmap = android.graphics.Bitmap.createBitmap(
                        canvasView.width,
                        canvasView.height,
                        android.graphics.Bitmap.Config.ARGB_8888
                    )
                    try {
                        canvasView.draw(android.graphics.Canvas(bitmap))
                        val radius = (4 * canvasView.resources.displayMetrics.density).toInt() + 1
                        var black = 0
                        var white = 0
                        for (y in focusBounds.top - radius..focusBounds.top + radius) {
                            for (x in focusBounds.left + focusBounds.width() / 4 until focusBounds.right - focusBounds.width() / 4) {
                                when (bitmap.getPixel(x, y)) {
                                    android.graphics.Color.BLACK -> black++
                                    android.graphics.Color.WHITE -> white++
                                }
                            }
                        }
                        if (black <= 100 || white <= 100) {
                            val directory =
                                InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let { java.io.File(it) }
                                    ?: context.getExternalFilesDir(null)
                            directory?.mkdirs()
                            directory?.let {
                                java.io.File(it, "focus-${background.toUInt()}.png").outputStream().use { output ->
                                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
                                }
                            }
                        }
                        assertTrue("Missing dark focus border", black > 100)
                        assertTrue("Missing light focus border", white > 100)
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
        }
    }

    @Test fun oldVirtualNodesCannotActOnANewPageWithTheSameImportedObjectId() {
        val first = Note(bounds = Bounds(100f, 100f, 300f, 260f), text = "First page object")
        val second = first.copy(text = "Second page object")
        val events = mutableListOf<dev.whiteboard.canvas.CanvasEvent>()
        var view: WhiteboardView? = null
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                view = WhiteboardView(activity).also {
                    it.update(Page(elements = listOf(first)), Tool.SELECT, InkStyle(), emptySet())
                    it.onEvent = events::add
                    activity.setContentView(it)
                }
            }
            instrument.waitForIdleSync()
            val stale = node("Note: First page object")
            scenario.onActivity { view!!.update(Page(elements = listOf(second)), Tool.SELECT, InkStyle(), emptySet()) }
            node("Note: Second page object")
            assertFalse(stale.performAction(CanvasResources.id.canvas_delete))
            instrument.runOnMainSync { assertTrue(events.isEmpty()) }
        }
    }

    @After fun cleanup() = runBlocking {
        owned.forEach { repository.deletePermanently(it) }
        database.close()
    }

    private fun control(label: String) = checkNotNull(device.wait(Until.findObject(By.desc(label)), 10_000)) { label }
    private fun text(label: String) = checkNotNull(device.wait(Until.findObject(By.text(label)), 10_000)) { label }
    private suspend fun open(elements: List<Element>): Board {
        val board = Board(name = "Accessibility ${newId()}", pages = listOf(Page(elements = elements)))
        owned.add(board.id)
        repository.save(board)
        context.startActivity(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        // A fresh installation can render onboarding after the first idle event.
        // Wait for either onboarding or the library, rather than taking a one-time snapshot.
        val deadline = android.os.SystemClock.uptimeMillis() + 10_000
        while (!device.hasObject(By.desc("New board")) && android.os.SystemClock.uptimeMillis() < deadline) {
            device.findObject(By.text("Start creating"))?.click()
            device.waitForIdle(250)
        }
        control("New board")
        checkNotNull(device.findObject(By.clazz("android.widget.EditText"))).text = board.name.substringAfter("Accessibility ")
        control("Search results")
        device.waitForIdle()
        text(board.name).click()
        control("More actions")
        device.waitForIdle()
        return board
    }

    private fun node(description: String): AccessibilityNodeInfo {
        val until = android.os.SystemClock.uptimeMillis() + 10_000
        do {
            val queue = ArrayDeque<AccessibilityNodeInfo>()
            instrument.uiAutomation.rootInActiveWindow?.let(queue::add)
            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                if (current.contentDescription?.toString()?.startsWith(description) == true) return current
                repeat(current.childCount) { current.getChild(it)?.let(queue::add) }
            }
            android.os.SystemClock.sleep(50)
        } while (android.os.SystemClock.uptimeMillis() < until)
        error("Missing accessibility node: $description")
    }

    @Test fun spokenObjectActionsSelectMoveEditDeleteUndoAndPersist(): Unit = runBlocking {
        val note = Note(bounds = Bounds(100f, 100f, 300f, 260f), text = "Accessible note")
        val board = open(listOf(note))
        assertTrue(node("Note: Accessible note").performAction(AccessibilityNodeInfo.ACTION_CLICK))
        text("1 selected")
        assertTrue(node("Note: Accessible note").performAction(CanvasResources.id.canvas_move_right))
        device.waitForIdle()
        assertTrue(node("Note: Accessible note").performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK))
        checkNotNull(device.wait(Until.findObject(By.clazz("android.widget.EditText")), 5_000)).text = "Edited by accessible action"
        text("Apply").click()
        control("Save").click()
        text("Saved on this device")
        val saved = repository.load(board.id).activePage.elements.single() as Note
        assertEquals("Edited by accessible action", saved.text)
        assertTrue(saved.bounds.left > note.bounds.left)
        val stale = node("Note: Edited by accessible action")
        assertTrue(stale.performAction(CanvasResources.id.canvas_delete))
        device.waitForIdle()
        assertFalse(stale.performAction(CanvasResources.id.canvas_delete))
        control("Undo").click()
        node("Note: Edited by accessible action")
    }

    @Test fun completeObjectNavigatorFindsOffscreenObjectsBeyondTheVisibleNodeWindow(): Unit = runBlocking {
        val items = List(300) { index -> Note(bounds = Bounds(100f, 100f, 220f, 240f), text = "Overlapping $index") } +
            Note(bounds = Bounds(50_000f, 60_000f, 50_200f, 60_200f), text = "Faraway target")
        open(items)
        assertTrue(node("Drawing canvas").childCount <= 512)
        control("More actions").click()
        text("Objects").click()
        val search = checkNotNull(device.wait(Until.findObject(By.clazz("android.widget.EditText")), 5_000))
        search.text = "Faraway target"
        text("Note: Faraway target").click()
        node("Note: Faraway target")
        text("1 selected")
    }

    @Test fun hostActionsAddNotesAndMoveTheCameraWithoutDrawingGestures(): Unit = runBlocking {
        val board = open(emptyList())
        assertTrue(node("Drawing canvas").performAction(CanvasResources.id.canvas_add_note))
        checkNotNull(device.wait(Until.findObject(By.clazz("android.widget.EditText")), 5_000)).text = "Note without a drawing gesture"
        text("Apply").click()
        control("Save").click()
        text("Saved on this device")
        assertEquals(1, repository.load(board.id).activePage.elements.size)
        assertTrue(node("Drawing canvas").performAction(CanvasResources.id.canvas_zoom_in))
        assertTrue(node("Drawing canvas").performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))
        assertTrue(node("Drawing canvas").performAction(CanvasResources.id.canvas_fit))
        text("Saved on this device")
    }
}
