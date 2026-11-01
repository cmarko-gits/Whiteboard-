package dev.whiteboard.canvas

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.view.View
import android.view.accessibility.AccessibilityEvent
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper
import dev.whiteboard.core.model.*
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

data class CanvasAccessibilitySnapshot(val pageId: String, val elements: List<Element>, val viewport: Viewport, val selected: Set<String>)

/** Bounded visible-node window; the Objects dialog provides access to the entire page. */
internal class CanvasAccessibility(
    private val host: View,
    private val state: () -> CanvasAccessibilitySnapshot,
    private val action: (Int, Element?) -> Boolean
) : ExploreByTouchHelper(host) {
    private var dirty = true
    private var sequence = 0
    private var nodes = linkedMapOf<Int, Element>()
    private val ids = mutableMapOf<String, Int>()
    private var lastPageId: String? = null
    private var lastElements: List<Element>? = null
    private val focusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF315D4C.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3 * host.resources.displayMetrics.density
    }

    fun changed() {
        dirty = true
        invalidateRoot()
    }

    private fun refresh() {
        val snapshot = state()
        if (!dirty && lastPageId == snapshot.pageId && lastElements === snapshot.elements) return
        dirty = false
        if (lastPageId != snapshot.pageId) ids.clear()
        lastPageId = snapshot.pageId
        lastElements = snapshot.elements
        val visible = Bounds.between(
            snapshot.viewport.toDocument(Point(0f, 0f)),
            snapshot.viewport.toDocument(Point(host.width.toFloat(), host.height.toFloat()))
        )
        val elements = snapshot.elements.asReversed().asSequence()
            .filter { it.renderBounds.intersects(visible) }.take(MAX_VISIBLE_NODES).toList().asReversed()
        val retained = elements.mapTo(mutableSetOf()) { it.id }
        ids.keys.retainAll(retained)
        nodes = elements.associateByTo(linkedMapOf()) { element -> ids.getOrPut(element.id) { ++sequence } }
        val focused = accessibilityFocusedVirtualViewId
        if (focused != INVALID_ID && focused !in nodes) {
            getAccessibilityNodeProvider(host).performAction(focused, AccessibilityNodeInfoCompat.ACTION_CLEAR_ACCESSIBILITY_FOCUS, null)
        }
        val keyboard = keyboardFocusedVirtualViewId
        if (keyboard != INVALID_ID && keyboard !in nodes) clearKeyboardFocusForVirtualView(keyboard)
    }

    private fun rectangle(element: Element): Rect {
        val viewport = state().viewport
        val start = viewport.toScreen(Point(element.renderBounds.left, element.renderBounds.top))
        val end = viewport.toScreen(Point(element.renderBounds.right, element.renderBounds.bottom))
        val minimum = 48 * host.resources.displayMetrics.density
        val width = max(end.x - start.x, minimum)
        val height = max(end.y - start.y, minimum)
        val x = (start.x + end.x) / 2
        val y = (start.y + end.y) / 2
        return Rect(
            floor(x - width / 2).toInt().coerceIn(0, host.width),
            floor(y - height / 2).toInt().coerceIn(0, host.height),
            ceil(x + width / 2).toInt().coerceIn(0, host.width),
            ceil(y + height / 2).toInt().coerceIn(0, host.height)
        )
    }

    override fun getVirtualViewAt(x: Float, y: Float): Int {
        refresh()
        return nodes.entries.reversed().firstOrNull { rectangle(it.value).contains(x.toInt(), y.toInt()) }?.key ?: INVALID_ID
    }

    override fun getVisibleVirtualViews(virtualViewIds: MutableList<Int>) {
        refresh()
        virtualViewIds.addAll(nodes.keys)
    }

    override fun onPopulateNodeForVirtualView(virtualViewId: Int, node: AccessibilityNodeInfoCompat) {
        refresh()
        val element = nodes[virtualViewId]
        node.className = "android.view.View"
        if (element == null) {
            node.contentDescription = host.context.getString(R.string.canvas_object_unavailable)
            node.setBoundsInParent(Rect(0, 0, 1, 1))
            node.isEnabled = false
            return
        }
        node.contentDescription = describeCanvasElement(host.context, element)
        node.setBoundsInParent(rectangle(element))
        node.isSelected = element.id in state().selected
        node.isClickable = true
        node.addAction(
            AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                AccessibilityNodeInfoCompat.ACTION_CLICK,
                host.context.getString(R.string.canvas_select)
            )
        )
        if (element is TextElement || element is Note) {
            node.isLongClickable = true
            node.addAction(
                AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                    AccessibilityNodeInfoCompat.ACTION_LONG_CLICK,
                    host.context.getString(R.string.canvas_edit)
                )
            )
        }
        listOf(
            R.id.canvas_move_left to R.string.canvas_move_left,
            R.id.canvas_move_right to R.string.canvas_move_right,
            R.id.canvas_move_up to R.string.canvas_move_up,
            R.id.canvas_move_down to R.string.canvas_move_down,
            R.id.canvas_grow to R.string.canvas_grow,
            R.id.canvas_shrink to R.string.canvas_shrink,
            R.id.canvas_delete to R.string.canvas_delete
        ).forEach { (id, label) ->
            node.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat(id, host.context.getString(label)))
        }
    }

    override fun onPerformActionForVirtualView(virtualViewId: Int, action: Int, arguments: Bundle?): Boolean {
        refresh()
        val element = nodes[virtualViewId] ?: return false
        if (!this.action(action, element)) return false
        changed()
        sendEventForVirtualView(virtualViewId, AccessibilityEvent.TYPE_VIEW_SELECTED)
        return true
    }

    override fun onPopulateNodeForHost(node: AccessibilityNodeInfoCompat) {
        super.onPopulateNodeForHost(node)
        node.isClickable = true
        node.addAction(
            AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                AccessibilityNodeInfoCompat.ACTION_CLICK,
                host.context.getString(R.string.canvas_activate)
            )
        )
        listOf(
            R.id.canvas_browse to R.string.canvas_browse,
            R.id.canvas_add_text to R.string.canvas_add_text,
            R.id.canvas_add_note to R.string.canvas_add_note,
            R.id.canvas_zoom_in to R.string.canvas_zoom_in,
            R.id.canvas_zoom_out to R.string.canvas_zoom_out,
            R.id.canvas_fit to R.string.canvas_fit
        ).forEach { (id, label) ->
            node.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat(id, host.context.getString(label)))
        }
        node.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SCROLL_LEFT)
        node.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SCROLL_RIGHT)
        node.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SCROLL_UP)
        node.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SCROLL_DOWN)
        node.isScrollable = true
        node.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SCROLL_FORWARD)
        node.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SCROLL_BACKWARD)
    }

    override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean =
        this.action(action, null) || super.performAccessibilityAction(host, action, args)

    override fun onVirtualViewKeyboardFocusChanged(virtualViewId: Int, hasFocus: Boolean) {
        host.invalidate()
    }

    fun drawFocus(canvas: Canvas) {
        if (keyboardFocusedVirtualViewId == INVALID_ID) return
        refresh()
        nodes[keyboardFocusedVirtualViewId]?.let { canvas.drawRect(rectangle(it), focusPaint) }
    }

    companion object {
        private const val MAX_VISIBLE_NODES = 256
    }
}

/** Shared spoken labels for native virtual nodes and the complete Compose object navigator. */
fun describeCanvasElement(context: Context, element: Element): String = when (element) {
    is Note -> context.getString(R.string.canvas_note, element.text.take(160))
    is TextElement -> context.getString(R.string.canvas_text, element.text.take(160))
    is Stroke -> context.getString(R.string.canvas_stroke)
    is Line -> context.getString(if (element.arrow) R.string.canvas_arrow else R.string.canvas_line)
    is Shape -> context.getString(
        when (element.kind) {
            Tool.RECTANGLE -> R.string.canvas_rectangle
            Tool.SQUARE -> R.string.canvas_square
            Tool.ELLIPSE -> R.string.canvas_ellipse
            Tool.CIRCLE -> R.string.canvas_circle
            Tool.TRIANGLE -> R.string.canvas_triangle
            else -> R.string.canvas_diamond
        }
    )
    is MediaElement -> context.getString(if (element.pdfPage == null) R.string.canvas_image else R.string.canvas_pdf)
    is ElementGroup -> context.resources.getQuantityString(R.plurals.canvas_group, element.children.size, element.children.size)
}
