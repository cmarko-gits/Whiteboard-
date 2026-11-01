package dev.whiteboard.core.model

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

data class Point(val x: Float, val y: Float) {
    init {
        require(x.isFinite() && y.isFinite())
    }
    operator fun plus(other: Point) = Point(x + other.x, y + other.y)
    operator fun minus(other: Point) = Point(x - other.x, y - other.y)
}

data class Bounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    init {
        require(listOf(left, top, right, bottom).all(Float::isFinite))
        require(listOf(left, top, right, bottom).all { abs(it) <= 1_000_000_000f })
        require(right >= left && bottom >= top)
    }
    val width get() = right - left
    val height get() = bottom - top
    val center get() = Point((left + right) / 2, (top + bottom) / 2)
    fun contains(point: Point, tolerance: Float = 0f) = point.x >= left - tolerance && point.x <= right + tolerance &&
        point.y >= top - tolerance && point.y <= bottom + tolerance
    fun intersects(other: Bounds) = left <= other.right && right >= other.left && top <= other.bottom && bottom >= other.top
    fun expand(amount: Float) = Bounds(left - amount, top - amount, right + amount, bottom + amount)
    fun union(other: Bounds) = Bounds(min(left, other.left), min(top, other.top), max(right, other.right), max(bottom, other.bottom))
    companion object {
        fun between(a: Point, b: Point) = Bounds(min(a.x, b.x), min(a.y, b.y), max(a.x, b.x), max(a.y, b.y))
        fun of(points: List<Point>): Bounds {
            require(points.isNotEmpty())
            return Bounds(points.minOf { it.x }, points.minOf { it.y }, points.maxOf { it.x }, points.maxOf { it.y })
        }
    }
}

data class Viewport(val offset: Point = Point(0f, 0f), val scale: Float = 1f) {
    init {
        require(scale.isFinite() && scale in MIN_SCALE..MAX_SCALE)
        require(abs(offset.x) <= MAX_OFFSET && abs(offset.y) <= MAX_OFFSET)
    }
    fun toDocument(point: Point) = Point((point.x - offset.x) / scale, (point.y - offset.y) / scale)
    fun toScreen(point: Point) = Point(point.x * scale + offset.x, point.y * scale + offset.y)
    fun pan(dx: Float, dy: Float) = copy(offset = boundedOffset(offset.x + dx, offset.y + dy))
    fun zoom(factor: Float, anchor: Point): Viewport {
        val next = (scale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
        val documentAnchor = toDocument(anchor)
        return Viewport(boundedOffset(anchor.x - documentAnchor.x * next, anchor.y - documentAnchor.y * next), next)
    }
    companion object {
        const val MIN_SCALE = 0.1f
        const val MAX_SCALE = 8f
        const val MAX_OFFSET = 8_000_000f
        private fun boundedOffset(x: Float, y: Float) = Point(x.coerceIn(-MAX_OFFSET, MAX_OFFSET), y.coerceIn(-MAX_OFFSET, MAX_OFFSET))
        fun fit(bounds: Bounds?, width: Float, height: Float, padding: Float = 64f): Viewport {
            if (bounds == null || width <= padding * 2 || height <= padding * 2) return Viewport()
            val scale = min((width - padding * 2) / max(bounds.width, 1f), (height - padding * 2) / max(bounds.height, 1f))
                .coerceIn(MIN_SCALE, MAX_SCALE)
            return Viewport(boundedOffset(width / 2 - bounds.center.x * scale, height / 2 - bounds.center.y * scale), scale)
        }
    }
}

fun distanceToSegment(point: Point, start: Point, end: Point): Float {
    val dx = end.x - start.x
    val dy = end.y - start.y
    val lengthSquared = dx * dx + dy * dy
    if (lengthSquared == 0f) return hypot(point.x - start.x, point.y - start.y)
    val projection = (((point.x - start.x) * dx + (point.y - start.y) * dy) / lengthSquared).coerceIn(0f, 1f)
    return hypot(point.x - start.x - projection * dx, point.y - start.y - projection * dy)
}

fun Point.remap(from: Bounds, to: Bounds): Point {
    val xRatio = if (abs(from.width) < 0.001f) 0.5f else (x - from.left) / from.width
    val yRatio = if (abs(from.height) < 0.001f) 0.5f else (y - from.top) / from.height
    return Point(to.left + xRatio * to.width, to.top + yRatio * to.height)
}
