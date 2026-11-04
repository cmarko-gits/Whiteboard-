package dev.whiteboard.core.model

/** Shared world geometry for one immutable element list; viewport and selection are not geometry. */
class PageGeometry internal constructor(val page: Page) {
    fun renderBounds(element: Element): Bounds = element.renderBounds
    fun selectionBounds(element: Element): Bounds = element.selectionBounds
    fun hitTest(element: Element, point: Point, tolerance: Float): Boolean {
        require(tolerance.isFinite() && tolerance >= 0f)
        return element.hitTest(point, tolerance)
    }

    val contentBounds: Bounds? get() = page.contentBounds

    /** Reused across metadata-only copies; content edits and page identity changes invalidate it. */
    fun connectionPorts(checkpoint: () -> Unit = {}): PagePortIndex = page.content.connectionPorts(checkpoint)

    fun topmostAt(point: Point, tolerance: Float): Element? {
        require(tolerance.isFinite() && tolerance >= 0f)
        return page.elements.lastOrNull { hitTest(it, point, tolerance) }
    }

    /** Replaces objects at their original layer positions. The source page and history are untouched. */
    fun preview(replacements: List<Element>, checkpoint: () -> Unit = {}): PageGeometry {
        if (replacements.isEmpty()) return this
        require(replacements.size <= Page.MAX_ELEMENTS)
        val remaining = HashMap<String, Element>(replacements.size)
        replacements.forEach {
            checkpoint()
            require(remaining.put(it.id, it) == null) { "Duplicate preview replacement" }
            it.renderBounds
        }
        var changed = false
        val elements = page.elements.map { original ->
            checkpoint()
            val replacement = remaining.remove(original.id) ?: original
            if (replacement !== original) changed = true
            replacement
        }
        require(remaining.isEmpty()) { "Unknown preview object" }
        return if (changed) page.copy(elements = elements).geometry else this
    }
}
