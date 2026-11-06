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

data class CanvasAccessibilitySnapshot(val page: Page, val viewport: Viewport, val selected: Set<String>) {
    val pageId get() = page.id
    val elements get() = page.visibleElements
}

/** Bounded visible-node window; the Objects dialog provides access to the entire page. */
internal class CanvasAccessibility(
    private val host: View,
    private val state: () -> CanvasAccessibilitySnapshot,
    private val action: (Int, PageObject?) -> Boolean,
    private val objectMenuAvailable: () -> Boolean = { false }
) : ExploreByTouchHelper(host) {
    private var dirty = true
    private var sequence = 0
    private var nodes = linkedMapOf<Int, PageObject>()
    private val ids = mutableMapOf<String, Int>()
    private var lastPageId: String? = null
    private var lastElements: List<PageObject>? = null
    private val focusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
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
            .filter { snapshot.page.geometry.renderBounds(it).intersects(visible) }.take(MAX_VISIBLE_NODES).toList().asReversed()
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

    private fun rectangle(element: PageObject): Rect {
        val snapshot = state()
        val viewport = snapshot.viewport
        val bounds = snapshot.page.geometry.renderBounds(element)
        val start = viewport.toScreen(Point(bounds.left, bounds.top))
        val end = viewport.toScreen(Point(bounds.right, bounds.bottom))
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
        if (element.content() is TextElement || element.content() is Note) {
            node.isLongClickable = true
            node.addAction(
                AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                    AccessibilityNodeInfoCompat.ACTION_LONG_CLICK,
                    host.context.getString(R.string.canvas_edit)
                )
            )
        }
        if (objectMenuAvailable()) {
            node.addAction(
                AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                    R.id.canvas_object_actions,
                    host.context.getString(R.string.canvas_object_actions)
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
        ).filter { it.first == R.id.canvas_delete || element.canTransformIndependently }.forEach { (id, label) ->
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
        nodes[keyboardFocusedVirtualViewId]?.let {
            val bounds = rectangle(it)
            // Two adjacent tones remain visible over arbitrary user-selected canvas colors.
            focusPaint.color = android.graphics.Color.BLACK
            focusPaint.strokeWidth = 6 * host.resources.displayMetrics.density
            canvas.drawRect(bounds, focusPaint)
            focusPaint.color = android.graphics.Color.WHITE
            focusPaint.strokeWidth = 2 * host.resources.displayMetrics.density
            canvas.drawRect(bounds, focusPaint)
        }
    }

    companion object {
        private const val MAX_VISIBLE_NODES = 256
    }
}

/** Shared spoken labels for native virtual nodes and the complete Compose object navigator. */
fun describeCanvasElement(context: Context, element: PageObject): String = when (element) {
    is AffineElement -> describeCanvasElement(context, element.source)
    is Note -> context.getString(R.string.canvas_note, element.text.take(160))
    is TextElement -> context.getString(R.string.canvas_text, element.text.take(160))
    is Stroke -> context.getString(R.string.canvas_stroke)
    is Line -> context.getString(if (element.arrow) R.string.canvas_arrow else R.string.canvas_line)
    is Connector -> context.getString(if (element.arrow) R.string.canvas_connection_arrow else R.string.canvas_connection_line)
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
