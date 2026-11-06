package dev.whiteboard.core.editor

import dev.whiteboard.core.model.*

/** Finds a visible vector surface. Open strokes and media are never interpreted as closed regions. */
object FillEngine {
    sealed interface Target {
        data object Background : Target
        data object Blocked : Target
        data class Surface(val container: PageObject, val leafId: String) : Target
    }

    fun target(page: Page, point: Point): Target {
        for (container in page.visibleElements.asReversed()) {
            for (leaf in container.leaves().asReversed()) {
                if (!page.geometry.renderBounds(leaf).contains(point)) continue
                val content = leaf.content()
                val localPoint = if (leaf is AffineElement) leaf.placement.unmap(point) else point
                val hit = if (content is Shape) content.containsShape(localPoint) else page.geometry.hitTest(leaf, point, 0f)
                if (!hit) continue
                return if (content is Shape || content is Note) Target.Surface(container, leaf.id) else Target.Blocked
            }
        }
        return Target.Background
    }
}
