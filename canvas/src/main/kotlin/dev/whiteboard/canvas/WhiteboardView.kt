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
    data class Add(val element: Element) : CanvasEvent
    data class Replace(val elements: List<Element>) : CanvasEvent
    data class TransformBy(val id: String, val translation: Point, val factor: Float) : CanvasEvent
    data class Delete(val ids: Set<String>) : CanvasEvent
    data class EraseStrokes(val ids: Set<String>) : CanvasEvent
    data class EraseParts(val cuts: List<StrokeCut>) : CanvasEvent
    data class Rejected(val error: DocumentError) : CanvasEvent
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
    private var dragOriginal = emptyList<Element>()
    private var selectionArea: Bounds? = null
    private var selectionPreview = emptyList<Element>()
    private var resizing = false
    private var preview: Element? = null
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
    var accessibilitySnapshot: ((Viewport) -> CanvasAccessibilitySnapshot)? = null
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
            accessibilitySnapshot?.invoke(viewport) ?: CanvasAccessibilitySnapshot(page.id, page.elements, viewport, selectedIds)
        }, ::performCanvasAccessibilityAction)
        ViewCompat.setAccessibilityDelegate(this, canvasAccessibility)
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        asyncAssets?.start()
    }
    override fun onDetachedFromWindow() {
        asyncAssets?.close()
        super.onDetachedFromWindow()
    }

    fun update(page: Page, tool: Tool, ink: InkStyle, selectedIds: Set<String>, restoredViewport: Viewport? = null) {
        val accessibilityChanged = this.page !== page || this.selectedIds != selectedIds || this.tool != tool
        if (this.page.id != page.id) {
            viewport = restoredViewport ?: page.viewport
            clearGesture()
        } else if (activePointer >= 0 && (this.page.elements !== page.elements || this.tool != tool)) {
            clearGesture()
        }
        this.page = page
        this.tool = tool
        this.ink = ink
        this.selectedIds = selectedIds
        if (accessibilityChanged) canvasAccessibility?.changed()
        invalidate()
    }
    fun revealElement(id: String) {
        val element = page.elements.firstOrNull { it.id == id } ?: return
        viewport = Viewport.fit(element.renderBounds, width.toFloat(), height.toFloat(), 48 * density)
        cameraChanged()
    }
    fun fitContent() {
        viewport = Viewport.fit(page.contentBounds, width.toFloat(), height.toFloat(), 36 * density)
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
    private fun cameraChanged() {
        onEvent(CanvasEvent.Camera(viewport))
        canvasAccessibility?.changed()
        invalidate()
    }

    override fun dispatchHoverEvent(event: MotionEvent): Boolean =
        canvasAccessibility?.dispatchHoverEvent(event) == true || super.dispatchHoverEvent(event)

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if ((tool == Tool.SELECT || event.keyCode == KeyEvent.KEYCODE_TAB) &&
            canvasAccessibility?.dispatchKeyEvent(event) == true
        ) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        if (!gainFocus || !isInTouchMode) canvasAccessibility?.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
    }

    private fun activateAccessibilityTool(): Boolean {
        performClick()
        val center = viewport.toDocument(Point(width / 2f, height / 2f))
        val extent = 64 * density / viewport.scale
        when (tool) {
            Tool.TEXT, Tool.NOTE -> onEvent(CanvasEvent.EditText(center, note = tool == Tool.NOTE))
            Tool.RECTANGLE, Tool.SQUARE, Tool.ELLIPSE, Tool.CIRCLE, Tool.TRIANGLE, Tool.DIAMOND ->
                onEvent(
                    CanvasEvent.Add(
                        Shape(
                            bounds = Bounds(center.x - extent, center.y - extent, center.x + extent, center.y + extent),
                            kind = tool,
                            style = ink
                        )
                    )
                )
            Tool.LINE, Tool.ARROW -> onEvent(
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

    private fun performCanvasAccessibilityAction(action: Int, element: Element?): Boolean {
        if (element == null) {
            val step = 96 * density
            when (action) {
                AccessibilityNodeInfoCompat.ACTION_CLICK -> return activateAccessibilityTool()
                R.id.canvas_browse -> onBrowseObjects()
                R.id.canvas_add_text, R.id.canvas_add_note -> onEvent(
                    CanvasEvent.EditText(
                        viewport.toDocument(Point(width / 2f, height / 2f)),
                        note =
                        action == R.id.canvas_add_note
                    )
                )
                R.id.canvas_zoom_in -> zoomBy(1.25f)
                R.id.canvas_zoom_out -> zoomBy(0.8f)
                R.id.canvas_fit -> fitContent()
                AccessibilityActionCompat.ACTION_SCROLL_LEFT.id -> {
                    viewport = viewport.pan(step, 0f)
                    cameraChanged()
                }
                AccessibilityActionCompat.ACTION_SCROLL_RIGHT.id -> {
                    viewport = viewport.pan(-step, 0f)
                    cameraChanged()
                }
                AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD, AccessibilityActionCompat.ACTION_SCROLL_UP.id -> {
                    viewport =
                        viewport.pan(0f, step)
                    cameraChanged()
                }
                AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD, AccessibilityActionCompat.ACTION_SCROLL_DOWN.id -> {
                    viewport =
                        viewport.pan(0f, -step)
                    cameraChanged()
                }
                else -> return false
            }
            return true
        }
        when (action) {
            AccessibilityNodeInfoCompat.ACTION_CLICK -> onEvent(CanvasEvent.Select(setOf(element.id)))
            AccessibilityNodeInfoCompat.ACTION_LONG_CLICK -> {
                val content = element.content()
                if (content !is Note && content !is TextElement) return false
                onEvent(CanvasEvent.EditText(element.bounds.center, element, content is Note))
            }
            R.id.canvas_delete -> onEvent(CanvasEvent.Delete(setOf(element.id)))
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
                if (dx == 0f && dy == 0f && factor == 1f) return false
                onEvent(CanvasEvent.TransformBy(element.id, Point(dx, dy), factor))
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
        val movingIds = selectionPreview.map { it.id }.toSet()
        page.elements.forEach {
            if (it.id !in erased && it.id !in movingIds &&
                it.renderBounds.expand(10f).intersects(visible)
            ) {
                it.withoutStrokes(erased)?.let { visibleElement ->
                    if (partialFragments.isEmpty()) {
                        renderer.drawElement(canvas, visibleElement)
                    } else {
                        visibleElement.withStrokeFragments(partialFragments).forEach { fragment -> renderer.drawElement(canvas, fragment) }
                    }
                }
            }
        }
        renderer.drawLiveStroke(canvas, activePath, samples.firstOrNull(), samples.size == 1, ink)
        preview?.let { renderer.drawElement(canvas, it) }
        selectionPreview.forEach { renderer.drawElement(canvas, it) }
        val selected = selectionPreview.ifEmpty { page.elements.filter { it.id in selectedIds } }
        val selectedBounds = selected.map { it.selectionBounds }.reduceOrNull(Bounds::union)
        (selectionArea ?: selectedBounds)?.let {
            val b = it.expand(6 * density / viewport.scale)
            selectionPaint.color = 0xFF40836C.toInt()
            selectionPaint.style = Paint.Style.STROKE
            selectionPaint.strokeWidth =
                1.5f * density / viewport.scale
            canvas.drawRect(b.left, b.top, b.right, b.bottom, selectionPaint)
            selectionPaint.style = Paint.Style.FILL
            if (selectionArea == null) canvas.drawCircle(b.right, b.bottom, 7 * density / viewport.scale, selectionPaint)
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
                    Tool.PEN, Tool.PENCIL, Tool.MARKER, Tool.HIGHLIGHTER -> addSample(point)
                    Tool.ERASER -> erase(point)
                    Tool.SELECT -> {
                        val current = page.elements.filter { it.id in selectedIds }
                        val handle = current.map { it.selectionBounds }.reduceOrNull(Bounds::union)?.expand(6 * density / viewport.scale)
                        resizing = handle != null && hypot(point.x - handle.right, point.y - handle.bottom) < 22 * density / viewport.scale
                        val hit = page.elements.lastOrNull { it.hitTest(point, 12 * density / viewport.scale) }
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
                            page.elements.filter { it.id !in ids && it.renderBounds.intersects(visible) }.map { it.selectionBounds }
                        onEvent(CanvasEvent.Select(ids))
                    }
                    else -> Unit
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> if (!stylus) {
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
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(activePointer)
                if (index < 0) return true
                if (navigating || event.pointerCount > 1 && !stylus) {
                    val next = centroid(event)
                    previousScreen?.let { viewport = viewport.pan(next.x - it.x, next.y - it.y) }
                    previousScreen = next
                    cameraChanged()
                    return true
                }
                val point = viewport.toDocument(Point(event.getX(index), event.getY(index)))
                when (tool) {
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
                            val b = original.map { it.selectionBounds }.reduce(Bounds::union)
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
                            selectionPreview = original.map { element ->
                                val bounds = element.selectionBounds
                                val topLeft = Point(bounds.left, bounds.top).remap(b, final)
                                val bottomRight = Point(bounds.right, bounds.bottom).remap(b, final)
                                element.transform(Bounds.between(topLeft, bottomRight))
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
                        if (tool == Tool.ERASER && !navigating) {
                            erase(viewport.toDocument(Point(event.getX(event.actionIndex), event.getY(event.actionIndex))))
                        }
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
                if (tool == Tool.ERASER && !navigating && activePointer >= 0) erase(viewport.toDocument(Point(event.x, event.y)))
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
    private fun addSample(point: Point) {
        val previous = samples.lastOrNull()
        if (samples.size >= 2_000_000) return
        if (previous == null || hypot(point.x - previous.x, point.y - previous.y) * viewport.scale >= 0.5f) {
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
            Tool.PEN, Tool.PENCIL, Tool.MARKER, Tool.HIGHLIGHTER -> if (samples.isNotEmpty()) {
                onEvent(
                    CanvasEvent.Add(Stroke(points = samples.toList(), style = ink, tool = tool))
                )
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
                    onEvent(CanvasEvent.Select(page.elements.filter { it.renderBounds.intersects(area) }.map { it.id }.toSet()))
                }
                val single = dragOriginal.singleOrNull()
                if (selectionPreview.isEmpty() && (single?.content() is TextElement || single?.content() is Note)) {
                    down?.let { onEvent(CanvasEvent.EditText(it, single, single?.content() is Note)) }
                }
            }
            Tool.TEXT, Tool.NOTE -> down?.let { onEvent(CanvasEvent.EditText(it, note = tool == Tool.NOTE)) }
            Tool.FILL -> if (fillTap) down?.let { onEvent(CanvasEvent.Fill(it, ink.color)) }
            else -> preview?.let { if (it.bounds.width >= 2 && it.bounds.height >= 2) onEvent(CanvasEvent.Add(it)) }
        }
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
        clearPartialErasure()
        guideX = null
        guideY = null
        snapCandidates = emptyList()
        samples.clear()
        activePath.reset()
        preview = null
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
