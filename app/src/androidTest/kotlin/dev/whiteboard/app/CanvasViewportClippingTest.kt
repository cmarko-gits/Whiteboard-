package dev.whiteboard.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.whiteboard.canvas.CanvasEvent
import dev.whiteboard.canvas.WhiteboardView
import dev.whiteboard.core.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CanvasViewportClippingTest {
    private fun onMain(action: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
    private fun view() = WhiteboardView(ApplicationProvider.getApplicationContext<Context>()).apply { layout(0, 0, WIDTH, HEIGHT) }

    private fun assertParentMarginsRemainUntouched(view: WhiteboardView, inspect: (Bitmap) -> Unit) {
        val bitmap = Bitmap.createBitmap(WIDTH + MARGIN * 2, HEIGHT + MARGIN * 2, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.MAGENTA)
            val canvas = Canvas(bitmap)
            canvas.translate(MARGIN.toFloat(), MARGIN.toFloat())
            val originalClip = canvas.clipBounds
            val originalSaveCount = canvas.saveCount
            view.draw(canvas)
            assertEquals(originalClip, canvas.clipBounds)
            assertEquals(originalSaveCount, canvas.saveCount)
            for (y in 0 until bitmap.height) {
                for (x in 0 until bitmap.width) {
                    if (x !in MARGIN until MARGIN + WIDTH || y !in MARGIN until MARGIN + HEIGHT) {
                        assertEquals("Drawing escaped its viewport at $x,$y", Color.MAGENTA, bitmap.getPixel(x, y))
                    }
                }
            }
            inspect(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    @Test fun documentAndSelectionRemainInsideTheNativeViewportAtDifferentCameras() = onMain {
        for (bounds in listOf(Bounds(-100f, -80f, 240f, 160f), Bounds(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat()))) {
            for (camera in listOf(Viewport(), Viewport(Point(20f, 10f), 1.5f))) {
                val shape = Shape(bounds = bounds, kind = Tool.RECTANGLE, style = InkStyle(width = 9f), fill = Color.RED)
                val page = Page(background = Background.PLAIN, elements = listOf(shape))
                val view = view()
                val events = mutableListOf<CanvasEvent>()
                view.onEvent = events::add
                view.update(page, Tool.SELECT, InkStyle(), setOf(shape.id), camera)
                assertParentMarginsRemainUntouched(view) { bitmap ->
                    assertEquals(Color.RED, bitmap.getPixel(MARGIN + WIDTH / 2, MARGIN + HEIGHT / 2))
                }
                assertTrue(events.isEmpty())
                assertEquals(listOf(shape), page.elements)
            }
        }
    }

    @Test fun aLiveStrokeIsVisuallyClippedWithoutTrimmingItsCommittedDocumentCoordinates() = onMain {
        val view = view()
        val events = mutableListOf<CanvasEvent>()
        view.onEvent = events::add
        view.update(Page(background = Background.PLAIN), Tool.PEN, InkStyle(color = Color.BLUE, width = 8f), emptySet())
        val started = SystemClock.uptimeMillis()
        fun send(action: Int, x: Float, y: Float) {
            val event = MotionEvent.obtain(started, SystemClock.uptimeMillis(), action, x, y, 0)
            try {
                assertTrue(view.onTouchEvent(event))
            } finally {
                event.recycle()
            }
        }
        send(MotionEvent.ACTION_DOWN, 60f, 40f)
        send(MotionEvent.ACTION_MOVE, 180f, -40f)
        assertParentMarginsRemainUntouched(view) { bitmap ->
            var bluePixels = 0
            for (y in MARGIN until MARGIN + HEIGHT) {
                for (x in MARGIN until MARGIN + WIDTH) {
                    if (bitmap.getPixel(x, y) == Color.BLUE) bluePixels++
                }
            }
            assertTrue("The in-bounds live stroke must still be drawn", bluePixels > 10)
        }
        assertTrue(events.none { it is CanvasEvent.Add })
        send(MotionEvent.ACTION_UP, 180f, -40f)
        val stroke = events.filterIsInstance<CanvasEvent.Add>().single().element as Stroke
        assertEquals(Point(60f, 40f), stroke.points.first())
        assertEquals(Point(180f, -40f), stroke.points.last())
    }

    companion object {
        private const val WIDTH = 120
        private const val HEIGHT = 80
        private const val MARGIN = 32
    }
}
