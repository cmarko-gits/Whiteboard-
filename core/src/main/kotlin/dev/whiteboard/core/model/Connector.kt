package dev.whiteboard.core.model

/** A world position or an explicit local port of an object on the owning page. */
sealed interface ConnectorEnd {
    data class Free(val point: Point) : ConnectorEnd {
        init {
            Bounds(point.x, point.y, point.x, point.y)
        }
    }

    data class Bound(val targetId: String, val port: ConnectionPort) : ConnectorEnd {
        init {
            requireConnectionId(targetId)
        }
    }
}

/**
 * Page-independent connection intent. Resolve both ends against one page snapshot before use.
 * Kept outside Element until all document, history and rendering consumers support page geometry.
 */
data class Connector(
    val id: String = newId(),
    val start: ConnectorEnd,
    val end: ConnectorEnd,
    val style: InkStyle = InkStyle(),
    val arrow: Boolean = true
) {
    init {
        requireConnectionId(id)
        require((start as? ConnectorEnd.Bound)?.targetId != id && (end as? ConnectorEnd.Bound)?.targetId != id)
        if (start is ConnectorEnd.Bound && end is ConnectorEnd.Bound) {
            require(start.targetId != end.targetId) { "Connections to the same object are not supported" }
        }
    }
}

private fun requireConnectionId(id: String) {
    require(id.length in 1..128 && id.isNotBlank()) { "Invalid connection object ID" }
}
