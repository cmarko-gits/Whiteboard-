package dev.whiteboard.app

import android.content.Context
import android.os.Build
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
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
class ColorPickerFlowTest {
    @get:Rule val failureCapture = CaptureUiFailure()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = Room.databaseBuilder(context, WhiteboardDatabase::class.java, "whiteboard.db")
        .addMigrations(DatabaseMigrations.FROM_1_TO_2, DatabaseMigrations.FROM_2_TO_3, DatabaseMigrations.FROM_3_TO_4).build()
    private val repository = LocalBoardRepository(database)
    private val preferences = AppSettings(context)
    private val originalSettings = runBlocking { preferences.values.first() }
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private fun text(value: String): UiObject2 = checkNotNull(device.wait(Until.findObject(By.text(value)), 10_000)) { value }
    private fun control(value: String): UiObject2 = checkNotNull(device.wait(Until.findObject(By.desc(value)), 10_000)) { value }
    private fun savedColor(value: String): UiObject2 {
        repeat(5) {
            refreshPickerAccessibility()
            device.findObject(By.desc(value))?.let { return it }
            if (!scrollPicker(forward = true)) return control(value)
            device.waitForIdle()
        }
        return control(value)
    }
    private fun refreshPickerAccessibility() {
        // Scroll/layout changes can leave stale descendant bounds in older framework caches.
        // Refresh connected nodes through the public API instead of relying on API 34 clearCache().
        val queue = ArrayDeque<android.view.accessibility.AccessibilityNodeInfo>()
        InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow?.let(queue::add)
        var remaining = 1000
        while (queue.isNotEmpty() && remaining-- > 0) {
            val node = queue.removeFirst()
            if (node.refresh()) repeat(node.childCount) { node.getChild(it)?.let(queue::add) }
        }
    }
    private fun scrollPicker(forward: Boolean): Boolean {
        // A coordinate swipe across the color plane edits HSV instead of scrolling its parent.
        val queue = ArrayDeque<android.view.accessibility.AccessibilityNodeInfo>()
        val scrollable = mutableListOf<android.view.accessibility.AccessibilityNodeInfo>()
        InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow?.let(queue::add)
        var remaining = 1000
        while (queue.isNotEmpty() && remaining-- > 0) {
            val node = queue.removeFirst()
            if (!node.refresh()) continue
            if (node.isScrollable) scrollable += node
            repeat(node.childCount) { node.getChild(it)?.let(queue::add) }
        }
        val action = if (forward) {
            android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        } else {
            android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }
        return scrollable.lastOrNull()?.performAction(action) == true
    }
    private fun pickerText(value: String): UiObject2 {
        repeat(5) {
            device.findObject(By.text(value))?.let { return it }
            if (!scrollPicker(forward = true)) return text(value)
            device.waitForIdle()
        }
        return text(value)
    }
    private fun home() = launchTestLibrary(device)

    private fun hex(): UiObject2 {
        refreshPickerAccessibility()
        return checkNotNull(device.wait(Until.findObject(By.clazz("android.widget.EditText")), 10_000)) { "Missing HEX input" }
    }
    private fun openPicker(index: Int = 0) {
        control("Color picker")
        device.findObjects(By.desc("Color picker"))[index].click()
        text("HEX color")
        device.waitForIdle()
    }
    private fun enterHex(value: String) {
        hex().click()
        val input = checkNotNull(
            device.wait(Until.findObject(By.clazz("android.widget.EditText").focused(true)), 5_000)
        ) { "HEX input did not receive focus" }
        input.text = value
        device.waitForIdle()
        // Refresh cached accessibility text after the rendered input changes.
        if (Build.VERSION.SDK_INT >= 34) InstrumentationRegistry.getInstrumentation().uiAutomation.clearCache()
        assertTrue("HEX input did not become $value", device.wait(Until.hasObject(By.clazz("android.widget.EditText").text(value)), 5_000))
    }
    private fun finishHexEntry() {
        if (Regex("\\bmInputShown=true\\b").containsMatchIn(device.executeShellCommand("dumpsys input_method"))) {
            val done = device.findObject(By.desc("Done"))
            if (done != null) done.click() else device.pressBack()
            device.waitForIdle()
            val until = android.os.SystemClock.uptimeMillis() + 5_000
            while (Regex("\\bmInputShown=true\\b").containsMatchIn(device.executeShellCommand("dumpsys input_method")) &&
                android.os.SystemClock.uptimeMillis() < until
            ) {
                android.os.SystemClock.sleep(50)
            }
            check(!Regex("\\bmInputShown=true\\b").containsMatchIn(device.executeShellCommand("dumpsys input_method"))) {
                "HEX keyboard did not close after Done"
            }
        }
    }
    private fun pick(value: String, index: Int = 0) {
        openPicker(index)
        enterHex(value)
        device.waitForIdle()
        text("Apply").click()
        device.waitForIdle()
    }

    private fun draw(offset: Int) {
        val bounds = controlCanvas().visibleBounds
        device.swipe(
            bounds.left + bounds.width() / 4,
            bounds.top + 120 + offset,
            bounds.left + bounds.width() * 3 / 4,
            bounds.top + 200 + offset,
            20
        )
        text("Unsaved changes")
    }

    private fun controlCanvas(): UiObject2 = checkNotNull(device.wait(Until.findObject(By.descContains("Drawing canvas")), 10_000))

    @After fun close(): Unit = runBlocking {
        device.setOrientationNatural()
        device.unfreezeRotation()
        preferences.favoriteColors(originalSettings.favoriteColors)
        preferences.lastBrush(originalSettings.lastTool, originalSettings.lastInk)
        database.close()
    }

    @Test fun exactColorsFavoritesCancelAndRotationSurviveThroughSavedDrawing(): Unit = runBlocking {
        preferences.favoriteColors(emptyList())
        val board = Board(name = "Color picker ${System.currentTimeMillis()}")
        repository.save(board)
        home()
        openTestBoard(device, board.name)
        control("Pen").click()
        openPicker()
        enterHex("12AB34")
        finishHexEntry()
        pickerText("Save color").click()
        pickerText("Remove saved color")
        device.waitForIdle()
        device.executeShellCommand("screencap -p /sdcard/whiteboard-color-picker-portrait.png")
        text("Apply").click()
        draw(0)
        withTimeout(5_000) { preferences.values.first { 0xFF12AB34.toInt() in it.favoriteColors } }

        openPicker()
        savedColor("Color #12AB34")
        enterHex("GGGGGG")
        device.waitForIdle()
        assertEquals("GGGGGG", hex().text)
        pickerText("Enter 6 hexadecimal digits, for example 12AB34.")
        text("Apply").click()
        assertEquals("GGGGGG", hex().text)
        text("Cancel").click()
        openPicker()
        assertEquals("12AB34", hex().text)
        enterHex("ABCDEF")
        finishHexEntry()
        pickerText("Save color").click()
        text("Cancel").click()
        assertEquals(listOf(0xFF12AB34.toInt()), preferences.values.first().favoriteColors)

        openPicker()
        finishHexEntry()
        val hueBounds = control("Hue").visibleBounds
        device.click(hueBounds.left + hueBounds.width() * 2 / 3, hueBounds.centerY())
        val plane = control("Saturation and brightness").visibleBounds
        device.swipe(plane.centerX(), plane.centerY(), plane.left + plane.width() * 3 / 4, plane.top + plane.height() / 4, 12)
        assertNotEquals("12AB34", hex().text)
        enterHex("65B7F2")
        // Commit the accessibility text replacement before starting a new IME session.
        // Rotation is still exercised with the real keyboard visible and the exact value preserved.
        finishHexEntry()
        assertEquals("65B7F2", hex().text)
        hex().click()
        val keyboardDeadline = android.os.SystemClock.uptimeMillis() + 5_000
        while (!Regex("\\bmInputShown=true\\b").containsMatchIn(device.executeShellCommand("dumpsys input_method")) &&
            android.os.SystemClock.uptimeMillis() < keyboardDeadline
        ) {
            android.os.SystemClock.sleep(50)
        }
        check(Regex("\\bmInputShown=true\\b").containsMatchIn(device.executeShellCommand("dumpsys input_method"))) {
            "Rotation must exercise an open HEX keyboard"
        }
        device.setOrientationLeft()
        device.waitForIdle()
        device.executeShellCommand("screencap -p /sdcard/whiteboard-color-picker-landscape.png")
        assertEquals("65B7F2", hex().text)
        device.executeShellCommand("screencap -p /sdcard/whiteboard-color-picker-landscape.png")
        finishHexEntry()
        text("Apply").click()
        device.setOrientationNatural()
        device.unfreezeRotation()
        device.waitForIdle()
        draw(100)
        control("Save").click()
        text("Saved on this device")
        val strokes = repository.load(board.id).activePage.elements.filterIsInstance<Stroke>()
        assertEquals(listOf(0xFF12AB34.toInt(), 0xFF65B7F2.toInt()), strokes.map { it.style.color })
        assertTrue(strokes.all { it.style.opacity == 1f })
        home()
        openTestBoard(device, board.name)
        openPicker()
        savedColor("Color #12AB34")
        text("Cancel").click()
    }

    @Test fun shapeBorderFillAndPageBackgroundUseIndependentCustomColors(): Unit = runBlocking {
        val shape = Shape(
            bounds = Bounds(100f, 100f, 300f, 300f),
            kind = Tool.RECTANGLE,
            style = InkStyle(opacity = 0.6f),
            fill = 0xFFF4E6A3.toInt()
        )
        val board = Board(name = "Shape colors ${System.currentTimeMillis()}", pages = listOf(Page(elements = listOf(shape))))
        repository.save(board)
        home()
        openTestBoard(device, board.name)
        control("More actions").click()
        text("Select all objects").click()
        control("Selection actions").click()
        text("Object properties").click()
        pick("3AA1D8")
        pick("F1AF23", index = 1)
        text("Confirm").click()
        control("More actions").click()
        text("Canvas background").click()
        pick("E8DFF5")
        text("Apply").click()
        control("Save").click()
        text("Saved on this device")
        val page = repository.load(board.id).activePage
        val saved = page.elements.single() as Shape
        assertEquals(0xFF3AA1D8.toInt(), saved.style.color)
        assertEquals(0.6f, saved.style.opacity, 0.001f)
        assertEquals(0xFFF1AF23.toInt(), saved.fill)
        assertEquals(0xFFE8DFF5.toInt(), page.backgroundColor)
    }
}
