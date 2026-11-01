package dev.whiteboard.core.editor

import dev.whiteboard.core.model.*
import kotlin.math.abs

/** Finds a visible vector surface. Open strokes and media are never interpreted as closed regions. */
object FillEngine {
    sealed interface Target {
        data object Background : Target
        data object Blocked : Target
        data class Surface(val container: Element, val leafId: String) : Target
    }

    fun target(page: Page, point: Point): Target {
        for (container in page.elements.asReversed()) {
            for (leaf in container.leaves().asReversed()) {
                val hit = if (leaf is Shape) contains(leaf, point) else leaf.hitTest(point, 0f)
                if (!hit) continue
                return if (leaf is Shape || leaf is Note) Target.Surface(container, leaf.id) else Target.Blocked
            }
        }
        return Target.Background
    }

    private fun contains(shape: Shape, point: Point): Boolean {
        val bounds = shape.bounds
        if (!bounds.contains(point) || bounds.width <= 0f || bounds.height <= 0f) return false
        val x = ((point.x.toDouble() - bounds.left) / bounds.width) * 2 - 1
        val y = ((point.y.toDouble() - bounds.top) / bounds.height) * 2 - 1
        return when (shape.kind) {
            Tool.ELLIPSE, Tool.CIRCLE -> x * x + y * y <= 1
            Tool.DIAMOND -> abs(x) + abs(y) <= 1
            Tool.TRIANGLE -> abs(x) <= (y + 1) / 2
            else -> true
        }
    }
}
