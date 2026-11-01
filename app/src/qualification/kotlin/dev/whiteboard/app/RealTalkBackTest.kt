package dev.whiteboard.app

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.content.Context
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.whiteboard.canvas.CanvasEvent
import dev.whiteboard.canvas.WhiteboardView
import dev.whiteboard.core.model.*
import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit qualification run with the installed TalkBack service, not the default UI suite. */
@RunWith(AndroidJUnit4::class)
class RealTalkBackTest {
    @Test fun talkBackCanFocusAndActivateAVirtualCanvasObject() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(AccessibilityManager::class.java)
        val services = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        assertTrue(
            "The qualification requires the real TalkBack service",
            services.any {
                it.resolveInfo.serviceInfo.packageName ==
                    "com.google.android.marvin.talkback"
            }
        )
        assertTrue("TalkBack touch exploration must be active", manager.isTouchExplorationEnabled)
        val note = Note(bounds = Bounds(100f, 120f, 400f, 320f), text = "TalkBack qualification note")
        val events = mutableListOf<CanvasEvent>()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val view = WhiteboardView(activity)
                view.update(Page(elements = listOf(note)), Tool.SELECT, InkStyle(), emptySet())
                view.onEvent = events::add
                activity.setContentView(view)
            }
            instrumentation.waitForIdleSync()
            val until = android.os.SystemClock.uptimeMillis() + 10_000
            var target: AccessibilityNodeInfo? = null
            do {
                val queue = ArrayDeque<AccessibilityNodeInfo>()
                automation.rootInActiveWindow?.let(queue::add)
                while (queue.isNotEmpty()) {
                    val current = queue.removeFirst()
                    if (current.contentDescription?.toString() == "Note: TalkBack qualification note") {
                        target = current
                        break
                    }
                    repeat(current.childCount) { current.getChild(it)?.let(queue::add) }
                }
                if (target == null) android.os.SystemClock.sleep(50)
            } while (target == null && android.os.SystemClock.uptimeMillis() < until)
            val node = checkNotNull(target) { "The real accessibility hierarchy did not expose the note" }
            node.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
            instrumentation.waitForIdleSync()
            val focusUntil = android.os.SystemClock.uptimeMillis() + 5000
            while (node.refresh() && !node.isAccessibilityFocused && android.os.SystemClock.uptimeMillis() < focusUntil) {
                node.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
                android.os.SystemClock.sleep(50)
            }
            assertTrue(node.isAccessibilityFocused)
            assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            instrumentation.runOnMainSync { assertTrue(events.any { it == CanvasEvent.Select(setOf(note.id)) }) }
            automation.takeScreenshot()?.let { bitmap ->
                try {
                    File(context.getExternalFilesDir(null), "qualification-talkback.png").outputStream().use {
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                    }
                } finally {
                    bitmap.recycle()
                }
            }
            val report = JSONObject().put("service", "com.google.android.marvin.talkback").put("touchExploration", true)
                .put("virtualObjectFocus", true).put("virtualObjectClick", true).put("api", android.os.Build.VERSION.SDK_INT)
            File(context.getExternalFilesDir(null), "qualification-talkback.json").writeText(report.toString(2))
        }
    }
}
