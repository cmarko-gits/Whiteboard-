package dev.whiteboard.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceFlowTest {
    @get:Rule val failureCapture = CaptureUiFailure()
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private fun text(value: String): UiObject2 =
        checkNotNull(device.wait(Until.findObject(By.text(value)), 10_000)) { "Missing text: $value" }
    private fun description(value: String): UiObject2 =
        checkNotNull(device.wait(Until.findObject(By.desc(value)), 10_000)) { "Missing control: $value" }

    private fun libraryBoard(name: String): UiObject2 {
        searchTestLibrary(device, name)
        return text(name)
    }

    private fun launchHome() = launchTestLibrary(device)

    @Test fun createDrawSaveAndReopenBoard() {
        launchHome()
        val name = "Flow test ${System.currentTimeMillis()}"
        description("New board").click()
        device.wait(Until.hasObject(By.text("Board name")), 10_000)
        val field = checkNotNull(device.findObjects(By.clazz("android.widget.EditText")).lastOrNull())
        field.text = name
        text("Confirm").click()
        description("Save")
        description("Pen").click()
        device.waitForIdle()
        val canvas = checkNotNull(device.wait(Until.findObject(By.descContains("Drawing canvas")), 10_000))
        val area = canvas.visibleBounds
        device.swipe(
            area.left + area.width() / 4,
            area.top + area.height() / 3,
            area.left + area.width() * 3 / 4,
            area.top + area.height() / 2,
            20
        )
        text("Unsaved changes")
        assertTrue(description("Undo").isEnabled)
        description("Save").click()
        text("Saved on this device")
        device.waitForIdle()
        description("Back to boards").click()
        libraryBoard(name).click()
        description("Save")
        text("Saved on this device")
        assertFalse(device.hasObject(By.text("Make room for an idea.")))
        device.executeShellCommand("screencap -p /sdcard/whiteboard-tested-editor.png")
    }

    @Test fun multiplePagesSurviveSaveAndReopen() {
        launchHome()
        val name = "Pages test ${System.currentTimeMillis()}"
        description("New board").click()
        device.wait(Until.hasObject(By.text("Board name")), 10_000)
        checkNotNull(device.findObjects(By.clazz("android.widget.EditText")).lastOrNull()).text = name
        text("Confirm").click()
        description("More actions").click()
        text("Pages").click()
        text("Add page").click()
        text("Page 2")
        text("Done").click()
        description("Undo").click()
        assertTrue(device.wait(Until.hasObject(By.textContains("1/1")), 5_000))
        description("Redo").click()
        assertTrue(device.wait(Until.hasObject(By.textContains("2/2")), 5_000))
        description("Save").click()
        text("Saved on this device")
        device.waitForIdle()
        description("Back to boards").click()
        libraryBoard(name).click()
        assertTrue(device.wait(Until.hasObject(By.textContains("2/2")), 10_000))
    }
}
