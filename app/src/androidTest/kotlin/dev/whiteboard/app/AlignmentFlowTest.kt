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
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlignmentFlowTest {
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

    @After fun cleanup(): Unit = runBlocking {
        if (!completed) capture.snapshot("alignment-before-cleanup")
        launchTestLibrary(device)
        owned.forEach { repository.deletePermanently(it) }
        database.close()
    }

    private suspend fun saveAndRead(board: Board, expected: List<Element>): Page {
        text("Unsaved changes")
        control("Save").click()
        text("Saved on this device")
        return withTimeout(10_000) {
            var page = repository.load(board.id).activePage
            while (page.elements != expected) {
                delay(50)
                page = repository.load(board.id).activePage
            }
            page
        }
    }

    private fun assertPngCenters(page: Page) {
        val output = ByteArrayOutputStream()
        PageExporter().png(page, output)
        val bytes = output.toByteArray()
        val image = checkNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        val area = checkNotNull(page.contentBounds).expand(32f)
        try {
            page.elements.forEach { element ->
                val shape = element as Shape
                val center = shape.bounds.center
                val x = ((center.x - area.left) * image.width / area.width).roundToInt()
                val y = ((center.y - area.top) * image.height / area.height).roundToInt()
                assertEquals(shape.fill, image.getPixel(x, y))
            }
        } finally {
            image.recycle()
        }
    }

    @Test fun allSixActionsSupportCancelUndoRedoSaveReopenAndPngExport(): Unit = runBlocking {
        val first = Shape(bounds = Bounds(100f, 100f, 180f, 180f), kind = Tool.RECTANGLE, style = InkStyle(), fill = Color.RED)
        val second = Shape(bounds = Bounds(300f, 300f, 420f, 420f), kind = Tool.ELLIPSE, style = InkStyle(), fill = Color.BLUE)
        val original = listOf(first, second)
        val board = Board(name = "Alignment ${newId()}", pages = listOf(Page(elements = original)))
        owned += board.id
        repository.save(board)
        launchTestLibrary(device)
        openTestBoard(device, board.name)
        control("More actions").click()
        text("Select all objects").click()
        control("Selection actions").click()
        text("Alignment").click()
        text("Cancel").click()
        text("Saved on this device")
        assertEquals(original, repository.load(board.id).activePage.elements)

        val cases = listOf(
            Triple("Align left edges", first.bounds, Bounds(100f, 300f, 220f, 420f)),
            Triple("Align horizontal centers", Bounds(220f, 100f, 300f, 180f), Bounds(200f, 300f, 320f, 420f)),
            Triple("Align right edges", Bounds(340f, 100f, 420f, 180f), second.bounds),
            Triple("Align top edges", first.bounds, Bounds(300f, 100f, 420f, 220f)),
            Triple("Align vertical centers", Bounds(100f, 220f, 180f, 300f), Bounds(300f, 200f, 420f, 320f)),
            Triple("Align bottom edges", Bounds(100f, 340f, 180f, 420f), second.bounds)
        )
        cases.forEachIndexed { index, (label, a, b) ->
            control("Selection actions").click()
            text("Alignment").click()
            text(label).click()
            assertTrue(device.wait(Until.gone(By.text("Alignment")), 10_000))
            val expected = listOf(first.copy(bounds = a), second.copy(bounds = b))
            val aligned = saveAndRead(board, expected)
            if (index == 0) assertPngCenters(aligned)
            control("Undo").click()
            saveAndRead(board, original)
            control("Redo").click()
            saveAndRead(board, expected)
            if (index != cases.lastIndex) {
                control("Undo").click()
                saveAndRead(board, original)
            }
        }
        control("Back to boards").click()
        openTestBoard(device, board.name)
        control("More actions")
        val expected = listOf(first.copy(bounds = cases.last().second), second)
        assertEquals(expected, repository.load(board.id).activePage.elements)
        completed = true
    }
}
