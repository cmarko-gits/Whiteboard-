package dev.whiteboard.app

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.util.Collections
import java.util.IdentityHashMap

/** Synchronizes fixture navigation with the visible library, including legacy tablet IMEs. */
internal fun launchTestLibrary(device: UiDevice) {
    val context = ApplicationProvider.getApplicationContext<Context>()
    InstrumentationRegistry.getInstrumentation().startActivitySync(
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    )
    val deadline = SystemClock.uptimeMillis() + 10_000
    while (!device.hasObject(By.desc("New board")) && SystemClock.uptimeMillis() < deadline) {
        device.findObject(By.text("Start creating"))?.click()
        device.waitForIdle(250)
    }
    check(device.hasObject(By.desc("New board"))) { "Library did not become visible" }
    hideTestKeyboard(device)
    testLibraryCreateButton(device)
}

/** The icon can be visible/enabled while its containing Material button is still loading. */
internal fun testLibraryCreateButton(device: UiDevice, description: String = "New board"): UiObject2 {
    val deadline = SystemClock.uptimeMillis() + 10_000
    while (SystemClock.uptimeMillis() < deadline) {
        try {
            var target = device.findObject(By.desc(description))
            while (target != null && !target.isClickable) target = target.parent
            if (target?.isEnabled == true) return target
        } catch (_: StaleObjectException) {
            // Re-read the current hierarchy while the Activity/library finishes loading.
        }
        SystemClock.sleep(50)
    }
    error("Library create button did not become interactive")
}

internal fun openTestBoard(device: UiDevice, name: String) {
    checkNotNull(device.wait(Until.findObject(By.desc("New board")), 10_000)) { "Library is not open" }
    searchTestLibrary(device, name)
    testBoardCard(device, name).click()
    check(device.wait(Until.hasObject(By.desc("More actions")), 10_000)) { "Editor did not open: $name" }
}

internal fun testBoardCard(device: UiDevice, name: String) =
    checkNotNull(device.wait(Until.findObject(By.text(name).clazz("android.widget.TextView")), 10_000)) { "Missing board card: $name" }

/** Match the dialog's labeled input; a library search field may still exist underneath it. */
internal fun testDialogField(device: UiDevice, label: String, resourceId: String? = null): UiObject2 {
    val selector = if (resourceId == null) {
        By.clazz("android.widget.EditText").hasDescendant(By.text(label))
    } else {
        By.res(resourceId).clazz("android.widget.EditText")
    }
    val deadline = SystemClock.uptimeMillis() + 10_000
    while (SystemClock.uptimeMillis() < deadline) {
        refreshTestInputHierarchy()
        try {
            device.findObject(selector)?.let { return it }
        } catch (_: StaleObjectException) {
            // The connected field can be replaced while a rotated dialog is being laid out.
        }
        SystemClock.sleep(50)
    }
    error("Missing labeled dialog input: $label")
}

/** API 26 can retain pre-rotation label descendants even after the visible window settles. */
@Suppress("DEPRECATION")
private fun refreshTestInputHierarchy() {
    val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
    if (Build.VERSION.SDK_INT <= 28) {
        // Reapplying the same public service info invalidates the framework's whole
        // node cache, including nonactive dialog roots after a legacy rotation.
        // Preserve all existing flags; refreshing only the IME root is insufficient.
        automation.serviceInfo = checkNotNull(automation.serviceInfo)
    }
    val root = automation.rootInActiveWindow ?: return
    val owned = Collections.newSetFromMap(IdentityHashMap<AccessibilityNodeInfo, Boolean>())
    val queue = ArrayDeque<AccessibilityNodeInfo>()
    owned.add(root)
    queue.add(root)
    var remaining = 2000
    try {
        while (queue.isNotEmpty() && remaining-- > 0) {
            val node = queue.removeFirst()
            if (!node.refresh()) continue
            repeat(node.childCount) {
                node.getChild(it)?.let { child ->
                    if (owned.add(child)) queue.add(child)
                }
            }
        }
    } finally {
        owned.forEach { it.recycle() }
    }
}

/** Observe only; do not press Back and hide an application's input-session regression. */
internal fun awaitTestKeyboardHidden(device: UiDevice) {
    val shown = Regex("\\bmInputShown=true\\b")
    val deadline = SystemClock.uptimeMillis() + 10_000
    while (shown.containsMatchIn(device.executeShellCommand("dumpsys input_method")) && SystemClock.uptimeMillis() < deadline) {
        SystemClock.sleep(50)
    }
    check(!shown.containsMatchIn(device.executeShellCommand("dumpsys input_method"))) { "Application keyboard did not close" }
}

internal fun searchTestLibrary(device: UiDevice, query: String) {
    val search = checkNotNull(device.wait(Until.findObject(By.clazz("android.widget.EditText")), 5_000))
    search.text = query
    check(device.wait(Until.hasObject(By.desc("Search results")), 10_000)) { "Search did not finish" }
    hideTestKeyboard(device)
}

private fun hideTestKeyboard(device: UiDevice) {
    val shown = Regex("\\bmInputShown=true\\b")
    if (!shown.containsMatchIn(device.executeShellCommand("dumpsys input_method"))) {
        device.waitForIdle()
        return
    }
    device.pressBack()
    val deadline = SystemClock.uptimeMillis() + 5_000
    while (shown.containsMatchIn(device.executeShellCommand("dumpsys input_method")) && SystemClock.uptimeMillis() < deadline) {
        SystemClock.sleep(50)
    }
    check(!shown.containsMatchIn(device.executeShellCommand("dumpsys input_method"))) { "Library keyboard did not close" }
    device.waitForIdle()
}
