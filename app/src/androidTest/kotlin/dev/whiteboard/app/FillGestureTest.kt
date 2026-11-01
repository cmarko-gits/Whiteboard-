package dev.whiteboard.app

import android.content.Context
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
class FillGestureTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun gesture(viewport: Viewport = Viewport(), action: (WhiteboardView, MutableList<CanvasEvent>) -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = WhiteboardView(context)
            view.layout(0, 0, 1000, 1000)
            view.update(Page(), Tool.FILL, InkStyle(color = 0xFF12AB34.toInt()), emptySet(), viewport)
            val events = mutableListOf<CanvasEvent>()
            view.onEvent = { events += it }
            action(view, events)
        }
    }
    private fun send(view: WhiteboardView, action: Int, x: Float = 100f, y: Float = 100f) {
        val now = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(now, now, action, x, y, 0)
        try {
            view.onTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    @Test fun completedTapUsesDocumentCoordinatesAtZoom() = gesture(Viewport(Point(50f, 50f), 2f)) { view, events ->
        send(view, MotionEvent.ACTION_DOWN)
        send(view, MotionEvent.ACTION_UP)
        assertEquals(listOf(CanvasEvent.Fill(Point(25f, 25f), 0xFF12AB34.toInt())), events.filterIsInstance<CanvasEvent.Fill>())
    }

    @Test fun cancellationDoesNotCommitAFill() = gesture { view, events ->
        send(view, MotionEvent.ACTION_DOWN)
        send(view, MotionEvent.ACTION_CANCEL)
        assertTrue(events.filterIsInstance<CanvasEvent.Fill>().isEmpty())
    }

    @Test fun dragDoesNotBecomeAFillEvenIfItReturnsToTheStartingPoint() = gesture { view, events ->
        send(view, MotionEvent.ACTION_DOWN)
        send(view, MotionEvent.ACTION_MOVE, 500f, 500f)
        send(view, MotionEvent.ACTION_UP)
        assertTrue(events.filterIsInstance<CanvasEvent.Fill>().isEmpty())
    }

    @Test fun distantReleaseWithoutAMoveEventDoesNotFillTheOldPoint() = gesture { view, events ->
        send(view, MotionEvent.ACTION_DOWN)
        send(view, MotionEvent.ACTION_UP, 500f, 500f)
        assertTrue(events.filterIsInstance<CanvasEvent.Fill>().isEmpty())
    }
}
