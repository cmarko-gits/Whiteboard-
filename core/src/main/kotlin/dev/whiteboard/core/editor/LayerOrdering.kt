package dev.whiteboard.core.editor

import dev.whiteboard.core.model.*

enum class LayerStep(val offset: Int) { FORWARD(1), BACKWARD(-1) }

/** Move each selected block across one adjacent unselected object, preserving both relative orders. */
object LayerOrdering {
    fun canStep(elements: List<Element>, ids: Set<String>, step: LayerStep): Boolean = positions(elements, step).any { index ->
        elements[index].id in ids && elements[index + step.offset].id !in ids
    }

    fun step(elements: List<Element>, ids: Set<String>, step: LayerStep): List<Element> {
        if (ids.isEmpty()) return elements
        var result: MutableList<Element>? = null
        for (index in positions(elements, step)) {
            val current = result ?: elements
            val adjacent = index + step.offset
            if (current[index].id in ids && current[adjacent].id !in ids) {
                val updated = result ?: elements.toMutableList().also { result = it }
                val neighbor = updated[adjacent]
                updated[adjacent] = updated[index]
                updated[index] = neighbor
            }
        }
        return result?.toList() ?: elements
    }

    private fun positions(elements: List<Element>, step: LayerStep): IntProgression =
        if (step == LayerStep.FORWARD) elements.lastIndex - 1 downTo 0 else 1..elements.lastIndex
}

/** Reordering owns IDs, not copies of geometry; existing content and deletion commands own attachments. */
data class LayerOrderChange(val pageId: String, val before: List<String>, val after: List<String>) : DocumentChange {
    init {
        require(before.size <= Page.MAX_ELEMENTS && before.size == after.size)
        val ids = before.toSet()
        require(ids.size == before.size && ids == after.toSet())
        require(before != after)
    }
    override val estimatedBytes = 128L + 16L * (before.size + after.size)
    override val assetIds: Set<String> = emptySet()
    override fun apply(board: Board, forward: Boolean): Board {
        val page = requireNotNull(board.pages.firstOrNull { it.id == pageId })
        val expected = if (forward) before else after
        val target = if (forward) after else before
        require(page.elements.map(Element::id) == expected) { "Layer order changed before applying command" }
        val current = page.elements.associateBy(Element::id)
        return board.withPage(page.copy(elements = target.map(current::getValue))).copy(activePageId = pageId)
    }
}
