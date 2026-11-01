package dev.whiteboard.core.editor

import dev.whiteboard.core.model.*
import kotlin.math.*

/** Conservative geometry recognition. Opt-in; the original sketch remains in Undo history. */
object SmartShapeRecognizer {
    fun recognize(stroke: Stroke): Shape? {
        if (stroke.tool == Tool.HIGHLIGHTER || stroke.points.size < 12) return null
        val bounds = Bounds.of(stroke.points)
        val size = min(bounds.width, bounds.height)
        if (size < 16f) return null
        val first = stroke.points.first()
        val last = stroke.points.last()
        if (hypot(first.x - last.x, first.y - last.y) > size * 0.25f) return null
        val corners =
            listOf(
                Point(bounds.left, bounds.top),
                Point(bounds.right, bounds.top),
                Point(bounds.right, bounds.bottom),
                Point(bounds.left, bounds.bottom)
            )
        val cornersVisited = corners.all { corner -> stroke.points.any { hypot(it.x - corner.x, it.y - corner.y) < size * 0.15f } }
        val edgeError =
            stroke.points.sumOf { point ->
                minOf(
                    abs(point.x - bounds.left),
                    abs(point.x - bounds.right),
                    abs(point.y - bounds.top),
                    abs(point.y - bounds.bottom)
                ).toDouble()
            } /
                stroke.points.size
        val kind = if (cornersVisited && edgeError / size < 0.07) {
            Tool.RECTANGLE
        } else {
            val radialError = stroke.points.sumOf { point ->
                val x = (point.x - bounds.center.x) / (bounds.width / 2)
                val y = (point.y - bounds.center.y) / (bounds.height / 2)
                abs(sqrt(x * x + y * y) - 1).toDouble()
            } / stroke.points.size
            if (radialError > 0.16) return null
            Tool.ELLIPSE
        }
        return Shape(id = stroke.id, bounds = bounds, kind = kind, style = stroke.style)
    }
}
