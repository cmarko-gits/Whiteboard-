package dev.whiteboard.app

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.whiteboard.canvas.CanvasEvent
import dev.whiteboard.canvas.WhiteboardView
import dev.whiteboard.core.editor.PartialStrokeEraser
import dev.whiteboard.core.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PartialEraserGestureTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val stroke = Stroke(points = listOf(Point(100f, 200f), Point(600f, 200f)), style = InkStyle(width = 6f))
    private val note = Note(bounds = Bounds(100f, 400f, 300f, 550f), text = "Keep note")
    private val page = Page(elements = listOf(ElementGroup(children = listOf(stroke, note))))
    private var downTime = 0L

    private fun gesture(
        mode: EraserMode? = EraserMode.PARTIAL,
        viewport: Viewport = Viewport(),
        action: (WhiteboardView, MutableList<CanvasEvent>) -> Unit
    ) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = WhiteboardView(context)
            view.layout(0, 0, 1000, 1000)
            if (mode != null) view.eraserMode = mode
            view.update(page, Tool.ERASER, InkStyle(), emptySet(), viewport)
            val events = mutableListOf<CanvasEvent>()
            view.onEvent = { events += it }
            action(view, events)
        }
    }

    private fun send(view: WhiteboardView, action: Int, x: Float = 300f, y: Float = 100f) {
        val now = SystemClock.uptimeMillis()
        if (action == MotionEvent.ACTION_DOWN) downTime = now
        val event = MotionEvent.obtain(downTime, now, action, x, y, 0)
        try {
            view.onTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    @Test fun releaseSampleCutsTheSweptSegmentEvenWithoutAMoveEvent() = gesture { view, events ->
        send(view, MotionEvent.ACTION_DOWN)
        assertTrue(events.isEmpty())
        send(view, MotionEvent.ACTION_UP, y = 300f)
        val cut = events.filterIsInstance<CanvasEvent.EraseParts>().single().cuts.single()
        assertEquals(stroke, cut.original)
        assertEquals(2, cut.fragments.size)
        assertTrue(cut.fragments.first().points.last().x < 300f)
        assertTrue(cut.fragments.last().points.first().x > 300f)
        assertTrue(cut.fragments.all { it.style == stroke.style })
    }

    @Test fun theDefaultModeRetainsTheExistingWholeStrokeEraseEvent() = gesture(mode = null) { view, events ->
        send(view, MotionEvent.ACTION_DOWN)
        send(view, MotionEvent.ACTION_UP, y = 300f)
        assertEquals(setOf(stroke.id), events.filterIsInstance<CanvasEvent.EraseStrokes>().single().ids)
        assertTrue(events.filterIsInstance<CanvasEvent.EraseParts>().isEmpty())
    }

    @Test fun cancellingAPreviewDoesNotCommitAnyCut() = gesture { view, events ->
        send(view, MotionEvent.ACTION_DOWN)
        send(view, MotionEvent.ACTION_MOVE, y = 300f)
        assertTrue(events.isEmpty())
        send(view, MotionEvent.ACTION_CANCEL)
        assertTrue(events.isEmpty())
    }

    @Test fun addingASecondFingerCancelsTheCutAndAllowsNavigation() = gesture { view, events ->
        send(view, MotionEvent.ACTION_DOWN)
        send(view, MotionEvent.ACTION_MOVE, y = 300f)
        val properties = Array(2) { index ->
            MotionEvent.PointerProperties().apply {
                id = index
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
        }
        val coordinates = Array(2) { index ->
            MotionEvent.PointerCoords().apply {
                x = 300f + index * 200f
                y = 300f
                pressure = 1f
                size =
                    1f
            }
        }
        listOf(MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP).forEach { action ->
            val event = MotionEvent.obtain(
                downTime, SystemClock.uptimeMillis(), action or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                2, properties, coordinates, 0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_TOUCHSCREEN, 0
            )
            try {
                view.onTouchEvent(event)
            } finally {
                event.recycle()
            }
        }
        send(view, MotionEvent.ACTION_UP, y = 300f)
        assertTrue(events.none { it is CanvasEvent.EraseParts || it is CanvasEvent.EraseStrokes })
    }

    @Test fun changingTheModeCancelsTheUnfinishedGesture() = gesture { view, events ->
        send(view, MotionEvent.ACTION_DOWN)
        send(view, MotionEvent.ACTION_MOVE, y = 300f)
        view.eraserMode = EraserMode.STROKE
        send(view, MotionEvent.ACTION_UP, y = 300f)
        assertTrue(events.isEmpty())
    }

    @Test fun changingTheDocumentCancelsTheOldPreview() = gesture { view, events ->
        send(view, MotionEvent.ACTION_DOWN)
        send(view, MotionEvent.ACTION_MOVE, y = 300f)
        view.update(page.copy(elements = listOf(note)), Tool.ERASER, InkStyle(), emptySet())
        send(view, MotionEvent.ACTION_UP, y = 300f)
        assertTrue(events.isEmpty())
    }

    @Test fun zoomChangesTheDocumentRadiusAndCoordinatesTogether() = gesture(viewport = Viewport(Point(50f, 50f), 2f)) { view, events ->
        send(view, MotionEvent.ACTION_DOWN, x = 650f, y = 350f)
        send(view, MotionEvent.ACTION_UP, x = 650f, y = 550f)
        val cut = events.filterIsInstance<CanvasEvent.EraseParts>().single().cuts.single()
        val reach = 15 * context.resources.displayMetrics.density / 2 + stroke.style.width / 2
        assertEquals(300f - reach, cut.fragments.first().points.last().x, 0.001f)
        assertEquals(300f + reach, cut.fragments.last().points.first().x, 0.001f)
        assertEquals(stroke.points.last(), cut.fragments.last().points.last())
    }

    @Test fun aMissDoesNotEmitADocumentChange() = gesture { view, events ->
        send(view, MotionEvent.ACTION_DOWN, x = 800f)
        send(view, MotionEvent.ACTION_UP, x = 900f)
        assertTrue(events.isEmpty())
    }

    @Test fun excessiveFragmentationIsReportedOnceAndDoesNotCommitThePreview() = gesture { view, events ->
        val complex = stroke.copy(points = List(PartialStrokeEraser.MAX_FRAGMENTS + 3) { Point(if (it % 2 == 0) 100f else 600f, 200f) })
        view.update(page.copy(elements = listOf(complex)), Tool.ERASER, InkStyle(), emptySet())
        send(view, MotionEvent.ACTION_DOWN, y = 200f)
        send(view, MotionEvent.ACTION_MOVE, y = 210f)
        send(view, MotionEvent.ACTION_UP, y = 220f)
        assertEquals(listOf(CanvasEvent.Rejected(DocumentError.EDIT_LIMIT)), events)
    }
}
