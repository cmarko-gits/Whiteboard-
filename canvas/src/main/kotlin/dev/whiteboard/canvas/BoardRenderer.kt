package dev.whiteboard.canvas

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import dev.whiteboard.core.model.*
import kotlin.math.*

/** One renderer is shared by the editor, thumbnails and file export. */
class BoardRenderer(private val assets: AssetProvider? = null, private val strictAssets: Boolean = false) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private data class CachedPath(val stroke: Stroke, val path: Path, val estimatedBytes: Long)
    private val pathCache = LinkedHashMap<String, CachedPath>(128, 0.75f, true)
    private var cachedBytes = 0L
    private val cacheLimit = 32L * 1024 * 1024
    private var pageBackground = Color.WHITE

    fun drawLiveStroke(canvas: Canvas, path: Path, first: Point?, singlePoint: Boolean, style: InkStyle) {
        if (first == null) return
        configure(style)
        if (singlePoint) {
            paint.style = Paint.Style.FILL
            canvas.drawCircle(first.x, first.y, style.width / 2, paint)
        } else {
            canvas.drawPath(path, paint)
        }
    }

    fun drawPage(canvas: Canvas, page: Page, visible: Bounds, scale: Float, omitId: String? = null) {
        drawBackground(canvas, page, visible, scale)
        val geometry = page.geometry
        page.elements.forEach { if (it.id != omitId && geometry.renderBounds(it).expand(4f).intersects(visible)) drawElement(canvas, it) }
    }

    fun drawBackground(canvas: Canvas, page: Page, visible: Bounds, scale: Float) {
        pageBackground = page.backgroundColor
        paint.reset()
        paint.isAntiAlias = true
        paint.color = page.backgroundColor
        paint.style = Paint.Style.FILL
        canvas.drawRect(visible.left, visible.top, visible.right, visible.bottom, paint)
        if (page.background == Background.PLAIN) return
        val dark = Color.red(page.backgroundColor) + Color.green(page.backgroundColor) + Color.blue(page.backgroundColor) < 320
        paint.color = if (dark) 0xFF454C46.toInt() else 0xFFD7DED5.toInt()
        paint.strokeWidth = 1f / scale
        var step = 32f
        while (step * scale < 12) step *= 2
        val firstX = floor(visible.left / step) * step
        val firstY = floor(visible.top / step) * step
        var y = firstY
        while (y <= visible.bottom) {
            if (page.background == Background.DOTS) {
                var x = firstX
                while (x <= visible.right) {
                    canvas.drawCircle(x, y, 1f / scale, paint)
                    x += step
                }
            } else {
                canvas.drawLine(visible.left, y, visible.right, y, paint)
            }
            y += step
        }
        if (page.background == Background.GRID) {
            var x = firstX
            while (x <= visible.right) {
                canvas.drawLine(x, visible.top, x, visible.bottom, paint)
                x += step
            }
        }
    }

    fun drawElement(canvas: Canvas, element: Element) {
        when (element) {
            is AffineElement -> {
                val p = element.placement
                val matrix = android.graphics.Matrix().apply {
                    setValues(
                        floatArrayOf(
                            p.xx.toFloat(), p.xy.toFloat(), p.tx.toFloat(),
                            p.yx.toFloat(), p.yy.toFloat(), p.ty.toFloat(),
                            0f, 0f, 1f
                        )
                    )
                }
                val content = element.source
                if (content is Shape) {
                    // Transform geometry before stroking: ink width remains in document units.
                    val shape = content
                    val path = shapePath(shape).apply { transform(matrix) }
                    shape.fill?.let {
                        paint.reset()
                        paint.isAntiAlias = true
                        paint.color = it
                        paint.style = Paint.Style.FILL
                        canvas.drawPath(path, paint)
                    }
                    configure(shape.style)
                    canvas.drawPath(path, paint)
                } else {
                    canvas.save()
                    try {
                        canvas.concat(matrix)
                        drawElement(canvas, element.source)
                    } finally {
                        canvas.restore()
                    }
                }
            }
            is ElementGroup -> element.children.forEach { drawElement(canvas, it) }
            is MediaElement -> {
                val image = assets?.load(element.assetId, element.pdfPage)
                if (image == null && strictAssets) throw java.io.IOException("Document attachment is unavailable")
                val b = element.bounds
                canvas.save()
                canvas.rotate(element.rotation.toFloat(), b.center.x, b.center.y)
                paint.reset()
                paint.isAntiAlias = true
                paint.isFilterBitmap = true
                if (image != null) {
                    val c = element.crop
                    val source = Rect(
                        (c.left * image.width).toInt(),
                        (c.top * image.height).toInt(),
                        (c.right * image.width).toInt().coerceAtLeast(1),
                        (c.bottom * image.height).toInt().coerceAtLeast(1)
                    )
                    canvas.drawBitmap(image, source, RectF(b.left, b.top, b.right, b.bottom), paint)
                } else {
                    paint.color = 0xFFE5E9E0.toInt()
                    canvas.drawRect(b.left, b.top, b.right, b.bottom, paint)
                    paint.color = 0xFF879680.toInt()
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 2f
                    canvas.drawLine(b.left, b.top, b.right, b.bottom, paint)
                    canvas.drawLine(b.right, b.top, b.left, b.bottom, paint)
                }
                canvas.restore()
            }
            is Stroke -> {
                configure(element.style)
                if (element.points.size == 1) {
                    paint.style = Paint.Style.FILL
                    canvas.drawCircle(element.points[0].x, element.points[0].y, element.style.width / 2, paint)
                } else {
                    canvas.drawPath(strokePath(element), paint)
                }
            }
            is Shape -> {
                val path = shapePath(element)
                element.fill?.let {
                    paint.reset()
                    paint.isAntiAlias = true
                    paint.color = it
                    paint.style = Paint.Style.FILL
                    canvas.drawPath(path, paint)
                }
                configure(element.style)
                canvas.drawPath(path, paint)
            }
            is Line -> {
                configure(element.style)
                canvas.drawLine(element.start.x, element.start.y, element.end.x, element.end.y, paint)
                element.arrowhead()?.let { (first, second) ->
                    val path = Path().apply {
                        moveTo(first.x, first.y)
                        lineTo(element.end.x, element.end.y)
                        lineTo(second.x, second.y)
                    }
                    canvas.drawPath(path, paint)
                }
            }
            is TextElement -> drawText(
                canvas,
                element.bounds,
                element.text,
                element.color,
                element.fontSize,
                element.bold,
                element.italic,
                element.alignment
            )
            is Note -> {
                paint.reset()
                paint.isAntiAlias = true
                paint.color = element.color
                val b = element.bounds
                canvas.drawRoundRect(b.left, b.top, b.right, b.bottom, 8f, 8f, paint)
                drawText(
                    canvas, b, element.text, NoteTextContrast.foreground(element.color, pageBackground),
                    element.fontSize, false, false, TextAlignment.LEFT, 16f
                )
            }
        }
    }

    private fun configure(style: InkStyle) {
        paint.reset()
        paint.isAntiAlias = true
        paint.color = style.color
        paint.alpha = (Color.alpha(style.color) * style.opacity).toInt().coerceIn(0, 255)
        paint.strokeWidth = style.width
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
    }
    private fun strokePath(stroke: Stroke): Path {
        pathCache[stroke.id]?.let { if (it.stroke === stroke) return it.path }
        val path = Path().apply {
            moveTo(stroke.points.first().x, stroke.points.first().y)
            for (index in 1 until stroke.points.size) lineTo(stroke.points[index].x, stroke.points[index].y)
        }
        pathCache.remove(stroke.id)?.let { cachedBytes -= it.estimatedBytes }
        val cost = 128L + stroke.points.size * 32L
        if (cost <= cacheLimit) {
            pathCache[stroke.id] = CachedPath(stroke, path, cost)
            cachedBytes += cost
            while (cachedBytes > cacheLimit) {
                val oldest = pathCache.entries.iterator()
                cachedBytes -= oldest.next().value.estimatedBytes
                oldest.remove()
            }
        }
        return path
    }
    private fun shapePath(shape: Shape): Path = Path().apply {
        val b = shape.bounds
        when (shape.kind) {
            Tool.ELLIPSE, Tool.CIRCLE -> addOval(b.left, b.top, b.right, b.bottom, Path.Direction.CW)
            Tool.TRIANGLE -> {
                moveTo(b.center.x, b.top)
                lineTo(b.right, b.bottom)
                lineTo(b.left, b.bottom)
                close()
            }
            Tool.DIAMOND -> {
                moveTo(b.center.x, b.top)
                lineTo(b.right, b.center.y)
                lineTo(b.center.x, b.bottom)
                lineTo(b.left, b.center.y)
                close()
            }
            else -> addRect(b.left, b.top, b.right, b.bottom, Path.Direction.CW)
        }
    }
    private fun drawText(
        canvas: Canvas,
        bounds: Bounds,
        text: String,
        color: Int,
        size: Float,
        bold: Boolean,
        italic: Boolean,
        alignment: TextAlignment,
        padding: Float = 4f
    ) {
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            typeface = Typeface.create("sans-serif", (if (bold) Typeface.BOLD else 0) or (if (italic) Typeface.ITALIC else 0))
        }
        val width = (bounds.width - padding * 2).toInt().coerceAtLeast(1)
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, width)
            .setAlignment(
                when (alignment) {
                    TextAlignment.LEFT -> Layout.Alignment.ALIGN_NORMAL
                    TextAlignment.CENTER -> Layout.Alignment.ALIGN_CENTER
                    TextAlignment.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
                }
            )
            .setIncludePad(false).build()
        canvas.save()
        canvas.clipRect(bounds.left, bounds.top, bounds.right, bounds.bottom)
        canvas.translate(bounds.left + padding, bounds.top + padding)
        layout.draw(canvas)
        canvas.restore()
    }
}
