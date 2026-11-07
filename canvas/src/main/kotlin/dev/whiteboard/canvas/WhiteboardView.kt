package dev.whiteboard.canvas

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import dev.whiteboard.core.editor.PartialStrokeEraser
import dev.whiteboard.core.editor.SnapEngine
import dev.whiteboard.core.editor.StrokeCut
import dev.whiteboard.core.editor.StrokeEraser
import dev.whiteboard.core.model.*
import kotlin.math.*

sealed interface CanvasEvent {
    data class Add(val element: PageObject) : CanvasEvent
    data class Replace(val elements: List<PageObject>) : CanvasEvent
    data class TransformBy(val id: String, val translation: Point, val factor: Float) : CanvasEvent
    data class Delete(val ids: Set<String>) : CanvasEvent
    data class EraseStrokes(val ids: Set<String>) : CanvasEvent
    data class EraseParts(val cuts: List<StrokeCut>) : CanvasEvent
    data class Rejected(val error: DocumentError) : CanvasEvent
    data object ConnectionTargetMissing : CanvasEvent
    data class Select(val ids: Set<String>) : CanvasEvent
    data class EditText(val position: Point, val existing: Element? = null, val note: Boolean = false) : CanvasEvent
    data class Camera(val viewport: Viewport) : CanvasEvent
    data class Fill(val position: Point, val color: Int) : CanvasEvent
}

/** Native input/rendering adapter. No database access and no per-sample Compose state updates. */
class WhiteboardView(context: Context, assets: AssetProvider? = null) : View(context) {
    private val asyncAssets = assets?.let { AsyncAssetProvider(it) { invalidate() } }
    private val renderer = BoardRenderer(asyncAssets)
    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var page = Page()
    private var tool = Tool.PEN
    private var ink = InkStyle()
    private var selectedIds = emptySet<String>()
    private var viewport = Viewport()
    private var down: Point? = null
    private var fillTap = false
    private var previousScreen: Point? = null
    private var activePointer = -1
    private var stylus = false
    private var navigating = false
    private var dragOriginal = emptyList<PageObject>()
    private var selectionArea: Bounds? = null
    private var selectionPreview = emptyList<PageObject>()
        set(value) {
            val next = if (value.isEmpty()) null else page.geometry.preview(value)
            field = value
            previewGeometry = next
            canvasAccessibility?.changed()
        }
    private var previewGeometry: PageGeometry? = null
    private var resizing = false
    private var preview: Element? = null
    private var connectionStartId: String? = null
    private var connectionEndPoint: Point? = null
    private val samples = mutableListOf<Point>()
    private val activePath = Path()
    private val erased = mutableSetOf<String>()
    private val partialFragments = linkedMapOf<String, List<Stroke>>()
    private var partialSource: Page? = null
    private var partialLeafCount = 0
    private var eraserRejected = false
    private var previousErase: Point? = null
    var fingerDrawing = true
    var snapEnabled = false
    var eraserMode = EraserMode.STROKE
        set(value) {
            if (field != value) {
                clearGesture()
                field = value
            }
        }
    private var snapCandidates = emptyList<Bounds>()
    private var guideX: Float? = null
    private var guideY: Float? = null
    var onEvent: (CanvasEvent) -> Unit = {}
    var onBrowseObjects: () -> Unit = {}
    var onCreateConnection: () -> Unit = {}
    var onObjectMenu: ((Set<String>) -> Unit)? = null
    private data class PendingObjectMenu(val id: String, val screen: Point)
    private var pendingObjectMenu: PendingObjectMenu? = null
    private val openLongPressMenu = Runnable {
        val pending = pendingObjectMenu
        if (pending != null && activePointer >= 0 && tool == Tool.SELECT && !navigating && !resizing &&
            dragOriginal.any { it.id == pending.id }
        ) {
            openObjectMenu(dragOriginal.map { it.id }.toSet())
        }
    }
    var accessibilitySnapshot: ((Viewport) -> CanvasAccessibilitySnapshot?)? = null
    var onAccessibilityEvent: ((CanvasEvent) -> Unit)? = null
    private var canvasAccessibility: CanvasAccessibility? = null
    private var pendingFit = false
    fun requestInitialFit() {
        if (width > 0 && height > 0) fitContent() else pendingFit = true
    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (pendingFit && w > 0 && h > 0) {
            pendingFit = false
            fitContent()
        }
        canvasAccessibility?.changed()
    }
    private val density = resources.displayMetrics.density
    fun documentCenter(): Point = viewport.toDocument(Point(width / 2f, height / 2f))
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (stylus) return false
                viewport = viewport.zoom(detector.scaleFactor, Point(detector.focusX, detector.focusY))
                cameraChanged()
                return true
            }
        }
    )

    init {
        // Virtual objects draw their own focus ring; a platform foreground would tint the artwork.
        defaultFocusHighlightEnabled = false
        isFocusable = true
        isFocusableInTouchMode = true
        isClickable = true
        contentDescription = context.getString(R.string.canvas_description)
        canvasAccessibility = CanvasAccessibility(this, {
            previewGeometry?.let { CanvasAccessibilitySnapshot(it.page, viewport, selectedIds) }
                ?: accessibilitySnapshot?.invoke(viewport) ?: CanvasAccessibilitySnapshot(page, viewport, selectedIds)
        }, ::performCanvasAccessibilityAction, { onObjectMenu != null })
        ViewCompat.setAccessibilityDelegate(this, canvasAccessibility)
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        asyncAssets?.start()
    }
    override fun onDetachedFromWindow() {
        clearGesture()
        asyncAssets?.close()
        super.onDetachedFromWindow()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) clearGesture()
    }

    fun update(page: Page, tool: Tool, ink: InkStyle, selectedIds: Set<String>, restoredViewport: Viewport? = null) {
        val accessibilityChanged = this.page !== page || this.selectedIds != selectedIds || this.tool != tool
        if (this.page.id != page.id) {
            viewport = restoredViewport ?: page.viewport
            clearGesture()
        } else if (activePointer >= 0 &&
            (
                !this.page.sharesSceneWith(page) || this.tool != tool ||
                    pendingObjectMenu != null && selectedIds != dragOriginal.map { it.id }.toSet()
                )
        ) {
            clearGesture()
        }
        this.page = page
        this.tool = tool
        this.ink = ink
        this.selectedIds = page.mindMapIndex?.let { index -> selectedIds.filterTo(HashSet(), index::isVisible) } ?: selectedIds
        if (accessibilityChanged) canvasAccessibility?.changed()
        invalidate()
    }
    fun revealElement(id: String) {
        val element = page.visibleElements.firstOrNull { it.id == id } ?: return
        revealBounds(page.geometry.renderBounds(element))
    }
    fun revealBounds(bounds: Bounds) {
        viewport = Viewport.fit(bounds, width.toFloat(), height.toFloat(), 48 * density)
        cameraChanged()
    }
    fun fitContent() {
        viewport = Viewport.fit(page.visibleContentBounds, width.toFloat(), height.toFloat(), 36 * density)
        cameraChanged()
    }
    fun resetCamera() {
        viewport = Viewport()
        cameraChanged()
    }
    fun zoomBy(factor: Float) {
        viewport = viewport.zoom(factor, Point(width / 2f, height / 2f))
        cameraChanged()
    }
    private fun cameraChanged(accessibility: Boolean = false) {
        val event = CanvasEvent.Camera(viewport)
        if (accessibility) emitAccessibilityEvent(event) else onEvent(event)
        canvasAccessibility?.changed()
        invalidate()
    }

    override fun dispatchHoverEvent(event: MotionEvent): Boolean =
        canvasAccessibility?.dispatchHoverEvent(event) == true || super.dispatchHoverEvent(event)

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 &&
            (event.keyCode == KeyEvent.KEYCODE_MENU || event.keyCode == KeyEvent.KEYCODE_F10 && event.isShiftPressed) &&
            showContextMenu()
        ) {
            return true
        }
        if ((tool == Tool.SELECT || event.keyCode == KeyEvent.KEYCODE_TAB) &&
            canvasAccessibility?.dispatchKeyEvent(event) == true
        ) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun showContextMenu(): Boolean = openObjectMenu(selectedIds)

    override fun showContextMenu(x: Float, y: Float): Boolean {
        if (!x.isFinite() || !y.isFinite()) return false
        val hit = page.geometry.topmostAt(viewport.toDocument(Point(x, y)), 12 * density / viewport.scale) ?: return false
        return openObjectMenu(if (hit.id in selectedIds) selectedIds else setOf(hit.id))
    }

    private fun openObjectMenu(ids: Set<String>, accessibility: Boolean = false): Boolean {
        val show = onObjectMenu ?: return false
        val scene = if (accessibility) accessibilitySnapshot?.invoke(viewport)?.page ?: page else page
        val valid = scene.visibleElements.filter { it.id in ids }.map { it.id }.toSet()
        if (valid.isEmpty()) return false
        clearGesture()
        if (accessibility) emitAccessibilityEvent(CanvasEvent.Select(valid)) else onEvent(CanvasEvent.Select(valid))
        show(valid)
        return true
    }

    private fun cancelObjectLongPress() {
        removeCallbacks(openLongPressMenu)
        pendingObjectMenu = null
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        if (!gainFocus || !isInTouchMode) canvasAccessibility?.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
    }

    private fun emitAccessibilityEvent(event: CanvasEvent) {
        (onAccessibilityEvent ?: onEvent)(event)
    }

    private fun activateAccessibilityTool(): Boolean {
        performClick()
        val center = viewport.toDocument(Point(width / 2f, height / 2f))
        val extent = 64 * density / viewport.scale
        when (tool) {
            Tool.CONNECTOR -> onCreateConnection()
            Tool.TEXT, Tool.NOTE -> emitAccessibilityEvent(CanvasEvent.EditText(center, note = tool == Tool.NOTE))
            Tool.RECTANGLE, Tool.SQUARE, Tool.ELLIPSE, Tool.CIRCLE, Tool.TRIANGLE, Tool.DIAMOND ->
                emitAccessibilityEvent(
                    CanvasEvent.Add(
                        Shape(
                            bounds = Bounds(center.x - extent, center.y - extent, center.x + extent, center.y + extent),
                            kind = tool,
                            style = ink
                        )
                    )
                )
            Tool.LINE, Tool.ARROW -> emitAccessibilityEvent(
                CanvasEvent.Add(
                    Line(
                        start = Point(center.x - extent, center.y),
                        end = Point(center.x + extent, center.y),
                        style = ink,
                        arrow =
                        tool == Tool.ARROW
                    )
                )
            )
            else -> onBrowseObjects()
        }
        return true
    }

    private fun performCanvasAccessibilityAction(action: Int, element: PageObject?): Boolean {
        if (element == null) {
            val step = 96 * density
            when (action) {
                AccessibilityNodeInfoCompat.ACTION_CLICK -> return activateAccessibilityTool()
                R.id.canvas_browse -> onBrowseObjects()
                R.id.canvas_add_text, R.id.canvas_add_note -> emitAccessibilityEvent(
                    CanvasEvent.EditText(
                        viewport.toDocument(Point(width / 2f, height / 2f)),
                        note =
                        action == R.id.canvas_add_note
                    )
                )
                R.id.canvas_zoom_in, R.id.canvas_zoom_out -> {
                    viewport = viewport.zoom(if (action == R.id.canvas_zoom_in) 1.25f else 0.8f, Point(width / 2f, height / 2f))
                    cameraChanged(accessibility = true)
                }
                R.id.canvas_fit -> {
                    val scene = accessibilitySnapshot?.invoke(viewport)?.page ?: page
                    viewport = Viewport.fit(scene.visibleContentBounds, width.toFloat(), height.toFloat(), 36 * density)
                    cameraChanged(accessibility = true)
                }
                AccessibilityActionCompat.ACTION_SCROLL_LEFT.id -> {
                    viewport = viewport.pan(step, 0f)
                    cameraChanged(accessibility = true)
                }
                AccessibilityActionCompat.ACTION_SCROLL_RIGHT.id -> {
                    viewport = viewport.pan(-step, 0f)
                    cameraChanged(accessibility = true)
                }
                AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD, AccessibilityActionCompat.ACTION_SCROLL_UP.id -> {
                    viewport =
                        viewport.pan(0f, step)
                    cameraChanged(accessibility = true)
                }
                AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD, AccessibilityActionCompat.ACTION_SCROLL_DOWN.id -> {
                    viewport =
                        viewport.pan(0f, -step)
                    cameraChanged(accessibility = true)
                }
                else -> return false
            }
            return true
        }
        when (action) {
            AccessibilityNodeInfoCompat.ACTION_CLICK -> emitAccessibilityEvent(CanvasEvent.Select(setOf(element.id)))
            AccessibilityNodeInfoCompat.ACTION_LONG_CLICK -> {
                if (element !is Element) return false
                val content = element.content()
                if (content !is Note && content !is TextElement) return false
                emitAccessibilityEvent(CanvasEvent.EditText(page.geometry.selectionBounds(element).center, element, content is Note))
            }
            R.id.canvas_delete -> emitAccessibilityEvent(CanvasEvent.Delete(setOf(element.id)))
            R.id.canvas_object_actions -> return openObjectMenu(setOf(element.id), accessibility = true)
            else -> {
                val step = 24 * density / viewport.scale
                val dx = when (action) {
                    R.id.canvas_move_left -> -step
                    R.id.canvas_move_right -> step
                    else -> 0f
                }
                val dy = when (action) {
                    R.id.canvas_move_up -> -step
                    R.id.canvas_move_down -> step
                    else -> 0f
                }
                val factor = when (action) {
                    R.id.canvas_grow -> 1.1f
                    R.id.canvas_shrink -> 1 / 1.1f
                    else -> 1f
                }
                if (!element.canTransformIndependently || (dx == 0f && dy == 0f && factor == 1f)) return false
                emitAccessibilityEvent(CanvasEvent.TransformBy(element.id, Point(dx, dy), factor))
            }
        }
        return true
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_SCROLL ||
            !(event.isFromSource(InputDevice.SOURCE_MOUSE) || event.isFromSource(InputDevice.SOURCE_TOUCHPAD))
        ) {
            return super.onGenericMotionEvent(event)
        }
        val vertical = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
        val horizontal = event.getAxisValue(MotionEvent.AXIS_HSCROLL)
        if (!vertical.isFinite() || !horizontal.isFinite() || !event.x.isFinite() || !event.y.isFinite() ||
            vertical == 0f && horizontal == 0f
        ) {
            return false
        }
        viewport = if (event.metaState and (KeyEvent.META_CTRL_ON or KeyEvent.META_META_ON) != 0) {
            viewport.zoom(exp(vertical.coerceIn(-8f, 8f) * 0.12f), Point(event.x, event.y))
        } else {
            val step = 48f * density
            if (event.metaState and KeyEvent.META_SHIFT_ON !=
                0
            ) {
                viewport.pan(vertical * step, 0f)
            } else {
                viewport.pan(-horizontal * step, vertical * step)
            }
        }
        cameraChanged()
        return true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.hasNoModifiers() && keyCode == KeyEvent.KEYCODE_ESCAPE) {
            clearGesture()
            onEvent(CanvasEvent.Select(emptySet()))
            invalidate()
            return true
        }
        if (event.hasNoModifiers() && tool == Tool.SELECT && selectedIds.isNotEmpty() &&
            keyCode in listOf(KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_FORWARD_DEL)
        ) {
            onEvent(CanvasEvent.Delete(selectedIds.toSet()))
            return true
        }
        if (event.hasNoModifiers() && tool == Tool.PAN) {
            val step = 48f * density
            val delta = when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> Point(step, 0f)
                KeyEvent.KEYCODE_DPAD_RIGHT -> Point(-step, 0f)
                KeyEvent.KEYCODE_DPAD_UP -> Point(0f, step)
                KeyEvent.KEYCODE_DPAD_DOWN -> Point(0f, -step)
                else -> null
            }
            if (delta != null) {
                viewport = viewport.pan(delta.x, delta.y)
                cameraChanged()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val visible = Bounds.between(viewport.toDocument(Point(0f, 0f)), viewport.toDocument(Point(width.toFloat(), height.toFloat())))
        canvas.save()
        canvas.translate(viewport.offset.x, viewport.offset.y)
        canvas.scale(viewport.scale, viewport.scale)
        renderer.drawBackground(canvas, page, visible, viewport.scale)
        val geometry = previewGeometry ?: page.geometry
        geometry.page.visibleElements.forEach {
            if (it.id !in erased &&
                geometry.renderBounds(it).expand(10f).intersects(visible)
            ) {
                it.withoutStrokes(erased)?.let { visibleElement ->
                    if (partialFragments.isEmpty()) {
                        renderer.drawElement(canvas, geometry.resolve(visibleElement))
                    } else {
                        visibleElement.withStrokeFragments(partialFragments).forEach { fragment ->
                            renderer.drawElement(canvas, geometry.resolve(fragment))
                        }
                    }
                }
            }
        }
        renderer.drawLiveStroke(canvas, activePath, samples.firstOrNull(), samples.size == 1, ink, tool)
        preview?.let { renderer.drawElement(canvas, it) }
        val selected = selectionPreview.ifEmpty { page.visibleElements.filter { it.id in selectedIds } }
        val selectedBounds = selected.map { geometry.selectionBounds(it) }.reduceOrNull(Bounds::union)
        (selectionArea ?: selectedBounds)?.let {
            val b = it.expand(6 * density / viewport.scale)
            selectionPaint.color = 0xFF40836C.toInt()
            selectionPaint.style = Paint.Style.STROKE
            selectionPaint.strokeWidth =
                1.5f * density / viewport.scale
            canvas.drawRect(b.left, b.top, b.right, b.bottom, selectionPaint)
            selectionPaint.style = Paint.Style.FILL
            if (selectionArea == null &&
                selected.canTransformTogether()
            ) {
                canvas.drawCircle(b.right, b.bottom, 7 * density / viewport.scale, selectionPaint)
            }
        }
        selectionPaint.color = 0x9983A391.toInt()
        selectionPaint.strokeWidth = 1f / viewport.scale
        guideX?.let { canvas.drawLine(it, visible.top, it, visible.bottom, selectionPaint) }
        guideY?.let { canvas.drawLine(visible.left, it, visible.right, it, selectionPaint) }
        canvas.restore()
        canvasAccessibility?.drawFocus(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean = try {
        handleTouch(event)
    } catch (error: DocumentFailure) {
        clearGesture()
        onEvent(CanvasEvent.Rejected(error.error))
        invalidate()
        true
    } catch (_: IllegalArgumentException) {
        clearGesture()
        onEvent(CanvasEvent.Rejected(DocumentError.EDIT_LIMIT))
        invalidate()
        true
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        if (!stylus) scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancelObjectLongPress()
                requestFocus()
                activePointer = event.getPointerId(0)
                stylus = event.getToolType(0) in setOf(MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.TOOL_TYPE_ERASER)
                navigating = false
                val screen = Point(event.x, event.y)
                previousScreen = screen
                val point = viewport.toDocument(screen)
                down = point
                fillTap = tool == Tool.FILL
                if (!fingerDrawing && !stylus || tool == Tool.PAN) {
                    navigating = true
                    return true
                }
                when (tool) {
                    Tool.CONNECTOR -> {
                        connectionStartId = page.geometry.connectionTargetAt(point, 12 * density / viewport.scale)?.id
                        updateConnectionPreview(point)
                    }
                    Tool.PEN, Tool.PENCIL, Tool.MARKER, Tool.HIGHLIGHTER -> addSample(point)
                    Tool.ERASER -> erase(point)
                    Tool.SELECT -> {
                        val current = page.visibleElements.filter { it.id in selectedIds }
                        val handle = current.map { page.geometry.selectionBounds(it) }
                            .reduceOrNull(Bounds::union)?.expand(6 * density / viewport.scale)
                        resizing =
                            current.canTransformTogether() && handle != null &&
                            hypot(point.x - handle.right, point.y - handle.bottom) < 22 * density / viewport.scale
                        val hit = page.geometry.topmostAt(point, 12 * density / viewport.scale)
                        dragOriginal = when {
                            resizing -> current
                            hit?.id in selectedIds -> current
                            hit != null -> listOf(hit)
                            else -> emptyList()
                        }
                        val ids = dragOriginal.map { it.id }.toSet()
                        val visible = Bounds.between(
                            viewport.toDocument(Point(0f, 0f)),
                            viewport.toDocument(Point(width.toFloat(), height.toFloat()))
                        )
                        snapCandidates =
                            page.visibleElements.filter { it.id !in ids && page.geometry.renderBounds(it).intersects(visible) }
                                .map { page.geometry.selectionBounds(it) }
                        onEvent(CanvasEvent.Select(ids))
                        if (!resizing && hit != null && onObjectMenu != null) {
                            pendingObjectMenu = PendingObjectMenu(hit.id, screen)
                            postDelayed(openLongPressMenu, ViewConfiguration.getLongPressTimeout().toLong())
                        }
                    }
                    else -> Unit
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                cancelObjectLongPress()
                if (tool == Tool.CONNECTOR) {
                    clearGesture()
                    return true
                }
                if (!stylus) {
                    navigating = true
                    samples.clear()
                    activePath.reset()
                    preview = null
                    dragOriginal = emptyList()
                    selectionPreview = emptyList()
                    selectionArea = null
                    guideX = null
                    guideY = null
                    erased.clear()
                    clearPartialErasure()
                    previousErase = null
                    previousScreen = centroid(event)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(activePointer)
                if (index < 0) return true
                pendingObjectMenu?.let { pending ->
                    val moved = hypot(event.getX(index) - pending.screen.x, event.getY(index) - pending.screen.y) > touchSlop ||
                        (0 until event.historySize).any { history ->
                            hypot(
                                event.getHistoricalX(index, history) - pending.screen.x,
                                event.getHistoricalY(index, history) - pending.screen.y
                            ) > touchSlop
                        }
                    if (moved) {
                        cancelObjectLongPress()
                    }
                }
                if (navigating || event.pointerCount > 1 && !stylus) {
                    val next = centroid(event)
                    previousScreen?.let { viewport = viewport.pan(next.x - it.x, next.y - it.y) }
                    previousScreen = next
                    cameraChanged()
                    return true
                }
                val point = viewport.toDocument(Point(event.getX(index), event.getY(index)))
                when (tool) {
                    Tool.CONNECTOR -> updateConnectionPreview(point)
                    Tool.PEN, Tool.PENCIL, Tool.MARKER, Tool.HIGHLIGHTER -> {
                        for (h in 0 until event.historySize) {
                            addSample(
                                viewport.toDocument(Point(event.getHistoricalX(index, h), event.getHistoricalY(index, h)))
                            )
                        }
                        addSample(point)
                    }
                    Tool.ERASER -> erase(point)
                    Tool.SELECT -> {
                        val original = dragOriginal
                        val origin = down ?: return true
                        if (original.isEmpty()) {
                            selectionArea = Bounds.between(origin, point)
                        } else {
                            if (!original.canTransformTogether()) return true
                            val b = original.map { page.geometry.selectionBounds(it) }.reduce(Bounds::union)
                            val target = if (resizing) {
                                Bounds(b.left, b.top, max(b.left + 24f, point.x), max(b.top + 24f, point.y))
                            } else {
                                Bounds(
                                    b.left + point.x - origin.x,
                                    b.top + point.y - origin.y,
                                    b.right + point.x - origin.x,
                                    b.bottom + point.y - origin.y
                                )
                            }
                            val snapped = if (snapEnabled &&
                                !resizing
                            ) {
                                SnapEngine.snap(target, snapCandidates, 8 * density / viewport.scale)
                            } else {
                                null
                            }
                            guideX = snapped?.guideX
                            guideY = snapped?.guideY
                            val final = snapped?.bounds ?: target
                            val movableTargets = if (original.any { it is Connector }) original.allObjectIds() else emptySet()
                            selectionPreview = original.map { element ->
                                val bounds = page.geometry.selectionBounds(element)
                                val topLeft = Point(bounds.left, bounds.top).remap(b, final)
                                val bottomRight = Point(bounds.right, bounds.bottom).remap(b, final)
                                page.geometry.transform(element, Bounds.between(topLeft, bottomRight), movableTargets)
                            }
                        }
                    }
                    Tool.FILL -> if ((down?.let { hypot(point.x - it.x, point.y - it.y) * viewport.scale } ?: 0f) > touchSlop) {
                        fillTap = false
                    }
                    Tool.TEXT, Tool.NOTE, Tool.PAN -> Unit
                    else -> down?.let { preview = createShape(it, point) }
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerId(event.actionIndex) == activePointer) {
                    if (stylus) {
                        consumeRelease(Point(event.getX(event.actionIndex), event.getY(event.actionIndex)))
                        finishGesture()
                        clearGesture()
                        navigating = true
                    } else {
                        activePointer = event.getPointerId(if (event.actionIndex == 0) 1 else 0)
                    }
                }
                previousScreen = null
            }
            MotionEvent.ACTION_UP -> {
                consumeRelease(Point(event.x, event.y))
                if (tool == Tool.FILL) {
                    val up = viewport.toDocument(Point(event.x, event.y))
                    if ((down?.let { hypot(up.x - it.x, up.y - it.y) * viewport.scale } ?: 0f) > touchSlop) fillTap = false
                }
                if (!navigating) finishGesture()
                performClick()
                clearGesture()
            }
            MotionEvent.ACTION_CANCEL -> clearGesture()
        }
        invalidate()
        return true
    }
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
    private fun consumeRelease(screen: Point) {
        if (navigating || activePointer < 0) return
        val point = viewport.toDocument(screen)
        when (tool) {
            Tool.CONNECTOR -> updateConnectionPreview(point)
            Tool.PEN, Tool.PENCIL, Tool.MARKER, Tool.HIGHLIGHTER -> addSample(point, finalSample = true)
            Tool.ERASER -> erase(point)
            Tool.RECTANGLE, Tool.SQUARE, Tool.ELLIPSE, Tool.CIRCLE, Tool.TRIANGLE, Tool.DIAMOND, Tool.LINE, Tool.ARROW ->
                down?.let { preview = createShape(it, point) }
            else -> Unit
        }
    }
    private fun addSample(point: Point, finalSample: Boolean = false) {
        val previous = samples.lastOrNull()
        if (samples.size >= 2_000_000) {
            if (finalSample) samples[samples.lastIndex] = point
            return
        }
        if (previous == null || previous != point &&
            (finalSample || hypot(point.x - previous.x, point.y - previous.y) * viewport.scale >= 0.5f)
        ) {
            if (previous == null) activePath.moveTo(point.x, point.y) else activePath.lineTo(point.x, point.y)
            samples.add(point)
        }
    }
    private fun erase(point: Point) {
        if (eraserRejected) return
        val start = previousErase ?: point
        previousErase = point
        if (eraserMode == EraserMode.PARTIAL) {
            try {
                if (partialSource == null) {
                    partialSource = page
                    partialLeafCount = page.elements.sumOf { it.leaves().size }
                }
                page.elements.flatMap { it.leaves() }.filterIsInstance<Stroke>().forEach { original ->
                    val before = partialFragments[original.id] ?: listOf(original)
                    val after = mutableListOf<Stroke>()
                    before.forEach { fragment ->
                        after.addAll(PartialStrokeEraser.cut(fragment, start, point, 15 * density / viewport.scale))
                        if (after.size > PartialStrokeEraser.MAX_FRAGMENTS) throw DocumentFailure(DocumentError.EDIT_LIMIT)
                    }
                    if (after != before) {
                        val count = partialLeafCount + after.size - before.size
                        if (count > Page.MAX_ELEMENTS) throw DocumentFailure(DocumentError.EDIT_LIMIT)
                        partialFragments[original.id] = after.toList()
                        partialLeafCount = count
                    }
                }
            } catch (error: DocumentFailure) {
                clearPartialErasure()
                eraserRejected = true
                onEvent(CanvasEvent.Rejected(error.error))
            }
            return
        }
        page.elements.flatMap { it.leaves() }.filterIsInstance<Stroke>()
            .filter { it.id !in erased && StrokeEraser.intersects(it, start, point, 15 * density / viewport.scale) }
            .forEach { erased.add(it.id) }
    }
    private fun finishGesture() {
        when (tool) {
            Tool.CONNECTOR -> if (down != null) {
                val point = connectionEndPoint
                val startId = connectionStartId
                val end = point?.let { page.geometry.connectionTargetAt(it, 12 * density / viewport.scale) }
                if (point != null && startId != null && end != null && startId != end.id) {
                    val startPort = page.geometry.closestConnectionPort(startId, point)
                    val anchor = page.geometry.connectionPorts().point(startId, startPort)
                    commitAddition(
                        Connector(
                            start = ConnectorEnd.Bound(startId, startPort),
                            end = ConnectorEnd.Bound(end.id, page.geometry.closestConnectionPort(end.id, anchor)),
                            style = ink
                        )
                    )
                } else {
                    onEvent(CanvasEvent.ConnectionTargetMissing)
                }
            }
            Tool.PEN, Tool.PENCIL, Tool.MARKER, Tool.HIGHLIGHTER -> if (samples.isNotEmpty()) {
                commitAddition(Stroke(points = samples.toList(), style = ink, tool = tool))
            }
            Tool.ERASER -> {
                if (partialFragments.isNotEmpty()) {
                    val cuts = partialSource?.elements.orEmpty().flatMap { it.leaves() }.filterIsInstance<Stroke>().mapNotNull { original ->
                        partialFragments[original.id]?.let { StrokeCut(original, it) }
                    }
                    onEvent(CanvasEvent.EraseParts(cuts))
                } else if (erased.isNotEmpty()) {
                    onEvent(CanvasEvent.EraseStrokes(erased.toSet()))
                }
            }
            Tool.SELECT -> {
                if (selectionPreview.isNotEmpty()) onEvent(CanvasEvent.Replace(selectionPreview))
                selectionArea?.let { area ->
                    val ids = page.visibleElements.filter { page.geometry.renderBounds(it).intersects(area) }.map { it.id }.toSet()
                    onEvent(CanvasEvent.Select(ids))
                }
                val single = dragOriginal.singleOrNull() as? Element
                if (selectionPreview.isEmpty() && (single?.content() is TextElement || single?.content() is Note)) {
                    down?.let { onEvent(CanvasEvent.EditText(it, single, single?.content() is Note)) }
                }
            }
            Tool.TEXT, Tool.NOTE -> down?.let { onEvent(CanvasEvent.EditText(it, note = tool == Tool.NOTE)) }
            Tool.FILL -> if (fillTap) down?.let { onEvent(CanvasEvent.Fill(it, ink.color)) }
            else -> preview?.let {
                val meaningful = if (it is Line) {
                    hypot(it.end.x - it.start.x, it.end.y - it.start.y) >= 2f
                } else {
                    it.bounds.width >= 2 && it.bounds.height >= 2
                }
                if (meaningful) commitAddition(it)
            }
        }
    }
    private fun commitAddition(element: PageObject) {
        if (element is Element) element.renderBounds else page.copy(elements = page.elements + element).contentBounds
        onEvent(CanvasEvent.Add(element))
    }
    private fun updateConnectionPreview(point: Point) {
        connectionEndPoint = point
        val startId = connectionStartId ?: return
        val startPort = page.geometry.closestConnectionPort(startId, point)
        val start = page.geometry.connectionPorts().point(startId, startPort)
        val target = page.geometry.connectionTargetAt(point, 12 * density / viewport.scale)?.takeUnless { it.id == startId }
        val end = if (target == null) {
            point
        } else {
            page.geometry.connectionPorts().point(target.id, page.geometry.closestConnectionPort(target.id, start))
        }
        preview = Line(start = start, end = end, style = ink, arrow = true).also { it.renderBounds }
    }
    private fun createShape(start: Point, end: Point): Element {
        if (tool == Tool.LINE || tool == Tool.ARROW) return Line(start = start, end = end, style = ink, arrow = tool == Tool.ARROW)
        var bounds = Bounds.between(start, end)
        if (tool == Tool.SQUARE || tool == Tool.CIRCLE) {
            val size = max(bounds.width, bounds.height)
            val x = start.x + if (end.x >= start.x) size else -size
            val y = start.y + if (end.y >= start.y) size else -size
            bounds = Bounds.between(start, Point(x, y))
        }
        return Shape(bounds = bounds, kind = tool, style = ink)
    }
    private fun centroid(event: MotionEvent): Point = if (event.pointerCount >
        1
    ) {
        Point((event.getX(0) + event.getX(1)) / 2, (event.getY(0) + event.getY(1)) / 2)
    } else {
        Point(event.x, event.y)
    }
    private fun clearGesture() {
        cancelObjectLongPress()
        clearPartialErasure()
        guideX = null
        guideY = null
        snapCandidates = emptyList()
        samples.clear()
        activePath.reset()
        preview = null
        connectionStartId = null
        connectionEndPoint = null
        dragOriginal = emptyList()
        selectionPreview = emptyList()
        selectionArea = null
        erased.clear()
        previousErase = null
        down = null
        fillTap = false
        previousScreen = null
        activePointer = -1
        navigating = false
        stylus = false
        resizing = false
        invalidate()
    }
    private fun clearPartialErasure() {
        partialFragments.clear()
        partialSource = null
        partialLeafCount = 0
        eraserRejected = false
    }
}
