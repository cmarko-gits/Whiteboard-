package dev.whiteboard.core.editor

import dev.whiteboard.core.model.*

enum class ObjectAlignment { LEFT, HORIZONTAL_CENTER, RIGHT, TOP, VERTICAL_CENTER, BOTTOM }

/** Align world selection frames by translation, preserving size, pose, content and layer order. */
object SelectionAlignment {
    fun plan(
        page: Page,
        ids: Set<String>,
        alignment: ObjectAlignment,
        byteBudget: Long = 32L * 1024 * 1024,
        checkpoint: () -> Unit = {}
    ): SelectionEditPlan {
        require(byteBudget > 0)
        val selected = page.elements.filter {
            checkpoint()
            it.id in ids
        }
        if (selected.size < 2) return SelectionEditPlan(page, emptyList())
        val frames = selected.map { page.geometry.selectionBounds(it) }
        val reference = frames.reduce(Bounds::union)
        val deltas = frames.map { frame ->
            when (alignment) {
                ObjectAlignment.LEFT -> Point(reference.left - frame.left, 0f)
                ObjectAlignment.HORIZONTAL_CENTER -> Point(reference.center.x - frame.center.x, 0f)
                ObjectAlignment.RIGHT -> Point(reference.right - frame.right, 0f)
                ObjectAlignment.TOP -> Point(0f, reference.top - frame.top)
                ObjectAlignment.VERTICAL_CENTER -> Point(0f, reference.center.y - frame.center.y)
                ObjectAlignment.BOTTOM -> Point(0f, reference.bottom - frame.bottom)
            }
        }
        val moved = selected.indices.filter { deltas[it].x != 0f || deltas[it].y != 0f }
        val cost = moved.sumOf { 2 * selected[it].estimatedBytes() + 64L }
        if (cost > byteBudget) throw DocumentFailure(DocumentError.EDIT_LIMIT)
        val replacements = moved.map { index -> translate(selected[index], deltas[index], checkpoint) }
        replacements.forEach { it.renderBounds }
        return SelectionEditPlan(page, replacements)
    }

    private fun translate(element: Element, delta: Point, checkpoint: () -> Unit): Element {
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
