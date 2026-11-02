package dev.whiteboard.app

import android.content.Context
import android.os.SystemClock
import android.view.InputDevice
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
class FinalGestureInputTest {
    private val ink = InkStyle(color = 0xFF12AB34.toInt(), width = 0.5f, opacity = 0.6f)
    private val brushes = listOf(Tool.PEN, Tool.PENCIL, Tool.MARKER, Tool.HIGHLIGHTER)
    private val shapes = listOf(Tool.RECTANGLE, Tool.SQUARE, Tool.ELLIPSE, Tool.CIRCLE, Tool.TRIANGLE, Tool.DIAMOND, Tool.LINE, Tool.ARROW)
    private var started = 0L

    private fun onView(tool: Tool, block: (WhiteboardView, MutableList<CanvasEvent>) -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = WhiteboardView(ApplicationProvider.getApplicationContext<Context>())
            view.layout(0, 0, 1000, 1000)
            view.update(Page(), tool, ink, emptySet())
            val events = mutableListOf<CanvasEvent>()
            view.onEvent = events::add
            block(view, events)
        }
    }

    private fun send(view: WhiteboardView, action: Int, point: Point): Point {
        val now = SystemClock.uptimeMillis()
        if (action == MotionEvent.ACTION_DOWN) started = now
        val event = MotionEvent.obtain(started, now, action, point.x, point.y, 0)
        try {
            view.onTouchEvent(event)
            return Point(event.x, event.y)
        } finally {
            event.recycle()
        }
    }

    @Test fun everyBrushRetainsTheFinalPointAfterTheLastMove() {
        brushes.forEach { tool ->
            onView(tool) { view, events ->
                send(view, MotionEvent.ACTION_DOWN, Point(10f, 20f))
                send(view, MotionEvent.ACTION_MOVE, Point(40f, 60f))
                send(view, MotionEvent.ACTION_UP, Point(80f, 100f))
                val stroke = events.filterIsInstance<CanvasEvent.Add>().single().element as Stroke
                assertEquals(listOf(Point(10f, 20f), Point(40f, 60f), Point(80f, 100f)), stroke.points)
                assertEquals(tool, stroke.tool)
                assertEquals(ink, stroke.style)
            }
        }
    }

    @Test fun aFastStrokeWithoutMoveStillConnectsDownAndUp() {
        brushes.forEach { tool ->
            onView(tool) { view, events ->
                send(view, MotionEvent.ACTION_DOWN, Point(10f, 20f))
                send(view, MotionEvent.ACTION_UP, Point(80f, 100f))
                assertEquals(
                    listOf(Point(10f, 20f), Point(80f, 100f)),
                    (events.filterIsInstance<CanvasEvent.Add>().single().element as Stroke).points
                )
            }
        }
    }

    @Test fun subpixelReleaseIsPreservedButATapDoesNotDuplicateItsPoint() = onView(Tool.PEN) { view, events ->
        send(view, MotionEvent.ACTION_DOWN, Point(10f, 20f))
        val delivered = send(view, MotionEvent.ACTION_UP, Point(10.2f, 20.1f))
        // Native MotionEvent storage can quantize decimal coordinates on newer Android releases.
        assertEquals(10.2f, delivered.x, 0.001f)
        assertEquals(20.1f, delivered.y, 0.001f)
        assertEquals(
            listOf(Point(10f, 20f), delivered),
            (events.filterIsInstance<CanvasEvent.Add>().single().element as Stroke).points
        )
        events.clear()
        send(view, MotionEvent.ACTION_DOWN, Point(50f, 50f))
        send(view, MotionEvent.ACTION_UP, Point(50f, 50f))
        assertEquals(listOf(Point(50f, 50f)), (events.filterIsInstance<CanvasEvent.Add>().single().element as Stroke).points)
    }

    @Test fun everyShapeUsesTheReleaseCoordinatesInsteadOfTheOldPreview() {
        shapes.forEach { tool ->
            onView(tool) { view, events ->
                send(view, MotionEvent.ACTION_DOWN, Point(10f, 20f))
                send(view, MotionEvent.ACTION_MOVE, Point(40f, 60f))
                send(view, MotionEvent.ACTION_UP, Point(80f, 120f))
                val result = events.filterIsInstance<CanvasEvent.Add>().single().element
                if (result is Line) {
                    assertEquals(Point(10f, 20f), result.start)
                    assertEquals(Point(80f, 120f), result.end)
                    assertEquals(tool == Tool.ARROW, result.arrow)
                } else {
                    assertEquals(
                        if (tool in
                            listOf(Tool.SQUARE, Tool.CIRCLE)
                        ) {
                            Bounds(10f, 20f, 110f, 120f)
                        } else {
                            Bounds(10f, 20f, 80f, 120f)
                        },
                        result.bounds
                    )
                    assertEquals(tool, (result as Shape).kind)
                }
            }
        }
    }

    @Test fun aFastShapeNeedsNoMoveAndAZeroLengthShapeDoesNotAppear() {
        shapes.forEach { tool ->
            onView(tool) { view, events ->
                send(view, MotionEvent.ACTION_DOWN, Point(10f, 20f))
                send(view, MotionEvent.ACTION_UP, Point(80f, 120f))
                assertEquals(1, events.filterIsInstance<CanvasEvent.Add>().size)
                events.clear()
                send(view, MotionEvent.ACTION_DOWN, Point(50f, 50f))
                send(view, MotionEvent.ACTION_UP, Point(50f, 50f))
                assertTrue(events.filterIsInstance<CanvasEvent.Add>().isEmpty())
            }
        }
    }

    @Test fun aThinHorizontalLineIsNotRejectedBecauseItsInkIsLessThanTwoUnitsHigh() = onView(Tool.LINE) { view, events ->
        send(view, MotionEvent.ACTION_DOWN, Point(10f, 20f))
        send(view, MotionEvent.ACTION_UP, Point(80f, 20f))
        val line = events.filterIsInstance<CanvasEvent.Add>().single().element as Line
        assertEquals(Point(80f, 20f), line.end)
        assertEquals(ink, line.style)
    }

    @Test fun cancelledOrReplacedGesturesCannotCommitAReleasePoint() {
        listOf(Tool.PEN, Tool.RECTANGLE).forEach { tool ->
            onView(tool) { view, events ->
                send(view, MotionEvent.ACTION_DOWN, Point(10f, 20f))
                send(view, MotionEvent.ACTION_MOVE, Point(40f, 60f))
                send(view, MotionEvent.ACTION_CANCEL, Point(40f, 60f))
                send(view, MotionEvent.ACTION_UP, Point(80f, 100f))
                assertTrue(events.filterIsInstance<CanvasEvent.Add>().isEmpty())
                send(view, MotionEvent.ACTION_DOWN, Point(10f, 20f))
                view.update(Page(), tool, ink, emptySet())
                send(view, MotionEvent.ACTION_UP, Point(80f, 100f))
                assertTrue(events.filterIsInstance<CanvasEvent.Add>().isEmpty())
                send(view, MotionEvent.ACTION_DOWN, Point(10f, 20f))
                view.update(Page(), Tool.PAN, ink, emptySet())
                send(view, MotionEvent.ACTION_UP, Point(80f, 100f))
                assertTrue(events.filterIsInstance<CanvasEvent.Add>().isEmpty())
            }
        }
    }

    @Test fun anOutOfBoundsReleaseIsRejectedBeforeAnyDocumentMutation() {
        listOf(Tool.PEN, Tool.RECTANGLE).forEach { tool ->
            onView(tool) { view, events ->
                send(view, MotionEvent.ACTION_DOWN, Point(10f, 20f))
                send(view, MotionEvent.ACTION_UP, Point(10_000_000_000f, 20f))
                assertEquals(listOf(CanvasEvent.Rejected(DocumentError.EDIT_LIMIT)), events)
                send(view, MotionEvent.ACTION_UP, Point(80f, 100f))
                assertEquals(1, events.size)
            }
        }
    }

    @Test fun aSecondFingerCancelsDrawingBeforeTheRelease() {
        listOf(Tool.PEN, Tool.RECTANGLE).forEach { tool ->
            onView(tool) { view, events ->
                send(view, MotionEvent.ACTION_DOWN, Point(10f, 20f))
                send(view, MotionEvent.ACTION_MOVE, Point(40f, 60f))
                val properties = Array(2) { index ->
                    MotionEvent.PointerProperties().apply {
                        id = index
                        toolType = MotionEvent.TOOL_TYPE_FINGER
                    }
                }
                val coordinates = Array(2) { index ->
                    MotionEvent.PointerCoords().apply {
                        x = 40f + index * 100f
                        y = 60f
                        pressure = 1f
                        size = 1f
                    }
                }
                listOf(MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP).forEach { action ->
                    val event = MotionEvent.obtain(
                        started, SystemClock.uptimeMillis(), action or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                        2, properties, coordinates, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0
                    )
                    try {
                        view.onTouchEvent(event)
                    } finally {
                        event.recycle()
                    }
                }
                send(view, MotionEvent.ACTION_UP, Point(80f, 100f))
                assertTrue(events.filterIsInstance<CanvasEvent.Add>().isEmpty())
            }
        }
    }

    @Test fun releasingTheActiveStylusBeforeASecondPointerPreservesItsEndpoint() = onView(Tool.PEN) { view, events ->
        fun stylusEvent(action: Int, point: Point, second: Boolean) {
            val now = SystemClock.uptimeMillis()
            if (action == MotionEvent.ACTION_DOWN) started = now
            val count = if (second) 2 else 1
            val properties = Array(count) { index ->
                MotionEvent.PointerProperties().apply {
                    id = index
                    toolType = if (index == 0) MotionEvent.TOOL_TYPE_STYLUS else MotionEvent.TOOL_TYPE_FINGER
                }
            }
            val coordinates = Array(count) { index ->
                MotionEvent.PointerCoords().apply {
                    x = point.x + index * 100f
                    y = point.y
                    pressure = 1f
                    size = 1f
                }
            }
            val event = MotionEvent.obtain(
                started, now, action, count, properties, coordinates,
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0
            )
            try {
                view.onTouchEvent(event)
            } finally {
                event.recycle()
            }
        }
        stylusEvent(MotionEvent.ACTION_DOWN, Point(10f, 20f), false)
        stylusEvent(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), Point(10f, 20f), true)
        stylusEvent(MotionEvent.ACTION_POINTER_UP, Point(80f, 100f), true)
        send(view, MotionEvent.ACTION_UP, Point(200f, 200f))
        assertEquals(
            listOf(Point(10f, 20f), Point(80f, 100f)),
            (events.filterIsInstance<CanvasEvent.Add>().single().element as Stroke).points
        )
    }
}
