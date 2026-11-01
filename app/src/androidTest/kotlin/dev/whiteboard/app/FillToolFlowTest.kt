package dev.whiteboard.app

import android.content.Context
import android.content.Intent
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
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FillToolFlowTest {
    @get:Rule val failureCapture = CaptureUiFailure()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val database = Room.databaseBuilder(context, WhiteboardDatabase::class.java, "whiteboard.db")
        .addMigrations(DatabaseMigrations.FROM_1_TO_2, DatabaseMigrations.FROM_2_TO_3, DatabaseMigrations.FROM_3_TO_4).build()
    private val repository = LocalBoardRepository(database)
    private fun text(value: String): UiObject2 = checkNotNull(device.wait(Until.findObject(By.text(value)), 10_000)) { value }
    private fun control(value: String): UiObject2 = checkNotNull(device.wait(Until.findObject(By.desc(value)), 10_000)) { value }
    private fun canvas(): UiObject2 = checkNotNull(device.wait(Until.findObject(By.descContains("Drawing canvas")), 10_000))
    private fun color(value: String) {
        control("Color picker").click()
        text("HEX color")
        device.waitForIdle()
        checkNotNull(device.findObjects(By.clazz("android.widget.EditText")).lastOrNull()).text = value
        assertTrue(device.wait(Until.hasObject(By.clazz("android.widget.EditText").text(value)), 5_000))
        text("Apply").click()
        device.waitForIdle()
    }
    private fun tap(x: Int, y: Int) {
        val bounds = canvas().visibleBounds
        device.click(bounds.left + x, bounds.top + y)
        device.waitForIdle()
    }
    private fun save() {
        control("Save").click()
        text("Saved on this device")
    }

    @After fun close() = database.close()

    @Test fun bucketFillsGroupedShapeAndBackgroundWithUndoSaveAndDragCancellation(): Unit = runBlocking {
        val shape = Shape(bounds = Bounds(80f, 80f, 320f, 320f), kind = Tool.RECTANGLE, style = InkStyle(opacity = 0.6f))
        val note = Note(bounds = Bounds(400f, 80f, 640f, 320f), text = "Keep text")
        val group = ElementGroup(children = listOf(shape, note))
        val board = Board(name = "Bucket ${System.currentTimeMillis()}", pages = listOf(Page(elements = listOf(group))))
        repository.save(board)
        context.startActivity(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        device.waitForIdle()
        device.findObject(By.text("OK"))?.click()
        if (!device.hasObject(By.desc("New board"))) device.wait(Until.findObject(By.text("Start creating")), 10_000)?.click()
        control("New board")
        text(board.name).click()
        control("Fill color").click()
        text("1:1").click()
        device.waitForIdle()
        color("CC4466")
        tap(200, 200)
        text("Unsaved changes")
        control("Undo").click()
        text("Saved on this device")
        control("Redo").click()
        save()
        var page = repository.load(board.id).activePage
        var children = (page.elements.single() as ElementGroup).children
        assertEquals(shape.copy(fill = 0xFFCC4466.toInt()), children.first())
        assertEquals(note, children.last())
        assertEquals(board.activePage.backgroundColor, page.backgroundColor)

        color("D0E8FF")
        tap(520, 200)
        save()
        color("F4E6A3")
        tap(24, canvas().visibleBounds.height() / 2)
        control("Undo").click()
        save()
        assertEquals(board.activePage.backgroundColor, repository.load(board.id).activePage.backgroundColor)
        control("Redo").click()
        save()
        page = repository.load(board.id).activePage
        children = (page.elements.single() as ElementGroup).children
        assertEquals(note.copy(color = 0xFFD0E8FF.toInt()), children.last())
        assertEquals(0xFFF4E6A3.toInt(), page.backgroundColor)

        color("000000")
        val bounds = canvas().visibleBounds
        device.swipe(bounds.centerX(), bounds.centerY(), bounds.centerX() + bounds.width() / 4, bounds.centerY(), 20)
        device.waitForIdle()
        text("Saved on this device")
        assertEquals(page, repository.load(board.id).activePage)
        device.executeShellCommand("screencap -p /sdcard/whiteboard-fill-tool.png")
        control("Back to boards").click()
        text(board.name).click()
        control("Fill color")
        assertEquals(page, repository.load(board.id).activePage)
    }
}
