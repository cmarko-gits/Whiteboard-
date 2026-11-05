package dev.whiteboard.core.editor

import dev.whiteboard.core.model.*

/** One layer-preserving object list and the complete identity map used to construct it. */
data class ConnectionCopy(val objects: List<PageObject>, val identities: Map<String, String>) {
    val elements: List<Element> get() = objects.filterIsInstance<Element>()
    val connections: List<Connector> get() = objects.filterIsInstance<Connector>()
}

object ConnectionCopies {
    fun duplicate(
        page: Page,
        connectors: List<Connector>,
        selectedIds: Set<String>,
        offset: Point = Point(0f, 0f),
        identity: () -> String = ::newId,
        checkpoint: () -> Unit = {}
    ): ConnectionCopy {
        require(connectors.size <= Page.MAX_ELEMENTS)
        val definitions = connectors.toList()
        val geometry = PageConnectionGeometry(page, definitions, checkpoint)
        val selected = page.elements.filterIsInstance<Element>().filter {
            checkpoint()
            it.id in selectedIds
        }
        val edges = definitions.filter {
            checkpoint()
            it.id in selectedIds
        }
        require(selected.size + edges.size == selectedIds.size) { "Unknown or child-only copy selection" }

        val sourceEdges = definitions.map { it.id }.toHashSet()
        val allocated = HashSet<String>()
        fun nextIdentity(): String {
            checkpoint()
            return identity().also { id ->
                require(
                    id.isNotBlank() && id.length <= 128 && !page.content.containsId(id) && id !in sourceEdges && allocated.add(id)
                ) { "Invalid copied object ID" }
            }
        }
        val objects = ObjectCopies.duplicate(selected, offset, ::nextIdentity, checkpoint)
        val edgeIds = edges.associate { it.id to nextIdentity() }
        val identities = objects.identities + edgeIds
        fun remap(end: ConnectorEnd, lastPosition: Point): ConnectorEnd {
            if (end is ConnectorEnd.Bound) {
                objects.identities[end.targetId]?.let { return ConnectorEnd.Bound(it, end.port) }
            }
            return ConnectorEnd.Free(lastPosition + offset)
        }
        val copied = edges.map { edge ->
            checkpoint()
            val line = geometry.line(edge.id)
            edge.copy(
                id = edgeIds.getValue(edge.id),
                start = remap(edge.start, line.start),
                end = remap(edge.end, line.end)
            )
        }
        // Validate the complete result before returning anything; no old target can be picked up
        // accidentally by a destination page or a later paste into another document.
        val copiesById = (objects.elements + copied).associateBy { it.id }
        val sourceIds = page.elements.map { it.id }.toHashSet()
        val sourceOrder = page.elements + definitions.filter { it.id !in sourceIds }
        val ordered = sourceOrder.filter { it.id in selectedIds }.map { copiesById.getValue(identities.getValue(it.id)) }
        val result = Page(id = page.id, elements = ordered)
        result.contentBounds
        checkpoint()
        return ConnectionCopy(ordered, identities)
    }
}
