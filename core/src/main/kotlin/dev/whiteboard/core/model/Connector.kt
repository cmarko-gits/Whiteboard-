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
 * A PageObject without standalone bounds. Kept outside Element so a group or affine source cannot
 * acquire a connection that needs an owning page to resolve its geometry.
 */
data class Connector(
    override val id: String = newId(),
    val start: ConnectorEnd,
    val end: ConnectorEnd,
    val style: InkStyle = InkStyle(),
    val arrow: Boolean = true
) : PageObject {
    init {
        requireConnectionId(id)
        require((start as? ConnectorEnd.Bound)?.targetId != id && (end as? ConnectorEnd.Bound)?.targetId != id)
        if (start is ConnectorEnd.Bound && end is ConnectorEnd.Bound) {
            require(start.targetId != end.targetId) { "Connections to the same object are not supported" }
        }
    }
}

private fun requireConnectionId(id: String) {
    require(isValidConnectionId(id)) { "Invalid connection object ID" }
}

internal fun isValidConnectionId(id: String): Boolean = id.length in 1..128 && id.isNotBlank()
