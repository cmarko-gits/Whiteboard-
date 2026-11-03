package dev.whiteboard.core.model

import java.util.UUID
import kotlin.math.abs
import kotlin.math.hypot

fun newId(): String = UUID.randomUUID().toString()

enum class Tool {
    SELECT,
    PAN,
    PEN,
    PENCIL,
    MARKER,
    HIGHLIGHTER,
    ERASER,
    RECTANGLE,
    SQUARE,
    ELLIPSE,
    CIRCLE,
    TRIANGLE,
    DIAMOND,
    LINE,
    ARROW,
    TEXT,
    NOTE,
    FILL
}
enum class Background { PLAIN, GRID, DOTS, RULED }
enum class TextAlignment { LEFT, CENTER, RIGHT }

data class InkStyle(val color: Int = 0xFF202621.toInt(), val width: Float = 3f, val opacity: Float = 1f) {
    init {
        require(width.isFinite() && width in 0.1f..200f)
        require(opacity.isFinite() && opacity in 0f..1f)
    }
}

sealed interface Element {
    val id: String
    val bounds: Bounds
    fun transform(target: Bounds): Element
    fun duplicate(): Element
}

data class Stroke(override val id: String = newId(), val points: List<Point>, val style: InkStyle, val tool: Tool = Tool.PEN) : Element {
    init {
        require(points.size in 1..2_000_000)
        require(tool in setOf(Tool.PEN, Tool.PENCIL, Tool.MARKER, Tool.HIGHLIGHTER))
    }
    override val bounds: Bounds by lazy { Bounds.of(points).expand(style.width / 2) }
    override fun transform(target: Bounds) = copy(points = points.map { it.remap(bounds, target) })
    override fun duplicate() = copy(id = newId())
}

data class Shape(
    override val id: String = newId(),
    override val bounds: Bounds,
    val kind: Tool,
    val style: InkStyle,
    val fill: Int? = null
) : Element {
    init {
        require(kind in setOf(Tool.RECTANGLE, Tool.SQUARE, Tool.ELLIPSE, Tool.CIRCLE, Tool.TRIANGLE, Tool.DIAMOND))
    }
    override fun transform(target: Bounds) = copy(bounds = target)
    override fun duplicate() = copy(id = newId())
}

data class Line(override val id: String = newId(), val start: Point, val end: Point, val style: InkStyle, val arrow: Boolean = false) :
    Element {
    override val bounds get() = Bounds.between(start, end).expand(style.width / 2 + if (arrow) style.width * 3 else 0f)
    override fun transform(target: Bounds) = copy(start = start.remap(bounds, target), end = end.remap(bounds, target))
    override fun duplicate() = copy(id = newId())
}

data class TextElement(
    override val id: String = newId(),
    override val bounds: Bounds,
    val text: String,
    val color: Int = 0xFF202621.toInt(),
    val fontSize: Float = 24f,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val alignment: TextAlignment = TextAlignment.LEFT
) : Element {
    init {
        require(fontSize.isFinite() && fontSize in 8f..200f)
    }
    override fun transform(target: Bounds) = copy(bounds = target)
    override fun duplicate() = copy(id = newId())
}

data class Note(
    override val id: String = newId(),
    override val bounds: Bounds,
    val text: String,
    val color: Int = 0xFFF4E6A3.toInt(),
    val fontSize: Float = 22f
) : Element {
    init {
        require(fontSize.isFinite() && fontSize in 8f..200f)
    }
    override fun transform(target: Bounds) = copy(bounds = target)
    override fun duplicate() = copy(id = newId())
}

data class Page(
    val id: String = newId(),
    val name: String = "Page 1",
    val background: Background = Background.DOTS,
    val backgroundColor: Int = 0xFFFAFBF7.toInt(),
    val elements: List<Element> = emptyList(),
    val viewport: Viewport = Viewport()
) {
    init {
        require(elements.size <= MAX_ELEMENTS)
        require(elements.map { it.id }.distinct().size == elements.size)
        val allIds = elements.flatMap { element ->
            if (element is ElementGroup) listOf(element.id) + element.children.map { it.id } else listOf(element.id)
        }
        require(elements.sumOf { it.leaves().size } <= MAX_ELEMENTS && allIds.toSet().size == allIds.size)
    }
    val geometry: PageGeometry by lazy { PageGeometry(this) }
    val contentBounds get() = geometry.contentBounds
    companion object {
        const val MAX_ELEMENTS = 100_000
    }
}

data class Board(
    val id: String = newId(),
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val pages: List<Page> = listOf(Page()),
    val activePageId: String = pages.first().id,
    val favorite: Boolean = false,
    val folderId: String? = null,
    val trashedAt: Long? = null
) {
    init {
        require(name.isNotBlank() && name.length <= 120)
        require(pages.size in 1..1000 && pages.map { it.id }.distinct().size == pages.size)
        require(pages.any { it.id == activePageId })
    }
    val activePage get() = pages.first { it.id == activePageId }
    fun withPage(page: Page) = copy(pages = pages.map { if (it.id == page.id) page else it })
}

data class BoardSummary(
    val id: String,
    val name: String,
    val updatedAt: Long,
    val lastOpenedAt: Long,
    val favorite: Boolean,
    val pageCount: Int,
    val objectCount: Int,
    val thumbnail: String?,
    val trashedAt: Long?,
    val folderId: String?
)

fun Element.hitTest(point: Point, tolerance: Float): Boolean {
    if (!renderBounds.contains(point, tolerance)) return false
    return when (this) {
        is AffineElement -> {
            val localPoint = placement.unmap(point)
            val inverseScale = placement.inverseMaxScale.toFloat()
            // Shape ink is stroked after placement in document units. Do not expand local
            // render bounds: a valid large source can be scaled into a small document frame.
            if (source is Shape) {
                source.containsShape(localPoint, (tolerance + source.style.width / 2) * inverseScale)
            } else {
                source.bounds.contains(localPoint, tolerance * inverseScale)
            }
        }
        is ElementGroup -> children.any { it.hitTest(point, tolerance) }
        is Stroke -> {
            if (points.size == 1) {
                hypot(point.x - points[0].x, point.y - points[0].y) <= tolerance + style.width / 2
            } else {
                var hit = false
                for (index in 1 until points.size) {
                    if (distanceToSegment(point, points[index - 1], points[index]) <= tolerance + style.width / 2) {
                        hit = true
                        break
                    }
                }
                hit
            }
        }
        is Line -> {
            val radius = tolerance + style.width / 2
            distanceToSegment(point, start, end) <= radius || arrowhead()?.let { (first, second) ->
                distanceToSegment(point, first, end) <= radius || distanceToSegment(point, end, second) <= radius
            } == true
        }
        is Shape -> containsShape(point, tolerance + style.width / 2)
        is MediaElement -> {
            val local = if (rotation == 0) point else AffineTransform2D.rotation(rotation.toDouble(), bounds.center).unmap(point)
            bounds.contains(local, tolerance)
        }
        else -> true
    }
}

val Element.selectionBounds: Bounds get() = when (this) {
    is ElementGroup -> children.map { it.selectionBounds }.reduce(Bounds::union)
    is MediaElement -> rotatedBounds
    else -> bounds
}

val Element.renderBounds: Bounds get() = when (this) {
    is AffineElement -> if (source is Shape) bounds.expand(source.style.width / 2) else bounds
    is ElementGroup -> children.map { it.renderBounds }.reduce(Bounds::union)
    is MediaElement -> rotatedBounds
    is Shape -> bounds.expand(style.width / 2)
    is Line -> bounds.expand(if (arrow) maxOf(14f, style.width * 4) else 0f)
    else -> bounds
}

fun Element.estimatedBytes(): Long = when (this) {
    is AffineElement -> 128L + source.estimatedBytes()
    is ElementGroup -> 128L + children.sumOf { it.estimatedBytes() }
    is Stroke -> 128L + points.size * 24L
    is TextElement -> 128L + text.length * 2L
    is Note -> 128L + text.length * 2L
    else -> 192L
}
