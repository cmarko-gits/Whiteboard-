package dev.whiteboard.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.whiteboard.canvas.*
import dev.whiteboard.core.model.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RotationRendererTest {
    @get:Rule val capture = CaptureUiFailure()
    private val shape =
        Shape(bounds = Bounds(100f, 150f, 300f, 250f), kind = Tool.RECTANGLE, style = InkStyle(width = 4f), fill = Color.RED)

    private fun placed() = AffineElement.fromDocument(shape, AffineTransform2D.rotation(45.0, shape.bounds.center))

    @Test fun rotatedShapePaintsItsInteriorAndLeavesEmptyBoundingBoxCornersUntouched() {
        val bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.WHITE)
            val element = placed()
            BoardRenderer().drawElement(Canvas(bitmap), element)
            assertEquals(Color.RED, bitmap.getPixel(200, 200))
            assertEquals(Color.WHITE, bitmap.getPixel((element.bounds.left + 2).toInt(), (element.bounds.top + 2).toInt()))
        } finally {
            bitmap.recycle()
        }
    }

    @Test fun nonuniformShapePlacementKeepsInkWidthInDocumentUnits() {
        val source = shape.copy(
            bounds = Bounds(100f, 100f, 200f, 200f),
            fill = Color.WHITE,
            style = InkStyle(color = Color.BLACK, width = 4f)
        )
        val element = AffineElement.fromDocument(source, AffineTransform2D.scale(3.0, 2.0, source.bounds.center))
        val bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.WHITE)
            BoardRenderer().drawElement(Canvas(bitmap), element)
            val width = (44..56).count { bitmap.getPixel(150, it) != Color.WHITE }
            assertTrue("Placed ink was scaled instead of retaining its 4-unit width: $width", width in 4..6)
        } finally {
            bitmap.recycle()
        }
    }

    @Test fun placedMediaLoadsItsOriginalAttachmentAndDrawsOnlyTheRotatedRectangle() {
        val tile = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val output = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        try {
            val media = MediaElement(bounds = Bounds(100f, 150f, 300f, 250f), assetId = "${newId()}.png")
            val element = AffineElement.fromDocument(media, AffineTransform2D.rotation(45.0, media.bounds.center))
            val requests = mutableListOf<String>()
            val assets = AssetProvider { id, _ ->
                requests += id
                tile
            }
            output.eraseColor(Color.WHITE)
            BoardRenderer(assets, strictAssets = true).drawElement(Canvas(output), element)
            assertEquals(listOf(media.assetId), requests)
            assertEquals(Color.RED, output.getPixel(200, 200))
            assertEquals(Color.WHITE, output.getPixel((element.bounds.left + 2).toInt(), (element.bounds.top + 2).toInt()))
        } finally {
            tile.recycle()
            output.recycle()
        }
    }

    @Test fun pngExportUsesTheSamePlacedGeometryAsTheEditor() {
        val element = placed()
        val page = Page(background = Background.PLAIN, backgroundColor = Color.WHITE, elements = listOf(element))
        val bitmap = PageExporter().bitmap(page)
        try {
            val bounds = checkNotNull(page.contentBounds).expand(32f)
            val scale = bitmap.width / bounds.width
            fun pixel(point: Point) = bitmap.getPixel(((point.x - bounds.left) * scale).toInt(), ((point.y - bounds.top) * scale).toInt())
            assertEquals(Color.RED, pixel(Point(200f, 200f)))
            assertEquals(Color.WHITE, pixel(Point(element.bounds.left + 2, element.bounds.top + 2)))
        } finally {
            bitmap.recycle()
        }
    }

    @Test fun placedNoteExposesTheConnectedAccessibilityEditAction() {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val note = Note(bounds = Bounds(100f, 100f, 300f, 250f), text = "Rotation accessibility")
        val placed = AffineElement.fromDocument(note, AffineTransform2D.rotation(30.0, note.bounds.center))
        val events = mutableListOf<CanvasEvent>()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContentView(
                    WhiteboardView(activity).apply {
                        update(Page(elements = listOf(placed)), Tool.SELECT, InkStyle(), emptySet())
                        onEvent = events::add
                    }
                )
            }
            instrument.waitForIdleSync()
            val until = android.os.SystemClock.uptimeMillis() + 10_000
            var target: AccessibilityNodeInfo? = null
            while (target == null && android.os.SystemClock.uptimeMillis() < until) {
                val queue = ArrayDeque<AccessibilityNodeInfo>()
                instrument.uiAutomation.rootInActiveWindow?.let(queue::add)
                var remaining = 1000
                while (queue.isNotEmpty() && remaining-- > 0) {
                    val node = queue.removeFirst()
                    if (!node.refresh()) continue
                    if (node.contentDescription?.toString() == "Note: Rotation accessibility") {
                        target = node
                        break
                    }
                    repeat(node.childCount) { node.getChild(it)?.let(queue::add) }
                }
                if (target == null) android.os.SystemClock.sleep(50)
            }
            assertTrue(checkNotNull(target) { "Placed note was not exposed" }.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK))
            instrument.runOnMainSync {
                val edit = events.filterIsInstance<CanvasEvent.EditText>().single()
                assertEquals(placed, edit.existing)
                assertTrue(edit.note)
                assertEquals(placed.bounds.center, edit.position)
            }
        }
    }

    @Test fun draggingBeyondTheDocumentLimitRejectsTheGestureWithoutAPartialTransform() {
        val instrument = InstrumentationRegistry.getInstrumentation()
        instrument.runOnMainSync {
            val source = shape.copy(bounds = Bounds(-1_000_000_000f, 0f, 0f, 100f), style = InkStyle(width = 0.1f))
            val placed = AffineElement.fromDocument(source)
            val view = WhiteboardView(instrument.targetContext)
            view.layout(0, 0, 1000, 1000)
            view.update(Page(elements = listOf(placed)), Tool.SELECT, InkStyle(), setOf(placed.id))
            val events = mutableListOf<CanvasEvent>()
            view.onEvent = events::add
            val started = android.os.SystemClock.uptimeMillis()
            fun send(action: Int, x: Float) {
                val event = android.view.MotionEvent.obtain(started, android.os.SystemClock.uptimeMillis(), action, x, 50f, 0)
                try {
                    view.onTouchEvent(event)
                } finally {
                    event.recycle()
                }
            }
            send(android.view.MotionEvent.ACTION_DOWN, 1f)
            send(android.view.MotionEvent.ACTION_MOVE, -100f)
            send(android.view.MotionEvent.ACTION_UP, -100f)
            assertTrue(events.filterIsInstance<CanvasEvent.Replace>().isEmpty())
            assertEquals(listOf(CanvasEvent.Rejected(DocumentError.EDIT_LIMIT)), events.filterIsInstance<CanvasEvent.Rejected>())
        }
    }
}
