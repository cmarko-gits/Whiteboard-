package dev.whiteboard.core.editor

import dev.whiteboard.core.model.*
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Clips a polyline against the capsule swept by one eraser movement, including round stroke caps. */
object PartialStrokeEraser {
    const val MAX_FRAGMENTS = 4096

    fun cut(stroke: Stroke, start: Point, end: Point, radius: Float): List<Stroke> {
        require(radius.isFinite() && radius >= 0f)
        val reach = radius.toDouble() + stroke.style.width / 2.0
        val left = min(start.x, end.x).toDouble() - reach
        val right = max(start.x, end.x).toDouble() + reach
        val top = min(start.y, end.y).toDouble() - reach
        val bottom = max(start.y, end.y).toDouble() + reach
        val bounds = stroke.bounds
        if (bounds.right < left || bounds.left > right || bounds.bottom < top || bounds.top > bottom) return listOf(stroke)
        val sweep = Capsule(start, end, reach)
        if (stroke.points.size == 1) return if (sweep.contains(stroke.points.single())) emptyList() else listOf(stroke)
        val pieces = mutableListOf<List<Point>>()
        var current = mutableListOf<Point>()
        var changed = false
        fun append(point: Point) {
            if (current.lastOrNull() != point) current.add(point)
        }
        fun finish() {
            if (current.isEmpty()) return
            if (pieces.size >= MAX_FRAGMENTS) throw DocumentFailure(DocumentError.EDIT_LIMIT)
            pieces.add(current.toList())
            current = mutableListOf()
        }
        for (index in 1 until stroke.points.size) {
            val a = stroke.points[index - 1]
            val b = stroke.points[index]
            val removed = sweep.intersection(a, b)
            if (removed == null) {
                if (changed) {
                    append(a)
                    append(b)
                }
                continue
            }
            // A miss preserves the original point list without copying it during the scan.
            if (!changed && index > 1) current.addAll(stroke.points.subList(0, index))
            changed = true
            if (removed.from > 0.0) {
                append(a)
                append(interpolate(a, b, removed.from))
            }
            finish()
            if (removed.to < 1.0) {
                append(interpolate(a, b, removed.to))
                append(b)
            }
        }
        if (!changed) return listOf(stroke)
        finish()
        return pieces.mapIndexed { index, points -> stroke.copy(id = if (index == 0) stroke.id else newId(), points = points) }
    }

    private fun interpolate(a: Point, b: Point, position: Double) = Point(
        (a.x + (b.x.toDouble() - a.x) * position).toFloat(),
        (a.y + (b.y.toDouble() - a.y) * position).toFloat()
    )

    private data class Interval(val from: Double, val to: Double)
    private val wholeInterval = Interval(0.0, 1.0)

    private class Capsule(private val start: Point, private val end: Point, private val radius: Double) {
        private val dx = end.x.toDouble() - start.x
        private val dy = end.y.toDouble() - start.y
        private val length = hypot(dx, dy)
        private val ux = if (length == 0.0) 0.0 else dx / length
        private val uy = if (length == 0.0) 0.0 else dy / length
        private val left = min(start.x, end.x).toDouble() - radius
        private val right = max(start.x, end.x).toDouble() + radius
        private val top = min(start.y, end.y).toDouble() - radius
        private val bottom = max(start.y, end.y).toDouble() + radius

        fun contains(point: Point): Boolean {
            val x = point.x.toDouble() - start.x
            val y = point.y.toDouble() - start.y
            if (length == 0.0) return hypot(x, y) <= radius
            val along = (x * ux + y * uy).coerceIn(0.0, length)
            return hypot(x - ux * along, y - uy * along) <= radius
        }

        fun intersection(a: Point, b: Point): Interval? {
            if (max(a.x, b.x) < left || min(a.x, b.x) > right || max(a.y, b.y) < top || min(a.y, b.y) > bottom) return null
            if (a.x == b.x && a.y == b.y) return if (contains(a)) wholeInterval else null
            if (contains(a) && contains(b)) return wholeInterval
            val first = circle(a, b, start)
            val last = circle(a, b, end)
            val middle = rectangle(a, b)
            if (first == null && last == null && middle == null) return null
            // A capsule is convex, so its disk/rectangle intersections form one interval.
            val from = minOf(first?.from ?: 1.0, last?.from ?: 1.0, middle?.from ?: 1.0)
            val to = maxOf(first?.to ?: 0.0, last?.to ?: 0.0, middle?.to ?: 0.0)
            return if (to - from > 1e-12) Interval(from, to) else null
        }

        private fun circle(a: Point, b: Point, center: Point): Interval? {
            val sx = b.x.toDouble() - a.x
            val sy = b.y.toDouble() - a.y
            val segmentLength = hypot(sx, sy)
            val x = center.x.toDouble() - a.x
            val y = center.y.toDouble() - a.y
            val projection = (x * sx + y * sy) / segmentLength
            val perpendicular = abs((x * sy - y * sx) / segmentLength)
            if (perpendicular > radius) return null
            // Avoid subtracting large quadratic terms for long, sparsely sampled strokes.
            val half = sqrt((radius - perpendicular) * (radius + perpendicular))
            val from = max(0.0, (projection - half) / segmentLength)
            val to = min(1.0, (projection + half) / segmentLength)
            return if (from <= to) Interval(from, to) else null
        }

        private fun rectangle(a: Point, b: Point): Interval? {
            if (length == 0.0) return null
            val x = a.x.toDouble() - start.x
            val y = a.y.toDouble() - start.y
            val sx = b.x.toDouble() - a.x
            val sy = b.y.toDouble() - a.y
            val along = slab(x * ux + y * uy, sx * ux + sy * uy, 0.0, length) ?: return null
            val across = slab(-x * uy + y * ux, -sx * uy + sy * ux, -radius, radius) ?: return null
            val from = max(along.from, across.from)
            val to = min(along.to, across.to)
            if (from > to) return null
            return Interval(from, to)
        }

        private fun slab(origin: Double, delta: Double, low: Double, high: Double): Interval? {
            if (delta == 0.0) return if (origin in low..high) wholeInterval else null
            val first = (low - origin) / delta
            val last = (high - origin) / delta
            val from = max(0.0, min(first, last))
            val to = min(1.0, max(first, last))
            return if (from <= to) Interval(from, to) else null
        }
    }
}
