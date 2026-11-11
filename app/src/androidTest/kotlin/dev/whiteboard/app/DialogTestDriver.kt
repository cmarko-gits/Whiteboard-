package dev.whiteboard.app

import android.app.UiAutomation
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import java.util.Collections
import java.util.IdentityHashMap

private data class DialogProbe(val found: Boolean, val selected: Boolean = false, val scroll: Rect? = null, val enabled: Boolean = false)

/** Read a new connected hierarchy; retain values rather than UiObject2 handles across recomposition. */
@Suppress("DEPRECATION")
private fun inspectDialogControl(
    label: String,
    activate: Boolean,
    byDescription: Boolean = false,
    automation: UiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation,
    expectedPackage: String = InstrumentationRegistry.getInstrumentation().targetContext.packageName,
    skipSelected: Boolean = true
): DialogProbe {
    val root = automation.rootInActiveWindow ?: return DialogProbe(false)
    val owned = Collections.newSetFromMap(IdentityHashMap<AccessibilityNodeInfo, Boolean>())
    fun own(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? = node?.also { owned.add(it) }
    val queue = ArrayDeque<AccessibilityNodeInfo>()
    own(root)?.let(queue::add)
    var scroll: Rect? = null
    var remaining = 2000
    try {
        if (root.packageName?.toString() != expectedPackage) return DialogProbe(false)
        while (queue.isNotEmpty() && remaining-- > 0) {
            val node = queue.removeFirst()
            if (!node.refresh()) continue
            if (node.isScrollable && node.isVisibleToUser) {
                val bounds = Rect().also(node::getBoundsInScreen)
                if (!bounds.isEmpty) scroll = bounds
            }
            val value = if (byDescription) node.contentDescription else node.text
            if (value?.toString() == label && node.isVisibleToUser) {
                var owner: AccessibilityNodeInfo? = node
                // Compose can remove ACTION_CLICK from a selected radio row. Its checked state
                // still belongs to that row; climbing only to a clickable ancestor loses it.
                while (owner != null && !owner.isClickable && !owner.isCheckable) owner = own(owner.parent)
                if (activate) {
                    // Text injection can precede the recomposition that enables Apply. Wait on a
                    // fresh hierarchy without scrolling away from the already visible control.
                    if (owner?.refresh() != true || !owner.isEnabled) return DialogProbe(false)
                    if ((!skipSelected || !owner.isChecked && !owner.isSelected) &&
                        !owner.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    ) {
                        return DialogProbe(false)
                    }
                }
                return DialogProbe(true, owner?.isChecked == true || owner?.isSelected == true, scroll, owner?.isEnabled == true)
            }
            repeat(node.childCount) { own(node.getChild(it))?.let(queue::add) }
        }
        return DialogProbe(false, scroll = scroll)
    } finally {
        owned.forEach { it.recycle() }
    }
}

/** Same connected-node path as native tests; the release client must name its active package. */
internal fun activateConnectedUiControl(
    automation: UiAutomation,
    expectedPackage: String,
    label: String,
    byDescription: Boolean = false
): Boolean = inspectDialogControl(label, true, byDescription, automation, expectedPackage, skipSelected = false).found

/** Wait for a connected target, but never replay a successful semantic action. */
internal fun clickConnectedUiControl(automation: UiAutomation, expectedPackage: String, label: String, byDescription: Boolean = false) {
    val deadline = SystemClock.uptimeMillis() + 10_000
    while (!activateConnectedUiControl(automation, expectedPackage, label, byDescription)) {
        check(SystemClock.uptimeMillis() < deadline) { "Connected control unavailable in $expectedPackage: $label" }
        SystemClock.sleep(50)
    }
}

/** Small overlapping physical swipes avoid API 26 accessibility page-scroll skipping a whole row. */
private fun reachDialogText(device: UiDevice, label: String, activate: Boolean, towardTop: Boolean) {
    val deadline = SystemClock.uptimeMillis() + 20_000
    var swipes = 0
    while (SystemClock.uptimeMillis() < deadline) {
        val probe = inspectDialogControl(label, activate)
        if (probe.found) return
        val bounds = probe.scroll
        if (bounds != null) {
            check(++swipes <= 24) { "Dialog text was not reached by bounded scrolling: $label" }
            val start = if (towardTop) 0.3f else 0.8f
            val end = if (towardTop) 0.65f else 0.45f
            check(
                device.swipe(
                    bounds.centerX(),
                    bounds.top + (bounds.height() * start).toInt(),
                    bounds.centerX(),
                    bounds.top + (bounds.height() * end).toInt(),
                    60
                )
            ) { "Dialog swipe could not be injected: $label" }
            device.waitForIdle()
        } else {
            SystemClock.sleep(50)
        }
    }
    error("Dialog text did not become available for the requested action: $label")
}

internal fun clickTestDialogText(device: UiDevice, label: String) = reachDialogText(device, label, true, false)

/** Activate the connected semantic owner while IME/layout changes invalidate screen coordinates. */
internal fun clickTestDialogDescription(label: String) {
    val deadline = SystemClock.uptimeMillis() + 10_000
    while (SystemClock.uptimeMillis() < deadline) {
        if (inspectDialogControl(label, activate = true, byDescription = true).found) return
        SystemClock.sleep(50)
    }
    error("Dialog description did not become available for activation: $label")
}

internal fun revealTestDialogText(device: UiDevice, label: String, towardTop: Boolean = false) =
    reachDialogText(device, label, false, towardTop)
internal fun testDialogChoiceSelected(label: String): Boolean = inspectDialogControl(label, false).selected
internal fun testDialogActionEnabled(label: String, byDescription: Boolean = false): Boolean =
    inspectDialogControl(label, false, byDescription).let {
        check(it.found) { "Dialog action is not visible: $label" }
        it.enabled
    }
