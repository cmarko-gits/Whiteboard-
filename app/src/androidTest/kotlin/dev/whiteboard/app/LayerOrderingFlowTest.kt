package dev.whiteboard.app

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import dev.whiteboard.canvas.PageExporter
import dev.whiteboard.core.model.*
import dev.whiteboard.data.*
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LayerOrderingFlowTest {
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
    private fun menuAction(label: String): UiObject2 {
        var node = text(label)
        while (!node.isClickable && node.isEnabled) node = checkNotNull(node.parent) { "Missing menu action: $label" }
        return node
    }

    @After fun cleanup(): Unit = runBlocking {
        if (!completed) capture.snapshot("layer-order-before-cleanup")
        launchTestLibrary(device)
        owned.forEach { repository.deletePermanently(it) }
        database.close()
    }

    private suspend fun saveAndRead(board: Board, expected: List<String>): Page {
        text("Unsaved changes")
        control("Save").click()
        text("Saved on this device")
        return withTimeout(10_000) {
            var page = repository.load(board.id).activePage
            while (page.elements.map(PageObject::id) != expected) {
                delay(50)
                page = repository.load(board.id).activePage
            }
            page
        }
    }

    private fun assertPngCenter(page: Page, color: Int) {
        val output = ByteArrayOutputStream()
        PageExporter().png(page, output)
        val bytes = output.toByteArray()
        val image = checkNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        try {
            assertEquals(color, image.getPixel(image.width / 2, image.height / 2))
        } finally {
            image.recycle()
        }
    }

    @Test fun stepActionsRespectBoundariesUndoSaveReopenAndTheVisiblePngOrder(): Unit = runBlocking {
        val rectangle = Shape(bounds = Bounds(100f, 100f, 300f, 300f), kind = Tool.RECTANGLE, style = InkStyle(), fill = Color.RED)
        val ellipse = Shape(bounds = rectangle.bounds, kind = Tool.ELLIPSE, style = InkStyle(), fill = Color.BLUE)
        val original = listOf(rectangle, ellipse)
        val board = Board(name = "Layers ${newId()}", pages = listOf(Page(elements = original)))
        owned += board.id
        repository.save(board)
        launchTestLibrary(device)
        openTestBoard(device, board.name)
        control("More actions").click()
        text("Objects").click()
        text("Rectangle").click()
        awaitTestKeyboardHidden(device)
        text("1 selected")
        control("Selection actions").click()
        assertFalse(menuAction("Send backward one layer").isEnabled)
        menuAction("Bring forward one layer").click()
        val forward = saveAndRead(board, listOf(ellipse.id, rectangle.id))
        assertPngCenter(forward, Color.RED)

        control("Selection actions").click()
        assertFalse(menuAction("Bring forward one layer").isEnabled)
        menuAction("Send backward one layer").click()
        assertPngCenter(saveAndRead(board, original.map(Element::id)), Color.BLUE)
        control("Undo").click()
        assertPngCenter(saveAndRead(board, listOf(ellipse.id, rectangle.id)), Color.RED)
        control("Redo").click()
        assertPngCenter(saveAndRead(board, original.map(Element::id)), Color.BLUE)
        control("Back to boards").click()
        openTestBoard(device, board.name)
        control("More actions")
        assertEquals(original, repository.load(board.id).activePage.elements)
        completed = true
    }
}
