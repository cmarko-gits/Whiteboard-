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
class SelectionPreviewTest {
    private val lower = Shape(
        bounds = Bounds(50f, 50f, 350f, 350f),
        kind = Tool.RECTANGLE,
        style = InkStyle(color = Color.RED),
        fill = Color.RED
    )
    private val upper = lower.copy(id = newId(), bounds = Bounds(250f, 250f, 550f, 550f), fill = Color.BLUE)
    private val page = Page(background = Background.PLAIN, backgroundColor = Color.WHITE, elements = listOf(lower, upper))
    private var started = 0L

    private fun onView(block: (WhiteboardView, MutableList<CanvasEvent>) -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = WhiteboardView(ApplicationProvider.getApplicationContext<Context>())
            view.layout(0, 0, 800, 800)
            view.update(page, Tool.SELECT, InkStyle(), setOf(lower.id))
            val events = mutableListOf<CanvasEvent>()
            view.onEvent = events::add
            block(view, events)
        }
    }

    private fun send(view: WhiteboardView, action: Int, x: Float, y: Float) {
        val now = SystemClock.uptimeMillis()
        if (action == MotionEvent.ACTION_DOWN) started = now
        val event = MotionEvent.obtain(started, now, action, x, y, 0)
        try {
            assertTrue(view.onTouchEvent(event))
        } finally {
            event.recycle()
        }
    }

    private fun pixels(view: WhiteboardView, block: (Bitmap) -> Unit) {
        val bitmap = Bitmap.createBitmap(800, 800, Bitmap.Config.ARGB_8888)
        try {
            view.draw(Canvas(bitmap))
            block(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    @Test fun movingAnObjectKeepsItsOriginalLayerDuringPreviewAndCommitsOnlyOnRelease() = onView { view, events ->
        send(view, MotionEvent.ACTION_DOWN, 100f, 100f)
        send(view, MotionEvent.ACTION_MOVE, 200f, 200f)
        pixels(view) { image ->
            assertEquals(Color.BLUE, image.getPixel(300, 300))
            assertEquals(Color.WHITE, image.getPixel(100, 150))
            assertEquals(Color.RED, image.getPixel(200, 200))
        }
        assertTrue(events.filterIsInstance<CanvasEvent.Replace>().isEmpty())
        assertEquals(listOf(lower, upper), page.elements)
        send(view, MotionEvent.ACTION_UP, 200f, 200f)
        val change = events.filterIsInstance<CanvasEvent.Replace>().single()
        assertEquals(listOf(lower.copy(bounds = Bounds(150f, 150f, 450f, 450f))), change.elements)
    }

    @Test fun cancellingTheGestureRestoresTheOriginalGeometryWithoutCommitting() = onView { view, events ->
        send(view, MotionEvent.ACTION_DOWN, 100f, 100f)
        send(view, MotionEvent.ACTION_MOVE, 200f, 200f)
        send(view, MotionEvent.ACTION_CANCEL, 200f, 200f)
        pixels(view) { image ->
            assertEquals(Color.RED, image.getPixel(100, 150))
            assertEquals(Color.BLUE, image.getPixel(300, 300))
        }
        assertTrue(events.filterIsInstance<CanvasEvent.Replace>().isEmpty())
        assertEquals(listOf(lower, upper), page.elements)
    }

    @Test fun anExternalObjectUpdateClearsTheOldPreviewAndCannotCommitStaleGeometry() = onView { view, events ->
        send(view, MotionEvent.ACTION_DOWN, 100f, 100f)
        send(view, MotionEvent.ACTION_MOVE, 200f, 200f)
        val movedElsewhere = lower.copy(bounds = Bounds(50f, 400f, 350f, 700f))
        view.update(page.copy(elements = listOf(movedElsewhere, upper)), Tool.SELECT, InkStyle(), setOf(lower.id))
        pixels(view) { image ->
            assertEquals(Color.WHITE, image.getPixel(200, 200))
            assertEquals(Color.RED, image.getPixel(100, 500))
        }
        send(view, MotionEvent.ACTION_UP, 200f, 200f)
        assertTrue(events.filterIsInstance<CanvasEvent.Replace>().isEmpty())
    }
}
