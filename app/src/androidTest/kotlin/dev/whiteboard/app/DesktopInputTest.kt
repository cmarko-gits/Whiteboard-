package dev.whiteboard.app

import android.content.Context
import android.view.InputDevice
import android.view.KeyEvent
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
class DesktopInputTest {
    private fun wheel(value: Float, modifiers: Int = 0): MotionEvent {
        val properties = MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_MOUSE
        }
        val coordinates = MotionEvent.PointerCoords().apply {
            x = 200f
            y = 160f
            setAxisValue(MotionEvent.AXIS_VSCROLL, value)
        }
        return MotionEvent.obtain(
            0L, 1L, MotionEvent.ACTION_SCROLL, 1, arrayOf(properties), arrayOf(coordinates),
            modifiers, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0
        )
    }
    private fun onView(block: (WhiteboardView, MutableList<CanvasEvent>) -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = WhiteboardView(ApplicationProvider.getApplicationContext<Context>())
            view.layout(0, 0, 720, 900)
            val events = mutableListOf<CanvasEvent>()
            view.onEvent = events::add
            block(view, events)
        }
    }

    @Test fun wheelPansWithoutCreatingDocumentObjects() = onView { view, events ->
        val motion = wheel(1f)
        try {
            assertTrue(view.onGenericMotionEvent(motion))
        } finally {
            motion.recycle()
        }
        assertTrue((events.single() as CanvasEvent.Camera).viewport.offset.y > 0f)
    }

    @Test fun controlWheelZoomKeepsTheDocumentPointUnderTheCursor() = onView { view, events ->
        val motion = wheel(1f, KeyEvent.META_CTRL_ON)
        try {
            assertTrue(view.onGenericMotionEvent(motion))
        } finally {
            motion.recycle()
        }
        val viewport = (events.single() as CanvasEvent.Camera).viewport
        assertTrue(viewport.scale > 1f)
        val point = viewport.toDocument(Point(200f, 160f))
        assertEquals(200f, point.x, 0.001f)
        assertEquals(160f, point.y, 0.001f)
    }

    @Test fun nonFiniteWheelInputIsRejectedWithoutAnEvent() = onView { view, events ->
        val motion = wheel(Float.NaN)
        try {
            assertFalse(view.onGenericMotionEvent(motion))
        } finally {
            motion.recycle()
        }
        assertTrue(events.isEmpty())
    }

    @Test fun arrowKeysPanOnlyWithThePanTool() = onView { view, events ->
        view.update(Page(), Tool.PAN, InkStyle(), emptySet())
        assertTrue(view.onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)))
        assertTrue((events.single() as CanvasEvent.Camera).viewport.offset.x < 0f)
    }
}
