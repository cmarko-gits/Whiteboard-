package dev.whiteboard.app.workspace

import dev.whiteboard.core.model.*

/** An immutable source snapshot; confirm must reject a changed document or dismissed request. */
data class ConnectionRequest(
    val boardId: String,
    val page: Page,
    val existing: Connector?,
    val targets: List<Element>,
    val start: ConnectorEnd,
    val end: ConnectorEnd,
    val startPosition: Point,
    val endPosition: Point,
    val style: InkStyle,
    val arrow: Boolean,
    val id: String = newId()
) {
    val targetIndex: Map<String, Int> = targets.withIndex().associate { it.value.id to it.index }
}
