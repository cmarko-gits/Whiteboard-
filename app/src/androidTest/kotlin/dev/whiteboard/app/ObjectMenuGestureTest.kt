package dev.whiteboard.app

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.whiteboard.canvas.CanvasEvent
import dev.whiteboard.canvas.WhiteboardView
import dev.whiteboard.core.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ObjectMenuGestureTest {
    private val instrument = InstrumentationRegistry.getInstrumentation()
    private val lower = Shape(bounds = Bounds(40f, 40f, 360f, 360f), kind = Tool.RECTANGLE, style = InkStyle(), fill = -1)
    private val upper = Note(bounds = Bounds(180f, 180f, 460f, 460f), text = "Top note")
    private val page = Page(elements = listOf(lower, upper))
    private var started = 0L

    private class Fixture(val view: WhiteboardView) {
        val events = mutableListOf<CanvasEvent>()
        val menus = mutableListOf<Set<String>>()
    }

    private fun withView(block: (ActivityScenario<MainActivity>, Fixture) -> Unit) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var fixture: Fixture
            scenario.onActivity { activity ->
                fixture = Fixture(WhiteboardView(activity))
                fixture.view.apply {
                    update(page, Tool.SELECT, InkStyle(), emptySet())
                    onEvent = { event ->
                        fixture.events.add(event)
                        if (event is CanvasEvent.Select) update(page, Tool.SELECT, InkStyle(), event.ids)
                    }
                    onObjectMenu = { fixture.menus.add(it) }
                }
                activity.setContentView(fixture.view)
            }
            instrument.waitForIdleSync()
            block(scenario, fixture)
        }
    }

    private fun send(view: WhiteboardView, action: Int, x: Float = 240f, y: Float = 240f) {
        val now = SystemClock.uptimeMillis()
        if (action == MotionEvent.ACTION_DOWN) started = now
        val event = MotionEvent.obtain(started, now, action, x, y, 0)
        try {
            assertTrue(view.onTouchEvent(event))
        } finally {
            event.recycle()
        }
    }

    private fun awaitLongPress() {
        SystemClock.sleep(ViewConfiguration.getLongPressTimeout().toLong() + 150)
        instrument.waitForIdleSync()
    }

    @Test fun longPressTargetsTheTopmostObjectAndReleaseDoesNotEditOrMoveIt() = withView { scenario, fixture ->
        scenario.onActivity { send(fixture.view, MotionEvent.ACTION_DOWN) }
        awaitLongPress()
        scenario.onActivity {
            assertEquals(listOf(setOf(upper.id)), fixture.menus)
            send(fixture.view, MotionEvent.ACTION_UP)
            assertTrue(fixture.events.all { it is CanvasEvent.Select })
            assertEquals(listOf(lower, upper), page.elements)
        }
    }

    @Test fun dragCancelSecondPointerExternalChangeToolChangeAndDetachCancelThePendingMenu() = withView { scenario, fixture ->
        listOf("drag", "cancel", "pointer", "source", "tool", "detach").forEach { change ->
            scenario.onActivity { activity ->
                fixture.view.update(page, Tool.SELECT, InkStyle(), emptySet())
                fixture.events.clear()
                send(fixture.view, MotionEvent.ACTION_DOWN)
                when (change) {
                    "drag" -> {
                        send(fixture.view, MotionEvent.ACTION_MOVE, 340f, 240f)
                        send(fixture.view, MotionEvent.ACTION_CANCEL)
                    }
                    "cancel" -> send(fixture.view, MotionEvent.ACTION_CANCEL)
                    "source" -> fixture.view.update(page.copy(elements = listOf(lower)), Tool.SELECT, InkStyle(), emptySet())
                    "tool" -> fixture.view.update(page, Tool.PEN, InkStyle(), emptySet())
                    "detach" -> activity.setContentView(FrameLayout(activity))
                    "pointer" -> {
                        val properties = Array(2) { index ->
                            MotionEvent.PointerProperties().apply {
                                id = index
                                toolType = MotionEvent.TOOL_TYPE_FINGER
                            }
                        }
                        val coordinates = Array(2) { index ->
                            MotionEvent.PointerCoords().apply {
                                x = 240f + index * 100f
                                y = 240f
                                pressure = 1f
                                size = 1f
                            }
                        }
                        val event = MotionEvent.obtain(
                            started, SystemClock.uptimeMillis(),
                            MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                            2, properties, coordinates, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0
                        )
                        try {
                            fixture.view.onTouchEvent(event)
                        } finally {
                            event.recycle()
                        }
                    }
                }
            }
            awaitLongPress()
            scenario.onActivity { activity ->
                assertTrue("Pending menu survived $change", fixture.menus.isEmpty())
                send(fixture.view, MotionEvent.ACTION_CANCEL)
                assertTrue(fixture.events.all { it is CanvasEvent.Select })
                if (change == "detach") activity.setContentView(fixture.view)
            }
            instrument.waitForIdleSync()
        }
    }

    @Test fun holdingADrawingToolStillCreatesOneStrokeWithoutOpeningAMenu() = withView { scenario, fixture ->
        scenario.onActivity {
            fixture.view.update(page, Tool.PENCIL, InkStyle(), emptySet())
            send(fixture.view, MotionEvent.ACTION_DOWN)
        }
        awaitLongPress()
        scenario.onActivity {
            send(fixture.view, MotionEvent.ACTION_UP)
            assertTrue(fixture.menus.isEmpty())
            val stroke = fixture.events.filterIsInstance<CanvasEvent.Add>().single().element as Stroke
            assertEquals(Tool.PENCIL, stroke.tool)
            assertEquals(listOf(Point(240f, 240f)), stroke.points)
        }
    }

    @Test fun keyboardAndCoordinateContextMenusPreserveTheSelectionAndRejectEmptyOrInvalidTargets() = withView { scenario, fixture ->
        scenario.onActivity {
            val selected = setOf(lower.id, upper.id)
            fixture.view.update(page, Tool.SELECT, InkStyle(), selected)
            assertTrue(fixture.view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MENU)))
            assertEquals(selected, fixture.menus.last())
            val shiftF10 = KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F10, 0, KeyEvent.META_SHIFT_ON)
            assertTrue(fixture.view.dispatchKeyEvent(shiftF10))
            assertEquals(selected, fixture.menus.last())
            fixture.view.update(page, Tool.SELECT, InkStyle(), emptySet())
            assertFalse(fixture.view.showContextMenu())
            assertFalse(fixture.view.showContextMenu(Float.NaN, 100f))
            assertFalse(fixture.view.showContextMenu(600f, 600f))
            assertTrue(fixture.view.showContextMenu(240f, 240f))
            assertEquals(setOf(upper.id), fixture.menus.last())
            fixture.view.onObjectMenu = null
            assertFalse(fixture.view.showContextMenu())
            assertTrue(fixture.events.all { it is CanvasEvent.Select })
        }
    }
}
