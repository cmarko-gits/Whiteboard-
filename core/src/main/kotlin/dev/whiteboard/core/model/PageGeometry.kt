package dev.whiteboard.core.model

/** Shared world geometry for one immutable element list; viewport and selection are not geometry. */
class PageGeometry internal constructor(val page: Page) {
    fun resolve(element: PageObject): Element = page.content.resolve(element)
    fun renderBounds(element: PageObject): Bounds = resolve(element).renderBounds
    fun selectionBounds(element: PageObject): Bounds = resolve(element).selectionBounds
    fun hitTest(element: PageObject, point: Point, tolerance: Float): Boolean {
        require(tolerance.isFinite() && tolerance >= 0f)
        return resolve(element).hitTest(point, tolerance)
    }

    val contentBounds: Bounds? get() = page.contentBounds

    /** Reused across metadata-only copies; content edits and page identity changes invalidate it. */
    fun connectionPorts(checkpoint: () -> Unit = {}): PagePortIndex = page.content.connectionPorts(checkpoint)

    fun topmostAt(point: Point, tolerance: Float): PageObject? {
        require(tolerance.isFinite() && tolerance >= 0f)
        return page.elements.lastOrNull { hitTest(it, point, tolerance) }
    }

    /** Replaces objects at their original layer positions. The source page and history are untouched. */
    fun preview(replacements: List<PageObject>, checkpoint: () -> Unit = {}): PageGeometry {
        if (replacements.isEmpty()) return this
        require(replacements.size <= Page.MAX_ELEMENTS)
        val remaining = HashMap<String, PageObject>(replacements.size)
        replacements.forEach {
            checkpoint()
            require(remaining.put(it.id, it) == null) { "Duplicate preview replacement" }
            if (it is Element) it.renderBounds
        }
        var changed = false
        val elements = page.elements.map { original ->
            checkpoint()
            val replacement = remaining.remove(original.id) ?: original
            if (replacement !== original) changed = true
            replacement
        }
        require(remaining.isEmpty()) { "Unknown preview object" }
        return if (changed) {
            page.copy(elements = elements).geometry.also { geometry ->
                replacements.forEach {
                    checkpoint()
                    geometry.renderBounds(it)
                }
                if (geometry.page.content.connections.isNotEmpty()) geometry.contentBounds
                checkpoint()
            }
        } else {
            this
        }
    }

    fun transform(element: PageObject, target: Bounds, movableTargets: Set<String> = emptySet()): PageObject = when (element) {
        is Element -> element.transform(target)
        is Connector -> {
            val bound = listOf(element.start, element.end).filterIsInstance<ConnectorEnd.Bound>()
            if (bound.any { it.targetId !in movableTargets }) {
                element
            } else {
                val frame = selectionBounds(element)
                fun remap(end: ConnectorEnd): ConnectorEnd = when (end) {
                    is ConnectorEnd.Free -> ConnectorEnd.Free(end.point.remap(frame, target))
                    is ConnectorEnd.Bound -> end
                }
                element.copy(start = remap(element.start), end = remap(element.end))
            }
        }
    }
}
