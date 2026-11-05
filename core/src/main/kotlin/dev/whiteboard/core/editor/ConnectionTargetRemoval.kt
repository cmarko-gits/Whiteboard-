package dev.whiteboard.core.editor

import dev.whiteboard.core.model.*

data class ConnectionTargetChange(val before: Connector, val after: Connector) {
    init {
        require(before.id == after.id)
    }
}

/**
 * Prepares changes for unmodified surviving connections when a command removes target identities.
 * The future session command must include these deltas in the same budgeted Undo entry as removal;
 * this planner neither mutates the document nor silently repairs imported dangling references.
 */
object ConnectionTargetRemoval {
    fun plan(before: Page, after: Page, surviving: List<Connector>, checkpoint: () -> Unit = {}): List<ConnectionTargetChange> {
        require(before.id == after.id) { "Target removal must stay on the same page" }
        require(surviving.size <= Page.MAX_ELEMENTS)
        val definitions = surviving.toList()
        val original = PageConnectionGeometry(before, definitions, checkpoint)
        val ports = after.geometry.connectionPorts(checkpoint)
        val changes = ArrayList<ConnectionTargetChange>()
        fun detach(end: ConnectorEnd, position: Point): ConnectorEnd {
            checkpoint()
            return if (end is ConnectorEnd.Bound && !ports.supports(end.targetId)) ConnectorEnd.Free(position) else end
        }
        val updated = definitions.map { edge ->
            checkpoint()
            val line = original.line(edge.id)
            val replacement = edge.copy(start = detach(edge.start, line.start), end = detach(edge.end, line.end))
            if (replacement != edge) changes.add(ConnectionTargetChange(edge, replacement))
            replacement
        }
        PageConnectionGeometry(after, updated, checkpoint)
        checkpoint()
        return changes.toList()
    }
}
