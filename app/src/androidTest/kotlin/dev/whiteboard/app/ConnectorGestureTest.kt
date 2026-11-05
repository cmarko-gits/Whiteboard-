package dev.whiteboard.app

import android.content.Context
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
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
class ConnectorGestureTest {
    private val first = Note(bounds = Bounds(40f, 40f, 140f, 140f), text = "A")
    private val second = Note(bounds = Bounds(260f, 180f, 360f, 280f), text = "B")
    private val page = Page(elements = listOf(first, second))
    private val ink = InkStyle(color = 0xFFCC2244.toInt(), width = 4f, opacity = 0.75f)
    private var started = 0L

    private fun onView(block: (WhiteboardView, MutableList<CanvasEvent>) -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = WhiteboardView(ApplicationProvider.getApplicationContext<Context>())
            view.layout(0, 0, 480, 320)
            view.update(page, Tool.CONNECTOR, ink, emptySet())
            val events = mutableListOf<CanvasEvent>()
            view.onEvent = events::add
            block(view, events)
        }
    }
    private fun send(view: WhiteboardView, action: Int, x: Float, y: Float, stylus: Boolean = false) {
        val now = SystemClock.uptimeMillis()
        if (action == MotionEvent.ACTION_DOWN) started = now
        val properties = arrayOf(
            MotionEvent.PointerProperties().apply {
                id = 0
                toolType = if (stylus) MotionEvent.TOOL_TYPE_STYLUS else MotionEvent.TOOL_TYPE_FINGER
            }
        )
        val coordinates = arrayOf(
            MotionEvent.PointerCoords().apply {
                this.x = x
                this.y = y
                pressure = 1f
                size = 1f
            }
        )
        val event = MotionEvent.obtain(
            started, now, action, 1, properties, coordinates, 0, 0, 1f, 1f, 0, 0,
            if (stylus) InputDevice.SOURCE_STYLUS else InputDevice.SOURCE_TOUCHSCREEN, 0
        )
        try {
            assertTrue(view.onTouchEvent(event))
        } finally {
            event.recycle()
        }
    }
    private fun secondPointer(view: WhiteboardView) {
        val properties = Array(2) { value ->
            MotionEvent.PointerProperties().apply {
                id = value
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
        }
        val coordinates =
            Array(2) { value ->
                MotionEvent.PointerCoords().apply {
                    x = 310f + value * 20
                    y = 230f
                    pressure = 1f
                    size = 1f
                }
            }
        val event = MotionEvent.obtain(
            started, SystemClock.uptimeMillis(), MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            2, properties, coordinates, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0
        )
        try {
            assertTrue(view.onTouchEvent(event))
        } finally {
            event.recycle()
        }
    }

    @Test fun releaseCoordinatesBindTwoDifferentObjectsWithTheOriginalStyle() = onView { view, events ->
        send(view, MotionEvent.ACTION_DOWN, 90f, 90f)
        send(view, MotionEvent.ACTION_MOVE, 190f, 90f)
        assertTrue(events.filterIsInstance<CanvasEvent.Add>().isEmpty())
        send(view, MotionEvent.ACTION_UP, 310f, 230f)
        val connection = events.filterIsInstance<CanvasEvent.Add>().single().element as Connector
        assertEquals(ConnectorEnd.Bound(first.id, ConnectionPort.RIGHT), connection.start)
        assertEquals(ConnectorEnd.Bound(second.id, ConnectionPort.LEFT), connection.end)
        assertEquals(ink, connection.style)
        assertTrue(connection.arrow)
        assertFalse(events.contains(CanvasEvent.ConnectionTargetMissing))
        assertEquals(listOf(first, second), page.elements)
    }

    @Test fun backgroundAndSelfConnectionsAreRejectedWithoutWritingAnObject() {
        listOf(Point(420f, 30f), Point(90f, 90f)).forEach { destination ->
            onView { view, events ->
                send(view, MotionEvent.ACTION_DOWN, 90f, 90f)
                send(view, MotionEvent.ACTION_UP, destination.x, destination.y)
                assertTrue(events.filterIsInstance<CanvasEvent.Add>().isEmpty())
                assertEquals(1, events.count { it == CanvasEvent.ConnectionTargetMissing })
            }
        }
        onView { view, events ->
            send(view, MotionEvent.ACTION_DOWN, 420f, 30f)
            send(view, MotionEvent.ACTION_UP, 310f, 230f)
            assertTrue(events.filterIsInstance<CanvasEvent.Add>().isEmpty())
        }
    }

    @Test fun cancelSecondPointerToolChangeFocusLossAndPageEditsCannotCommitAnOldGesture() {
        val cancellations: List<(WhiteboardView) -> Unit> = listOf(
            { send(it, MotionEvent.ACTION_CANCEL, 310f, 230f) },
            { secondPointer(it) },
            { it.update(page, Tool.PEN, ink, emptySet()) },
            { it.onWindowFocusChanged(false) },
            { it.update(page.copy(elements = listOf(first, second.copy(text = "Changed"))), Tool.CONNECTOR, ink, emptySet()) }
        )
        cancellations.forEach { cancel ->
            onView { view, events ->
                send(view, MotionEvent.ACTION_DOWN, 90f, 90f)
                send(view, MotionEvent.ACTION_MOVE, 310f, 230f)
                cancel(view)
                send(view, MotionEvent.ACTION_UP, 310f, 230f)
                assertTrue(events.filterIsInstance<CanvasEvent.Add>().isEmpty())
                assertFalse(events.contains(CanvasEvent.ConnectionTargetMissing))
            }
        }
    }

    @Test fun theHostAccessibilityActionUsesTheDialogAlternativeInsteadOfInventingATarget() = onView { view, events ->
        var opened = 0
        view.onCreateConnection = { opened++ }
        assertTrue(view.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null))
        assertEquals(1, opened)
        assertTrue(events.filterIsInstance<CanvasEvent.Add>().isEmpty())
    }

    @Test fun stylusInputStillCreatesAConnectionWhenFingerDrawingIsDisabled() = onView { view, events ->
        view.fingerDrawing = false
        send(view, MotionEvent.ACTION_DOWN, 90f, 90f, stylus = true)
        send(view, MotionEvent.ACTION_UP, 310f, 230f, stylus = true)
        val created = events.filterIsInstance<CanvasEvent.Add>().single().element as Connector
        assertEquals(ConnectorEnd.Bound(first.id, ConnectionPort.RIGHT), created.start)
        assertEquals(ConnectorEnd.Bound(second.id, ConnectionPort.LEFT), created.end)
    }
}
