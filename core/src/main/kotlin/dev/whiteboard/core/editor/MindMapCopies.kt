package dev.whiteboard.core.editor

import dev.whiteboard.core.model.*
import java.util.ArrayDeque

/** Rekeys complete maps with the same two-pass object identity mapping used by F05. */
object MindMapCopies {
    /** Copies each selected branch once, including hidden descendants; its incoming edge is omitted. */
    fun branches(
        page: Page,
        selectedIds: Set<String>,
        offset: Point = Point(0f, 0f),
        byteBudget: Long = 32L * 1024 * 1024,
        identity: () -> String = ::newId,
        checkpoint: () -> Unit = {}
    ): PageFragment {
        require(byteBudget > 0 && selectedIds.size <= Page.MAX_ELEMENTS)
        checkpoint()
        val canonical = page.elements.mapTo(HashSet()) {
            checkpoint()
            it.id
        }
        require(selectedIds.all { it in canonical }) { "Unknown or child-only branch selection" }
        val chosen = HashMap<String, MutableSet<String>>()
        val objects = HashSet<String>()
        selectedIds.forEach { id ->
            checkpoint()
            val tree = page.mindMapIndex?.ownerOfObject(id)
            if (tree == null) objects.add(id) else chosen.getOrPut(tree.id) { HashSet() }.add(checkNotNull(tree.nodeForObject(id)).id)
        }
        val branches = ArrayList<List<MindMapNode>>()
        page.mindMaps.forEach { tree ->
            val roots = chosen[tree.id] ?: return@forEach
            val pending = ArrayDeque<MindMapNode>()
            pending.add(tree.node(tree.rootId))
            while (pending.isNotEmpty()) {
                checkpoint()
                val node = pending.removeLast()
                if (node.id in roots) {
                    val branch = tree.subtree(node.id, checkpoint)
                    branches.add(branch)
                    branch.forEach { child ->
                        checkpoint()
                        objects.add(child.id)
                        if (child.id != node.id) objects.add(checkNotNull(child.connectionId))
                    }
                } else {
                    val children = tree.childrenOf(node.id)
                    for (index in children.indices.reversed()) pending.add(children[index])
                }
            }
        }
        if (branches.size > MindMapPageIndex.MAX_TREES) throw DocumentFailure(DocumentError.EDIT_LIMIT)
        var minimumBytes = 128L
        page.elements.forEach {
            checkpoint()
            if (it.id in objects) minimumBytes += it.estimatedBytes()
            if (minimumBytes > byteBudget) throw DocumentFailure(DocumentError.EDIT_LIMIT)
        }
        val copied = ConnectionCopies.duplicate(page, page.elements.filterIsInstance<Connector>(), objects, offset, identity, checkpoint)
        val allocated = copied.identities.values.toHashSet()
        val oldMaps = page.mindMaps.mapTo(HashSet()) { it.id }
        val maps = branches.map { nodes ->
            checkpoint()
            val mapId = identity()
            require(isValidConnectionId(mapId) && !page.content.containsId(mapId) && mapId !in oldMaps && allocated.add(mapId)) {
                "Invalid copied branch ID"
            }
            val root = nodes.first().id
            MindMapTree(
                mapId,
                nodes.map { node ->
                    checkpoint()
                    if (node.id == root) {
                        MindMapNode(copied.identities.getValue(node.id), collapsed = node.collapsed)
                    } else {
                        node.copy(
                            id = copied.identities.getValue(node.id),
                            parentId = copied.identities.getValue(checkNotNull(node.parentId)),
                            connectionId = copied.identities.getValue(checkNotNull(node.connectionId))
                        )
                    }
                },
                checkpoint
            )
        }
        val result = PageFragment(copied.objects, maps)
        if (result.estimatedBytes > byteBudget) throw DocumentFailure(DocumentError.EDIT_LIMIT)
        checkpoint()
        return result
    }

    fun duplicate(
        trees: List<MindMapTree>,
        identities: Map<String, String>,
        identity: () -> String = ::newId,
        checkpoint: () -> Unit = {}
    ): List<MindMapTree> {
        checkpoint()
        if (trees.isEmpty()) return emptyList()
        require(trees.size <= MindMapPageIndex.MAX_TREES && identities.size <= Page.MAX_ELEMENTS * 2)
        val allocated = HashSet<String>()
        identities.values.forEach {
            checkpoint()
            require(isValidConnectionId(it) && it !in identities && allocated.add(it)) { "Invalid copied identity mapping" }
        }
        val oldMaps = trees.map { it.id }.toHashSet()
        return trees.map { tree ->
            checkpoint()
            val id = identity()
            require(isValidConnectionId(id) && id !in identities && id !in oldMaps && allocated.add(id)) { "Invalid copied mind-map ID" }
            MindMapTree(
                id,
                tree.nodes.map { node ->
                    checkpoint()
                    node.copy(
                        id = identities.getValue(node.id),
                        parentId = node.parentId?.let(identities::getValue),
                        connectionId = node.connectionId?.let(identities::getValue)
                    )
                },
                checkpoint
            )
        }
    }
}
