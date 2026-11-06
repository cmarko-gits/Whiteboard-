package dev.whiteboard.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
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
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MindMapRenderTest {
    @get:Rule val capture = CaptureUiFailure()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrument = InstrumentationRegistry.getInstrumentation()
    private val root = Note(id = "map-root", bounds = Bounds(20f, 20f, 120f, 100f), text = "Root", color = Color.RED)
    private val child = root.copy(id = "map-child", bounds = Bounds(260f, 180f, 360f, 260f), text = "Hidden child", color = Color.BLUE)
    private val edge =
        Connector(
            "map-edge",
            ConnectorEnd.Bound(root.id, ConnectionPort.RIGHT),
            ConnectorEnd.Bound(child.id, ConnectionPort.LEFT),
            InkStyle(Color.GREEN, 4f),
            false
        )
    private val tree = MindMapTree("map", listOf(MindMapNode(root.id), MindMapNode(child.id, root.id, edge.id)))
    private val page =
        Page(background = Background.PLAIN, backgroundColor = Color.WHITE, elements = listOf(root, edge, child), mindMaps = listOf(tree))
    private val collapsed = page.copy(mindMaps = listOf(tree.withCollapsed(root.id, true)))
    private val reference = page.copy(elements = listOf(root), mindMaps = emptyList())

    @Test fun rendererAndRasterExporterHideDescendantsAndKeepTheCanonicalDocumentUnchanged() {
        val expandedImage = Bitmap.createBitmap(480, 320, Bitmap.Config.ARGB_8888)
        val collapsedImage = Bitmap.createBitmap(480, 320, Bitmap.Config.ARGB_8888)
        val actual = PageExporter().bitmap(collapsed)
        val expected = PageExporter().bitmap(reference)
        try {
            val renderer = BoardRenderer()
            val frame = Bounds(0f, 0f, 480f, 320f)
            renderer.drawPage(Canvas(expandedImage), page, frame, 1f)
            renderer.drawPage(Canvas(collapsedImage), collapsed, frame, 1f)
            assertEquals(Color.BLUE, expandedImage.getPixel(340, 240))
            assertEquals(Color.GREEN, expandedImage.getPixel(190, 140))
            assertEquals(Color.WHITE, collapsedImage.getPixel(340, 240))
            assertEquals(Color.WHITE, collapsedImage.getPixel(190, 140))
            assertEquals(Color.RED, collapsedImage.getPixel(100, 80))
            assertTrue("Raster dimensions and pixels must match the visible scene", actual.sameAs(expected))
            assertEquals(listOf(root, edge, child), collapsed.elements)
            assertEquals(page.contentBounds, collapsed.contentBounds)
        } finally {
            expandedImage.recycle()
            collapsedImage.recycle()
            actual.recycle()
            expected.recycle()
        }
    }

    @Test fun jpegAndPdfUseTheVisibleFrameAndCannotExportHiddenBlueContent(): Unit = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, WhiteboardDatabase::class.java).build()
        val files = DocumentFiles(context, LocalBoardRepository(database), LocalMediaStore(context))
        val temporary = mutableListOf<File>()
        fun output(suffix: String) = File.createTempFile("map-visible-", suffix, context.cacheDir).also { temporary.add(it) }
        try {
            val jpeg = output(".jpg")
            val expectedJpeg = output(".jpg")
            files.exportJpeg(Uri.fromFile(jpeg), collapsed)
            files.exportJpeg(Uri.fromFile(expectedJpeg), reference)
            val actual = checkNotNull(BitmapFactory.decodeFile(jpeg.absolutePath))
            val expected = checkNotNull(BitmapFactory.decodeFile(expectedJpeg.absolutePath))
            try {
                assertTrue(actual.sameAs(expected))
            } finally {
                actual.recycle()
                expected.recycle()
            }
            val pdf = output(".pdf")
            val expectedPdf = output(".pdf")
            files.exportPdf(Uri.fromFile(pdf), Board(name = "Collapsed", pages = listOf(collapsed)))
            files.exportPdf(Uri.fromFile(expectedPdf), Board(name = "Visible reference", pages = listOf(reference)))
            fun raster(file: File): Bitmap = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    assertEquals(1, renderer.pageCount)
                    renderer.openPage(0).use { exported ->
                        Bitmap.createBitmap(exported.width, exported.height, Bitmap.Config.ARGB_8888).also {
                            exported.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                    }
                }
            }
            val actualPdf = raster(pdf)
            val referencePdf = raster(expectedPdf)
            try {
                assertTrue("PDF frame and artwork must match the visible reference", actualPdf.sameAs(referencePdf))
            } finally {
                actualPdf.recycle()
                referencePdf.recycle()
            }
        } finally {
            temporary.forEach { it.delete() }
            database.close()
        }
    }

    @Test fun collapsingDuringANativeDragCancelsThePreviewAndCannotCommitHiddenGeometry() {
        instrument.runOnMainSync {
            val view = WhiteboardView(context).apply { layout(0, 0, 480, 320) }
            val events = mutableListOf<CanvasEvent>()
            view.onEvent = events::add
            view.update(page, Tool.SELECT, InkStyle(), setOf(child.id))
            val started = SystemClock.uptimeMillis()
            fun send(action: Int, x: Float) {
                val event = MotionEvent.obtain(started, SystemClock.uptimeMillis(), action, x, 220f, 0)
                try {
                    event.source = InputDevice.SOURCE_TOUCHSCREEN
                    assertTrue(view.onTouchEvent(event))
                } finally {
                    event.recycle()
                }
            }
            send(MotionEvent.ACTION_DOWN, 310f)
            send(MotionEvent.ACTION_MOVE, 390f)
            view.update(collapsed, Tool.SELECT, InkStyle(), setOf(child.id))
            send(MotionEvent.ACTION_UP, 390f)
            assertTrue(events.filterIsInstance<CanvasEvent.Replace>().isEmpty())
            events.clear()
            send(MotionEvent.ACTION_DOWN, 310f)
            send(MotionEvent.ACTION_UP, 310f)
            assertTrue(events.filterIsInstance<CanvasEvent.Select>().all { child.id !in it.ids })
            assertTrue(events.filterIsInstance<CanvasEvent.EditText>().isEmpty())
        }
    }

    @Test fun collapsingRemovesNativeAccessibilityChildrenAndRejectsAnOldVirtualNodeAction() {
        val childDescription = describeCanvasElement(context, child)
        fun nodes(): List<AccessibilityNodeInfo> {
            val result = mutableListOf<AccessibilityNodeInfo>()
            val queue = ArrayDeque<AccessibilityNodeInfo>()
            instrument.uiAutomation.rootInActiveWindow?.let(queue::add)
            while (queue.isNotEmpty() && result.size < 2000) {
                val node = queue.removeFirst()
                result.add(node)
                for (index in 0 until node.childCount) node.getChild(index)?.let(queue::add)
            }
            return result
        }
        val events = mutableListOf<CanvasEvent>()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var view: WhiteboardView? = null
            try {
                scenario.onActivity { activity ->
                    view = WhiteboardView(activity).apply {
                        update(page, Tool.SELECT, InkStyle(), emptySet())
                        onEvent = events::add
                    }.also(activity::setContentView)
                }
                instrument.waitForIdleSync()
                var initial: AccessibilityNodeInfo? = null
                val deadline = SystemClock.uptimeMillis() + 10_000
                while (initial == null && SystemClock.uptimeMillis() < deadline) {
                    initial = nodes().firstOrNull { it.contentDescription?.toString() == childDescription }
                    if (initial == null) SystemClock.sleep(50)
                }
                val old = checkNotNull(initial) { "Expanded child must have an actual framework node" }
                assertTrue(old.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                scenario.onActivity { checkNotNull(view).update(collapsed, Tool.SELECT, InkStyle(), setOf(child.id)) }
                instrument.waitForIdleSync()
                assertFalse(nodes().any { it.contentDescription?.toString() == childDescription })
                events.clear()
                assertFalse(old.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                assertTrue(events.isEmpty())
            } catch (failure: Throwable) {
                capture.snapshot("mind-map-visibility-before-cleanup")
                throw failure
            }
        }
    }
}
