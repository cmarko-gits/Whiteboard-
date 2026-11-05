package dev.whiteboard.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import dev.whiteboard.core.model.*
import dev.whiteboard.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConnectionFlowTest {
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
    private fun description(prefix: String) =
        checkNotNull(device.wait(Until.findObject(By.descStartsWith(prefix).enabled(true)), 10_000)) { prefix }

    @After fun cleanup(): Unit = runBlocking {
        if (!completed) capture.snapshot("connection-flow-before-cleanup")
        launchTestLibrary(device)
        owned.forEach { repository.deletePermanently(it) }
        database.close()
    }

    private suspend fun open(): Board {
        val first = Shape(bounds = Bounds(80f, 100f, 180f, 200f), kind = Tool.RECTANGLE, style = InkStyle())
        val second = Shape(bounds = Bounds(320f, 220f, 420f, 320f), kind = Tool.ELLIPSE, style = InkStyle())
        val board = Board(name = "Connection ${newId()}", pages = listOf(Page(elements = listOf(first, second))))
        owned += board.id
        repository.save(board)
        launchTestLibrary(device)
        openTestBoard(device, board.name)
        control("More actions").click()
        text("Connect objects").click()
        text("Attachment points follow the object when it moves or rotates.")
        return board
    }

    private suspend fun save(board: Board, matches: (Page) -> Boolean): Page {
        text("Unsaved changes")
        control("Save").click()
        text("Saved on this device")
        return withTimeout(10_000) {
            var page = repository.load(board.id).activePage
            while (!matches(page)) {
                delay(50)
                page = repository.load(board.id).activePage
            }
            page
        }
    }

    @Test fun createEditDetachUndoRedoAndReopenPreserveConnectionIdentity(): Unit = runBlocking {
        val board = open()
        capture.snapshot("connection-editor")
        clickTestDialogText(device, "Apply")
        val created = save(board) { it.elements.size == 3 }
        val edge = created.elements.filterIsInstance<Connector>().single()
        assertEquals(ConnectorEnd.Bound(board.activePage.elements[0].id, ConnectionPort.RIGHT), edge.start)
        assertEquals(ConnectorEnd.Bound(board.activePage.elements[1].id, ConnectionPort.LEFT), edge.end)
        assertTrue(edge.arrow)

        control("Selection actions").click()
        text("Edit connection").click()
        revealTestDialogText(device, "Attachment point: Left side")
        description("End: Attachment point:").click()
        text("Top side").click()
        clickTestDialogText(device, "Apply")
        val edited = edge.copy(end = ConnectorEnd.Bound(board.activePage.elements[1].id, ConnectionPort.TOP))
        val editedPage = save(board) { it.elements.last() == edited }
        control("Undo").click()
        save(board) { it.elements.last() == edge }
        control("Redo").click()
        save(board) { it.elements.last() == edited }

        control("Selection actions").click()
        text("Detach connection").click()
        val detached = save(board) { page ->
            val value = page.elements.last() as? Connector
            value?.start is ConnectorEnd.Free && value.end is ConnectorEnd.Free
        }
        val free = detached.elements.last() as Connector
        assertEquals(edge.id, free.id)
        assertEquals(editedPage.geometry.resolve(edited), detached.geometry.resolve(free))
        control("Undo").click()
        save(board) { it.elements.last() == edited }
        control("Back to boards").click()
        openTestBoard(device, board.name)
        control("More actions")
        assertEquals(edited, repository.load(board.id).activePage.elements.last())
        completed = true
    }

    @Test fun targetPickerRejectsSelfConnectionAndCancelLeavesNoHistoryChange(): Unit = runBlocking {
        val board = open()
        description("End: 2.").click()
        text("Find an object")
        val input = checkNotNull(device.wait(Until.findObject(By.clazz("android.widget.EditText")), 5_000))
        input.text = "Rectangle"
        assertTrue(device.wait(Until.gone(By.text("2. Ellipse")), 10_000))
        clickTestDialogText(device, "1. Rectangle")
        revealTestDialogText(device, "Choose valid, different objects or free positions for the two ends.")
        assertFalse("Self connections must not be confirmed", testDialogActionEnabled("Apply"))
        clickTestDialogText(device, "Cancel")
        text("Saved on this device")
        assertEquals(board.activePage.elements, repository.load(board.id).activePage.elements)
        assertFalse(testDialogActionEnabled("Undo", byDescription = true))
        completed = true
    }

    @Test fun freeEndPreservesTheSelectedPortPositionAndCanBeSaved(): Unit = runBlocking {
        val board = open()
        description("End: 2.").click()
        clickTestDialogText(device, "Free end")
        revealTestDialogText(device, "End: X coordinate")
        clickTestDialogText(device, "Apply")
        val saved = save(board) { it.elements.size == 3 }
        val edge = saved.elements.last() as Connector
        assertEquals(ConnectorEnd.Bound(board.activePage.elements[0].id, ConnectionPort.RIGHT), edge.start)
        assertEquals(ConnectorEnd.Free(Point(320f, 270f)), edge.end)
        control("Undo").click()
        save(board) { it.elements == board.activePage.elements }
        control("Redo").click()
        save(board) { it.elements.last() == edge }
        completed = true
    }

    @Test fun anUnconfirmedAttachmentChoiceSurvivesConfigurationChange(): Unit = runBlocking {
        val board = open()
        revealTestDialogText(device, "Attachment point: Left side")
        description("End: Attachment point:").click()
        text("Top side").click()
        try {
            device.setOrientationLeft()
            text("Attachment points follow the object when it moves or rotates.")
            revealTestDialogText(device, "Attachment point: Top side")
            description("End: Attachment point: Top side")
            capture.snapshot("connection-editor-landscape")
            clickTestDialogText(device, "Apply")
            val saved = save(board) { it.elements.size == 3 }
            val edge = saved.elements.last() as Connector
            assertEquals(ConnectorEnd.Bound(board.activePage.elements[1].id, ConnectionPort.TOP), edge.end)
            completed = true
        } finally {
            device.setOrientationNatural()
            device.unfreezeRotation()
        }
    }
}
