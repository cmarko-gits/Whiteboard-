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
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RotationFlowTest {
    @get:Rule val capture = CaptureUiFailure()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val database = Room.databaseBuilder(context, WhiteboardDatabase::class.java, "whiteboard.db")
        .addMigrations(DatabaseMigrations.FROM_1_TO_2, DatabaseMigrations.FROM_2_TO_3, DatabaseMigrations.FROM_3_TO_4).build()
    private val repository = LocalBoardRepository(database)
    private val owned = mutableListOf<String>()
    private var completed = false
    private fun text(value: String) = checkNotNull(device.wait(Until.findObject(By.text(value)), 10_000)) { value }
    private fun control(value: String) = checkNotNull(device.wait(Until.findObject(By.desc(value).enabled(true)), 10_000)) { value }
    private fun save() {
        control("Save").click()
        text("Saved on this device")
        device.waitForIdle()
    }

    @After fun cleanup(): Unit = runBlocking {
        if (!completed) capture.snapshot("rotation-before-cleanup")
        launchTestLibrary(device)
        owned.forEach { repository.deletePermanently(it) }
        database.close()
    }

    @Test fun cancellingThenRotatingAGroupPreservesItsContentThroughUndoSaveReopenAndExport(): Unit = runBlocking {
        val shape = Shape(bounds = Bounds(100f, 150f, 300f, 250f), kind = Tool.RECTANGLE, style = InkStyle(), fill = 0xFF12AB34.toInt())
        val note = Note(bounds = Bounds(100f, 350f, 300f, 500f), text = "Keep rotated text")
        val group = ElementGroup(children = listOf(shape, note))
        val board = Board(name = "Rotate ${newId()}", pages = listOf(Page(elements = listOf(group))))
        owned += board.id
        repository.save(board)
        launchTestLibrary(device)
        openTestBoard(device, board.name)
        control("More actions").click()
        text("Select all objects").click()
        control("Rotate selection").click()
        text("Angle in degrees")
        text("Cancel").click()
        text("Saved on this device")
        assertEquals(listOf(group), repository.load(board.id).activePage.elements)

        control("Rotate selection").click()
        checkNotNull(device.wait(Until.findObject(By.clazz("android.widget.EditText")), 5_000)).text = "45"
        assertTrue(device.wait(Until.hasObject(By.clazz("android.widget.EditText").text("45")), 5_000))
        text("Apply").click()
        assertTrue(device.wait(Until.gone(By.text("Angle in degrees")), 10_000))
        awaitTestKeyboardHidden(device)
        text("Unsaved changes")
        save()
        val rotated = repository.load(board.id).activePage
        val savedGroup = rotated.elements.single() as ElementGroup
        assertEquals(group.id, savedGroup.id)
        assertEquals(group.children.map(Element::id), savedGroup.children.map(Element::id))
        assertTrue(savedGroup.children.all { it is AffineElement })
        assertEquals(288.388f, savedGroup.children.first().bounds.center.x, 0.1f)
        assertEquals(236.612f, savedGroup.children.first().bounds.center.y, 0.1f)
        assertEquals(129.289f, savedGroup.children.last().bounds.center.x, 0.1f)
        assertEquals(395.711f, savedGroup.children.last().bounds.center.y, 0.1f)
        assertEquals(note.text, (savedGroup.children.last().content() as Note).text)
        val image = PageExporter().bitmap(rotated)
        try {
            assertTrue(image.width > 0 && image.height > 0)
        } finally {
            image.recycle()
        }
        control("Undo").click()
        save()
        assertEquals(listOf(group), repository.load(board.id).activePage.elements)
        control("Redo").click()
        save()
        assertEquals(rotated.elements, repository.load(board.id).activePage.elements)
        control("Back to boards").click()
        openTestBoard(device, board.name)
        control("More actions")
        assertEquals(rotated.elements, repository.load(board.id).activePage.elements)
        completed = true
    }
}
