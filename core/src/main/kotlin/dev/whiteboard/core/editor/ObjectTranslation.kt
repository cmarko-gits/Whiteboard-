package dev.whiteboard.core.editor

import dev.whiteboard.core.model.*

/** Translation preserves each object's local pose; it does not resize through an enclosing frame. */
internal object ObjectTranslation {
    fun translate(element: Element, delta: Point, checkpoint: () -> Unit): Element {
        checkpoint()
        fun Point.shift() = this + delta
        fun Bounds.shift() = Bounds(left + delta.x, top + delta.y, right + delta.x, bottom + delta.y)
        return when (element) {
            is ElementGroup -> element.copy(children = element.children.map { translate(it, delta, checkpoint) })
            is AffineElement -> element.copy(
                placement = element.placement.then(AffineTransform2D.translation(delta.x.toDouble(), delta.y.toDouble()))
            )
            is Stroke -> element.copy(
                points = element.points.mapIndexed { index, point ->
                    if (index % 2048 == 0) checkpoint()
                    point.shift()
                }
            )
            is Line -> element.copy(start = element.start.shift(), end = element.end.shift())
            is Shape -> element.copy(bounds = element.bounds.shift())
            is Note -> element.copy(bounds = element.bounds.shift())
            is TextElement -> element.copy(bounds = element.bounds.shift())
            is MediaElement -> element.copy(bounds = element.bounds.shift())
        }
    }
}
