package dev.whiteboard.core.editor

import dev.whiteboard.core.model.*
import kotlin.math.min

/** Swept hit testing prevents fast eraser gestures from skipping thin strokes between input events. */
object StrokeEraser {
    fun intersects(stroke: Stroke, start: Point, end: Point, radius: Float): Boolean {
        require(radius.isFinite() && radius >= 0f)
        val reach = radius + stroke.style.width / 2
        if (!stroke.bounds.intersects(Bounds.between(start, end).expand(reach))) return false
        if (stroke.points.size == 1) return distanceToSegment(stroke.points.single(), start, end) <= reach
        return (1 until stroke.points.size).any { index ->
            segmentDistance(start, end, stroke.points[index - 1], stroke.points[index]) <= reach
        }
    }
    private fun segmentDistance(a: Point, b: Point, c: Point, d: Point): Float {
        fun cross(p: Point, q: Point, r: Point): Double =
            (q.x.toDouble() - p.x) * (r.y.toDouble() - p.y) - (q.y.toDouble() - p.y) * (r.x.toDouble() - p.x)
        val abC = cross(a, b, c)
        val abD = cross(a, b, d)
        val cdA = cross(c, d, a)
        val cdB = cross(c, d, b)
        if (Bounds.between(a, b).intersects(Bounds.between(c, d)) && abC * abD <= 0.0 && cdA * cdB <= 0.0) return 0f
        return min(
            min(distanceToSegment(a, c, d), distanceToSegment(b, c, d)),
            min(distanceToSegment(c, a, b), distanceToSegment(d, a, b))
        )
    }
}
