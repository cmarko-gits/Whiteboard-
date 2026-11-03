package dev.whiteboard.core.editor

import dev.whiteboard.core.model.*

/**
 * Resolves a batch atomically against one immutable page. Construction rejects dangling references
 * and connector targets rather than silently detaching them. Editing commands must handle detachment.
 * This is the connector portion of the future shared page geometry, not a document format change.
 */
class PageConnectionGeometry(page: Page, connectors: List<Connector>, checkpoint: () -> Unit = {}) {
    private val pageId = page.id
    private val elements = page.elements
    private val definitions = connectors.also { require(it.size <= Page.MAX_ELEMENTS) { "Too many connections" } }.toList()
    private val lines: Map<String, Line>

    init {
        var objectCount = connectors.size.toLong()
        val ids = HashSet<String>()
        page.elements.forEach { element ->
            checkpoint()
            ids.add(element.id)
            if (element is ElementGroup) {
                objectCount += element.children.size
                element.children.forEach { child ->
                    checkpoint()
                    ids.add(child.id)
                }
            } else {
                objectCount++
            }
        }
        require(objectCount <= Page.MAX_ELEMENTS) { "Too many page objects" }
        definitions.forEach { connector ->
            checkpoint()
            require(ids.add(connector.id)) { "Duplicate connection object ID" }
        }
        val ports = PagePortIndex(page, checkpoint)
        fun point(end: ConnectorEnd): Point = when (end) {
            is ConnectorEnd.Free -> end.point
            is ConnectorEnd.Bound -> ports.point(end.targetId, end.port)
        }
        lines = buildMap {
            definitions.forEach { connector ->
                checkpoint()
                val line = Line(connector.id, point(connector.start), point(connector.end), connector.style, connector.arrow)
                line.renderBounds
                put(connector.id, line)
            }
        }
    }

    fun matches(page: Page, connectors: List<Connector>): Boolean =
        page.id == pageId && page.elements === elements && connectors == definitions

    /** Uses the existing line geometry for identical bounds, picking and arrowheads across consumers. */
    fun line(id: String): Line = requireNotNull(lines[id]) { "Unknown connection ID" }
}
