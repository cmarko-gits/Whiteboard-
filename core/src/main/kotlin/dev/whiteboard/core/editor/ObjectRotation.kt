package dev.whiteboard.core.editor

import dev.whiteboard.core.model.*

data class RotationPlan(val original: Page, val replacements: List<Element>) {
    init {
        require(replacements.map(Element::id).distinct().size == replacements.size)
        val ids = original.elements.map(Element::id).toSet()
        require(replacements.all { it.id in ids })
    }
}

/** Immutable planning can run off the UI thread; applying the plan rechecks its original page. */
object ObjectRotation {
    fun plan(
        page: Page,
        ids: Set<String>,
        degrees: Double,
        byteBudget: Long = 32L * 1024 * 1024,
        checkpoint: () -> Unit = {}
    ): RotationPlan {
        require(degrees.isFinite())
        require(byteBudget > 0)
        val selected = page.elements.filter { it.id in ids }
        if (selected.isEmpty() || AffineTransform2D.rotation(degrees) == AffineTransform2D.IDENTITY) return RotationPlan(page, emptyList())
        val retained = selected.sumOf { element ->
            2 * element.estimatedBytes() + 64L + 128L * element.leaves().count { it !is Stroke && it !is Line && it !is AffineElement }
        }
        if (retained > byteBudget) throw DocumentFailure(DocumentError.EDIT_LIMIT)
        val bounds = selected.map { it.selectionBounds }.reduce(Bounds::union)
        val turn = AffineTransform2D.rotation(degrees, bounds.center)
        val rotated = selected.map { rotate(it, turn, checkpoint) }
        rotated.forEach { it.renderBounds }
        return RotationPlan(page, rotated)
    }

    private fun rotate(element: Element, turn: AffineTransform2D, checkpoint: () -> Unit): Element {
        checkpoint()
        return when (element) {
            is ElementGroup -> element.copy(children = element.children.map { rotate(it, turn, checkpoint) })
            is Stroke -> element.copy(
                points = element.points.mapIndexed { index, point ->
                    if (index % 2048 == 0) checkpoint()
                    turn.map(point)
                }
            )
            is Line -> element.copy(start = turn.map(element.start), end = turn.map(element.end))
            is AffineElement -> element.copy(placement = element.placement.then(turn))
            else -> AffineElement.fromDocument(element, turn)
        }
    }
}
