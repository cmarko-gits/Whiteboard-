package dev.whiteboard.core.model

import java.util.ArrayDeque
import java.util.Collections

/** References existing page objects; text, color and geometry have one owner in Note/Connector. */
data class MindMapNode(val id: String, val parentId: String? = null, val connectionId: String? = null, val collapsed: Boolean = false) {
    init {
        require(isValidConnectionId(id)) { "Invalid mind-map node ID" }
        require((parentId == null) == (connectionId == null)) { "A child needs both parent and connection" }
        if (parentId != null && connectionId != null) {
            require(isValidConnectionId(parentId) && isValidConnectionId(connectionId))
            require(parentId != id && connectionId != id && connectionId != parentId)
        }
    }
}

/**
 * Bounded, immutable tree topology. This value is not yet integrated into the document format/UI.
 * Input order defines sibling order; all validation and traversal are iterative and cancellable.
 */
class MindMapTree(val id: String = newId(), nodes: List<MindMapNode>, checkpoint: () -> Unit = {}) {
    val nodes: List<MindMapNode>
    val rootId: String

    /** Conservative history accounting for topology values, excluding derived page indexes. */
    val estimatedBytes: Long
    private val byId: Map<String, MindMapNode>
    private val children: Map<String, List<MindMapNode>>

    init {
        checkpoint()
        require(isValidConnectionId(id)) { "Invalid mind-map ID" }
        require(nodes.size in 1..MAX_NODES) { "Mind-map node limit exceeded" }
        val prepared = ArrayList<MindMapNode>(nodes.size)
        val indexed = LinkedHashMap<String, MindMapNode>(nodes.size)
        val edges = HashSet<String>()
        var root: String? = null
        var bytes = 128L + id.length * 2L
        nodes.forEach { node ->
            checkpoint()
            require(node.id != id && indexed.put(node.id, node) == null) { "Duplicate mind-map identity" }
            if (node.parentId == null) {
                require(root == null) { "A mind map needs one root" }
                root = node.id
            } else {
                require(node.connectionId != id && edges.add(checkNotNull(node.connectionId))) { "Duplicate mind-map connection" }
            }
            prepared.add(node)
            bytes += 96L + node.id.length * 2L + (node.parentId?.length ?: 0) * 2L + (node.connectionId?.length ?: 0) * 2L
        }
        rootId = requireNotNull(root) { "Missing mind-map root" }
        val childLists = LinkedHashMap<String, MutableList<MindMapNode>>()
        prepared.forEach { node ->
            checkpoint()
            require(node.id !in edges) { "Node and connection identities collide" }
            node.parentId?.let { parent ->
                require(parent in indexed) { "Missing mind-map parent" }
                childLists.getOrPut(parent) { ArrayList() }.add(node)
            }
        }
        val queue = ArrayDeque<String>()
        queue.add(rootId)
        var visited = 0
        while (queue.isNotEmpty()) {
            checkpoint()
            // Each child has one parent. A disconnected cycle cannot be reached from the root.
            require(++visited <= prepared.size) { "Cyclic mind map" }
            childLists[queue.removeFirst()]?.forEach {
                checkpoint()
                queue.addLast(it.id)
            }
        }
        require(visited == prepared.size) { "Disconnected or cyclic mind map" }
        val immutableChildren = LinkedHashMap<String, List<MindMapNode>>(childLists.size)
        childLists.forEach { (parent, values) ->
            checkpoint()
            immutableChildren[parent] = Collections.unmodifiableList(values)
        }
        checkpoint()
        this.nodes = Collections.unmodifiableList(prepared)
        byId = Collections.unmodifiableMap(indexed)
        children = Collections.unmodifiableMap(immutableChildren)
        estimatedBytes = bytes
    }

    fun node(id: String): MindMapNode = requireNotNull(byId[id]) { "Unknown mind-map node" }

    fun childrenOf(id: String): List<MindMapNode> {
        node(id)
        return children[id] ?: emptyList()
    }

    /** Full branch, including descendants hidden by collapse. */
    fun subtree(id: String, checkpoint: () -> Unit = {}): List<MindMapNode> = traverse(id, false, checkpoint)

    fun visibleNodes(checkpoint: () -> Unit = {}): List<MindMapNode> = traverse(rootId, true, checkpoint)

    private fun traverse(id: String, respectCollapse: Boolean, checkpoint: () -> Unit): List<MindMapNode> {
        val pending = ArrayDeque<MindMapNode>()
        pending.add(node(id))
        val result = ArrayList<MindMapNode>()
        while (pending.isNotEmpty()) {
            checkpoint()
            val current = pending.removeLast()
            result.add(current)
            if (!respectCollapse || !current.collapsed) {
                val next = children[current.id] ?: emptyList()
                for (index in next.indices.reversed()) {
                    checkpoint()
                    pending.addLast(next[index])
                }
            }
        }
        checkpoint()
        return Collections.unmodifiableList(result)
    }

    /** Root-to-node path, independent of visibility. */
    fun pathTo(id: String, checkpoint: () -> Unit = {}): List<MindMapNode> {
        val result = ArrayList<MindMapNode>()
        var current: MindMapNode? = node(id)
        while (current != null) {
            checkpoint()
            result.add(current)
            current = current.parentId?.let(::node)
        }
        result.reverse()
        checkpoint()
        return Collections.unmodifiableList(result)
    }

    fun withCollapsed(id: String, collapsed: Boolean, checkpoint: () -> Unit = {}): MindMapTree {
        checkpoint()
        if (node(id).collapsed == collapsed) return this
        val updated = nodes.map {
            checkpoint()
            if (it.id == id) it.copy(collapsed = collapsed) else it
        }
        return MindMapTree(this.id, updated, checkpoint)
    }

    /** Expand ancestors only; revealing a collapsed node must preserve its own collapsed state. */
    fun reveal(id: String, checkpoint: () -> Unit = {}): MindMapTree {
        val path = pathTo(id, checkpoint)
        val expand = HashSet<String>()
        for (index in 0 until path.lastIndex) {
            checkpoint()
            if (path[index].collapsed) expand.add(path[index].id)
        }
        if (expand.isEmpty()) return this
        return MindMapTree(
            this.id,
            nodes.map {
                checkpoint()
                if (it.id in expand) it.copy(collapsed = false) else it
            },
            checkpoint
        )
    }

    /** Page binding is explicit; graph validity alone cannot establish object ownership. */
    fun validateObjects(page: Page, checkpoint: () -> Unit = {}) {
        val objects = HashMap<String, PageObject>(page.elements.size)
        page.elements.forEach {
            checkpoint()
            objects[it.id] = it
        }
        validateObjects(objects, page.content::containsId, checkpoint)
    }

    /** Shared page lookup avoids rebuilding all object indexes once for every map. */
    internal fun validateObjects(objects: Map<String, PageObject>, containsId: (String) -> Boolean, checkpoint: () -> Unit) {
        checkpoint()
        require(!containsId(id)) { "Mind-map ID collides with a page object" }
        nodes.forEach { node ->
            checkpoint()
            require(objects[node.id]?.content() is Note) { "A mind-map node must reference a top-level note" }
            if (node.parentId != null) {
                val edge = objects[node.connectionId] as? Connector
                require(edge?.start is ConnectorEnd.Bound && edge.end is ConnectorEnd.Bound) { "Missing bound mind-map connection" }
                require(edge.start.targetId == node.parentId && edge.end.targetId == node.id) { "Mind-map connection targets differ" }
            }
        }
        checkpoint()
    }

    override fun equals(other: Any?): Boolean = this === other || other is MindMapTree && id == other.id && nodes == other.nodes
    override fun hashCode(): Int = 31 * id.hashCode() + nodes.hashCode()
    override fun toString(): String = "MindMapTree(id=$id, rootId=$rootId, nodeCount=${nodes.size})"

    companion object {
        // Every node after the root also needs a canonical Connector in Page.elements.
        const val MAX_NODES = (Page.MAX_ELEMENTS + 1) / 2
    }
}
