package dev.whiteboard.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.whiteboard.app.workspace.DocumentFiles
import dev.whiteboard.app.workspace.LocalMediaStore
import dev.whiteboard.canvas.*
import dev.whiteboard.core.model.*
import dev.whiteboard.data.LocalBoardRepository
import dev.whiteboard.data.WhiteboardDatabase
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConnectorRenderTest {
    @get:Rule val capture = CaptureUiFailure()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val first =
        Shape(id = "first", bounds = Bounds(40f, 40f, 140f, 140f), kind = Tool.RECTANGLE, style = InkStyle(Color.GREEN), fill = Color.GREEN)
    private val second = first.copy(id = "second", bounds = Bounds(260f, 180f, 360f, 280f), fill = Color.BLUE)
    private val edge = Connector(
        id = "edge",
        start = ConnectorEnd.Bound(first.id, ConnectionPort.RIGHT),
        end = ConnectorEnd.Bound(second.id, ConnectionPort.LEFT),
        style = InkStyle(Color.RED, 4f)
    )
    private val page = Page(background = Background.PLAIN, backgroundColor = Color.WHITE, elements = listOf(first, edge, second))
    private val moved = first.copy(bounds = Bounds(40f, 160f, 140f, 260f))
    private fun red(color: Int) {
        assertTrue("Expected red ink, actual color=$color", Color.red(color) >= 200 && Color.green(color) <= 80 && Color.blue(color) <= 80)
    }

    @Test fun theRendererFollowsTheTargetAndKeepsTheOriginalSnapshotUnchanged() {
        val renderer = BoardRenderer()
        val before = Bitmap.createBitmap(480, 320, Bitmap.Config.ARGB_8888)
        val after = Bitmap.createBitmap(480, 320, Bitmap.Config.ARGB_8888)
        try {
            renderer.drawPage(Canvas(before), page, Bounds(0f, 0f, 480f, 320f), 1f)
            val updated = page.geometry.preview(listOf(moved)).page
            renderer.drawPage(Canvas(after), updated, Bounds(0f, 0f, 480f, 320f), 1f)
            red(before.getPixel(200, 160))
            assertEquals(Color.WHITE, before.getPixel(200, 220))
            assertEquals(Color.WHITE, after.getPixel(200, 160))
            red(after.getPixel(200, 220))
            assertEquals(Point(140f, 90f), (page.geometry.resolve(edge) as Line).start)
            assertEquals(Point(140f, 210f), (updated.geometry.resolve(edge) as Line).start)
            val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            if (output != null) {
                val comparison = Bitmap.createBitmap(960, 344, Bitmap.Config.ARGB_8888)
                try {
                    comparison.eraseColor(Color.WHITE)
                    val canvas = Canvas(comparison)
                    val paint = android.graphics.Paint().apply {
                        color = Color.BLACK
                        textSize = 16f
                    }
                    canvas.drawText("Original", 20f, 18f, paint)
                    canvas.drawText("Moved target", 500f, 18f, paint)
                    canvas.drawBitmap(before, 0f, 24f, null)
                    canvas.drawBitmap(after, 480f, 24f, null)
                    val file = File(output, "connector-renderer-comparison.png")
                    file.parentFile?.mkdirs()
                    file.outputStream().use {
                        assertTrue(comparison.compress(Bitmap.CompressFormat.PNG, 100, it))
                    }
                } finally {
                    comparison.recycle()
                }
            }
        } finally {
            before.recycle()
            after.recycle()
        }
    }

    @Test fun aNativeDragUpdatesTheConnectionPreviewAndCancelCannotCommitIt() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = WhiteboardView(context)
            view.layout(0, 0, 480, 320)
            view.update(page, Tool.SELECT, InkStyle(), setOf(first.id))
            val events = mutableListOf<CanvasEvent>()
            view.onEvent = events::add
            val started = SystemClock.uptimeMillis()
            fun send(action: Int, y: Float) {
                val event = MotionEvent.obtain(started, SystemClock.uptimeMillis(), action, 90f, y, 0)
                try {
                    event.source = InputDevice.SOURCE_TOUCHSCREEN
                    assertTrue(view.onTouchEvent(event))
                } finally {
                    event.recycle()
                }
            }
            val image = Bitmap.createBitmap(480, 320, Bitmap.Config.ARGB_8888)
            try {
                send(MotionEvent.ACTION_DOWN, 90f)
                send(MotionEvent.ACTION_MOVE, 210f)
                view.draw(Canvas(image))
                red(image.getPixel(200, 220))
                assertEquals(Color.WHITE, image.getPixel(200, 160))
                assertTrue(events.filterIsInstance<CanvasEvent.Replace>().isEmpty())
                send(MotionEvent.ACTION_CANCEL, 210f)
                view.draw(Canvas(image))
                red(image.getPixel(200, 160))
                assertEquals(Color.WHITE, image.getPixel(200, 220))
                assertTrue(events.filterIsInstance<CanvasEvent.Replace>().isEmpty())
                assertEquals(listOf(first, edge, second), page.elements)
            } finally {
                image.recycle()
            }
        }
    }

    @Test fun roomRecoveryThumbnailAndPngJpegPdfExportsKeepTheSameConnection() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, WhiteboardDatabase::class.java).build()
        val repository = LocalBoardRepository(database)
        val files = DocumentFiles(context, repository, LocalMediaStore(context))
        val temporary = mutableListOf<File>()
        try {
            val original = Board(name = "Connection persistence", pages = listOf(page))
            repository.save(original)
            assertEquals(original, repository.load(original.id))
            val draft = original.withPage(page.geometry.preview(listOf(moved)).page)
            repository.saveDraft(draft)
            assertEquals(draft, repository.draft(original.id))
            val png = ByteArrayOutputStream()
            PageExporter().png(page, png)
            val bitmap = checkNotNull(BitmapFactory.decodeByteArray(png.toByteArray(), 0, png.size()))
            try {
                val bounds = checkNotNull(page.contentBounds).expand(32f)
                val scale = bitmap.width / bounds.width
                red(bitmap.getPixel(((200f - bounds.left) * scale).toInt(), ((160f - bounds.top) * scale).toInt()))
            } finally {
                bitmap.recycle()
            }
            val jpeg = File.createTempFile("connection-test-", ".jpg", context.cacheDir).also { temporary.add(it) }
            files.exportJpeg(Uri.fromFile(jpeg), page)
            val raster = checkNotNull(BitmapFactory.decodeFile(jpeg.absolutePath))
            try {
                red(raster.getPixel(raster.width / 2, raster.height / 2))
            } finally {
                raster.recycle()
            }
            val pdf = File.createTempFile("connection-test-", ".pdf", context.cacheDir).also { temporary.add(it) }
            files.exportPdf(Uri.fromFile(pdf), original)
            ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    assertEquals(1, renderer.pageCount)
                    renderer.openPage(0).use { exported ->
                        val image = Bitmap.createBitmap(exported.width, exported.height, Bitmap.Config.ARGB_8888)
                        try {
                            exported.render(image, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            red(image.getPixel(image.width / 2, image.height / 2))
                        } finally {
                            image.recycle()
                        }
                    }
                }
            }
            files.thumbnail(original)
            val preview = checkNotNull(repository.observeBoards().first().single().thumbnail)
            temporary.add(File(preview))
            assertNotNull(files.readPreview(preview))
        } finally {
            temporary.forEach { it.delete() }
            database.close()
        }
    }

    @Test fun theNativeConnectionNodeTracksBoundsAndDoesNotAdvertiseImpossibleMoves() {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val description = context.getString(dev.whiteboard.canvas.R.string.canvas_connection_arrow)
        val events = mutableListOf<CanvasEvent>()
        var view: WhiteboardView? = null
        fun node(): AccessibilityNodeInfo {
            val deadline = SystemClock.uptimeMillis() + 10_000
            while (SystemClock.uptimeMillis() < deadline) {
                val queue = ArrayDeque<AccessibilityNodeInfo>()
                instrument.uiAutomation.rootInActiveWindow?.let(queue::add)
                var remaining = 2000
                while (queue.isNotEmpty() && remaining-- > 0) {
                    val current = queue.removeFirst()
                    if (!current.refresh()) continue
                    if (current.contentDescription?.toString() == description) return current
                    repeat(current.childCount) { current.getChild(it)?.let(queue::add) }
                }
                SystemClock.sleep(50)
            }
            error("Connection accessibility node was not exposed")
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            try {
                scenario.onActivity { activity ->
                    val canvas = WhiteboardView(activity).apply {
                        update(page, Tool.SELECT, InkStyle(), emptySet())
                        onEvent = events::add
                    }
                    view = canvas
                    activity.setContentView(canvas)
                }
                instrument.waitForIdleSync()
                val initial = node()
                val before = Rect().also(initial::getBoundsInScreen)
                assertFalse(initial.actionList.any { it.id == dev.whiteboard.canvas.R.id.canvas_move_right })
                assertFalse(initial.actionList.any { it.id == dev.whiteboard.canvas.R.id.canvas_grow })
                assertTrue(initial.actionList.any { it.id == dev.whiteboard.canvas.R.id.canvas_delete })
                assertFalse(initial.performAction(dev.whiteboard.canvas.R.id.canvas_move_right))
                assertTrue(initial.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                instrument.runOnMainSync {
                    checkNotNull(view).update(page.geometry.preview(listOf(moved)).page, Tool.SELECT, InkStyle(), setOf(edge.id))
                }
                instrument.waitForIdleSync()
                val after = Rect().also(node()::getBoundsInScreen)
                assertTrue("Connection bounds must follow the moved target", after.top > before.top)
                assertTrue(events.filterIsInstance<CanvasEvent.TransformBy>().isEmpty())
                assertEquals(setOf(edge.id), events.filterIsInstance<CanvasEvent.Select>().single().ids)
            } catch (error: Throwable) {
                capture.snapshot("connection-accessibility-before-cleanup")
                throw error
            }
        }
    }
}
