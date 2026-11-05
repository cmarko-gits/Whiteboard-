package dev.whiteboard.core.model

/**
 * Resolves a batch atomically against one immutable page. Construction rejects dangling references
 * and connector targets rather than silently detaching them. Editing commands must handle detachment.
 * This is the connector portion of the future shared page geometry, not a document format change.
 */
class PageConnectionGeometry internal constructor(content: PageContent, connectors: List<Connector>, checkpoint: () -> Unit = {}) {
    constructor(page: Page, connectors: List<Connector>, checkpoint: () -> Unit = {}) : this(page.content, connectors, checkpoint)

    private val pageId = content.pageId
    private val elements = content.elements
    private val definitions = connectors.also { require(it.size <= Page.MAX_ELEMENTS) { "Too many connections" } }.toList()
    private val lines: Map<String, Line>

    init {
        checkpoint()
        var objectCount = content.objectCount.toLong()
        val ids = HashSet<String>()
        definitions.forEach { connector ->
            checkpoint()
            require(ids.add(connector.id)) { "Duplicate connection object ID" }
            if (content.containsId(connector.id)) {
                require(content.connections[connector.id] == connector) { "Connection object ID collides with page content" }
            } else {
                objectCount++
            }
        }
        require(objectCount <= Page.MAX_ELEMENTS) { "Too many page objects" }
        val ports = content.connectionPorts(checkpoint)
        lines = buildMap {
            definitions.forEach { connector ->
                checkpoint()
                put(connector.id, resolveLine(connector, ports))
            }
        }
    }

    fun matches(page: Page, connectors: List<Connector>): Boolean =
        page.id == pageId && page.elements === elements && connectors == definitions

    /** Uses the existing line geometry for identical bounds, picking and arrowheads across consumers. */
    fun line(id: String): Line = requireNotNull(lines[id]) { "Unknown connection ID" }

    internal companion object {
        fun resolveLine(connector: Connector, ports: PagePortIndex): Line {
            fun point(end: ConnectorEnd): Point = when (end) {
                is ConnectorEnd.Free -> end.point
                is ConnectorEnd.Bound -> ports.point(end.targetId, end.port)
            }
            return Line(connector.id, point(connector.start), point(connector.end), connector.style, connector.arrow).also {
                it.renderBounds
            }
        }
    }
}
