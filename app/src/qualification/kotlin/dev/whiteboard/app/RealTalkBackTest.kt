package dev.whiteboard.app

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.content.Context
import android.os.SystemClock
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.whiteboard.canvas.CanvasEvent
import dev.whiteboard.canvas.WhiteboardView
import dev.whiteboard.core.model.*
import dev.whiteboard.data.*
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit qualification run with the installed TalkBack service, not the default UI suite. */
@RunWith(AndroidJUnit4::class)
class RealTalkBackTest {
    @Test fun talkBackCanToggleSettingsAndManageItsOwnBoard(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(AccessibilityManager::class.java)
        fun requireTalkBack() {
            assertTrue(
                manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any {
                    it.resolveInfo.serviceInfo.packageName == "com.google.android.marvin.talkback"
                }
            )
            assertTrue(manager.isTouchExplorationEnabled)
        }
        requireTalkBack()
        fun nodes(): List<AccessibilityNodeInfo> {
            val found = mutableListOf<AccessibilityNodeInfo>()
            val queue = ArrayDeque<AccessibilityNodeInfo>()
            automation.rootInActiveWindow?.let(queue::add)
            while (queue.isNotEmpty() && found.size < 1000) {
                val current = queue.removeFirst()
                if (!current.refresh()) continue
                found += current
                repeat(current.childCount) { current.getChild(it)?.let(queue::add) }
            }
            return found
        }
        fun find(label: String, scroll: Boolean = false): AccessibilityNodeInfo {
            val until = SystemClock.uptimeMillis() + 10_000
            do {
                val current = nodes()
                current.firstOrNull {
                    it.isVisibleToUser && (it.text?.toString() == label || it.contentDescription?.toString() == label)
                }?.let { return it }
                if (scroll) current.firstOrNull { it.isScrollable }?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                SystemClock.sleep(100)
            } while (SystemClock.uptimeMillis() < until)
            error("TalkBack hierarchy missing $label")
        }
        fun activate(node: AccessibilityNodeInfo) {
            var target = node
            while (!target.isClickable) target = checkNotNull(target.parent) { "No accessible click action" }
            target.refresh()
            if (!target.isAccessibilityFocused) assertTrue(target.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS))
            val until = SystemClock.uptimeMillis() + 5000
            while (target.refresh() && !target.isAccessibilityFocused && SystemClock.uptimeMillis() < until) {
                target.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
                SystemClock.sleep(50)
            }
            assertTrue("Missing TalkBack focus before activation", target.isAccessibilityFocused)
            assertTrue(target.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            instrumentation.waitForIdleSync()
        }
        fun switch(label: String, scroll: Boolean = false): AccessibilityNodeInfo {
            var node = find(label, scroll)
            // The framework may retain a label child underneath the merged Compose switch.
            while (!node.isCheckable) node = checkNotNull(node.parent) { "$label has no checked state" }
            return node
        }
        fun enter(value: String) {
            val until = SystemClock.uptimeMillis() + 5000
            var input: AccessibilityNodeInfo? = null
            do {
                input = nodes().firstOrNull { it.isEditable && it.isVisibleToUser }
                if (input == null) SystemClock.sleep(50)
            } while (input == null && SystemClock.uptimeMillis() < until)
            val arguments = android.os.Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
            }
            assertTrue(checkNotNull(input).performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments))
            instrumentation.waitForIdleSync()
        }
        val preferences = AppSettings(context)
        val original = preferences.values.first()
        val fixture = "TalkBack ${newId()}"
        val labels = listOf(
            "Draw with finger",
            "Recognize hand-drawn shapes",
            "Snap to alignment guides",
            "Automatically save completed edits"
        )
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                if (!original.onboarded) activate(find("Start creating"))
                activate(find("Settings"))
                labels.forEach { label ->
                    val node = switch(label, scroll = true)
                    assertTrue("$label must expose its checked state", node.isCheckable)
                    assertTrue("$label must expose a click action", node.isClickable)
                    val before = node.isChecked
                    if (!node.isAccessibilityFocused) assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS))
                    val focusUntil = SystemClock.uptimeMillis() + 5000
                    while (node.refresh() && !node.isAccessibilityFocused && SystemClock.uptimeMillis() < focusUntil) {
                        node.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
                        SystemClock.sleep(50)
                    }
                    assertTrue("TalkBack focus missing on $label", node.isAccessibilityFocused)
                    activate(node)
                    val until = SystemClock.uptimeMillis() + 5000
                    while (switch(label).isChecked == before && SystemClock.uptimeMillis() < until) SystemClock.sleep(50)
                    assertEquals("Toggle did not update $label", !before, switch(label).isChecked)
                    requireTalkBack()
                }
                activate(find("Done"))
                activate(find("New board"))
                find("Board name")
                enter(fixture)
                activate(find("Confirm"))
                activate(find("Back to boards"))
                enter(fixture.substringAfter("TalkBack "))
                find("Search results")
                find(fixture)
                val star = switch("Toggle favorite")
                assertFalse(star.isChecked)
                activate(star)
                val favoriteUntil = SystemClock.uptimeMillis() + 5000
                while (!switch("Toggle favorite").isChecked && SystemClock.uptimeMillis() < favoriteUntil) SystemClock.sleep(50)
                assertTrue(switch("Toggle favorite").isChecked)
                activate(find("Actions for $fixture"))
                activate(find("Move to Trash"))
                activate(find("Trash"))
                activate(find("Actions for $fixture"))
                activate(find("Restore"))
                activate(find("All boards"))
                find(fixture)
                activate(find("Actions for $fixture"))
                activate(find("Move to Trash"))
                activate(find("Trash"))
                activate(find("Actions for $fixture"))
                activate(find("Delete permanently?"))
                activate(find("Cancel"))
                find(fixture)
                activate(find("Actions for $fixture"))
                activate(find("Delete permanently?"))
                activate(find("Delete"))
                val deletedUntil = SystemClock.uptimeMillis() + 5000
                while (nodes().any { it.text?.toString() == fixture } && SystemClock.uptimeMillis() < deletedUntil) SystemClock.sleep(50)
                assertFalse(nodes().any { it.text?.toString() == fixture })
                requireTalkBack()
            }
            File(context.getExternalFilesDir(null), "qualification-talkback-settings.json").writeText(
                JSONObject().put("service", "com.google.android.marvin.talkback").put("touchExploration", true)
                    .put("namedFocusedAndToggledSwitches", org.json.JSONArray(labels)).put("api", android.os.Build.VERSION.SDK_INT)
                    .put("boardJourney", "create, search, favorite state, trash, restore, cancel deletion, permanent deletion")
                    .put("spokenAudioReviewed", false).toString(2)
            )
        } finally {
            preferences.fingerDrawing(original.fingerDrawing)
            preferences.smartShapes(original.smartShapes)
            preferences.snap(original.snap)
            preferences.autosave(original.autosave)
            val database = Room.databaseBuilder(context, WhiteboardDatabase::class.java, "whiteboard.db")
                .addMigrations(DatabaseMigrations.FROM_1_TO_2, DatabaseMigrations.FROM_2_TO_3, DatabaseMigrations.FROM_3_TO_4).build()
            try {
                val repository = LocalBoardRepository(database)
                repository.observeBoards().first().filter { it.name == fixture }.forEach { repository.deletePermanently(it.id) }
            } finally {
                database.close()
            }
        }
    }

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
