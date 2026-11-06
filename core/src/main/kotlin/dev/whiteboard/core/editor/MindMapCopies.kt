package dev.whiteboard.core.editor

import dev.whiteboard.core.model.*

/** Rekeys complete maps with the same two-pass object identity mapping used by F05. */
object MindMapCopies {
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
