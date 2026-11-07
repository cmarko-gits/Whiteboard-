package dev.whiteboard.app.workspace

import dev.whiteboard.core.editor.DocumentSession
import dev.whiteboard.core.model.*
import java.util.ArrayDeque
import java.util.Collections

data class MindMapRow(
    val node: MindMapNode,
    val mapId: String,
    val note: Note,
    val depth: Int,
    val visible: Boolean,
    val childCount: Int,
    val mapNumber: Int
)

class MindMapEditorRequest internal constructor(
    val boardId: String,
    val page: Page,
    val position: Point,
    rows: List<MindMapRow>,
    val selectedNodeId: String?,
    internal val owner: DocumentSession,
    val id: String = newId()
) {
    val rows: List<MindMapRow> = Collections.unmodifiableList(rows.toList())
    val byId: Map<String, MindMapRow> = Collections.unmodifiableMap(this.rows.associateBy { it.node.id })

    companion object {
        internal fun prepare(
            owner: DocumentSession,
            source: Page,
            boardId: String,
            position: Point,
            nodeId: String?,
            checkpoint: () -> Unit
        ): MindMapEditorRequest {
            val objects = source.elements.associateBy {
                checkpoint()
                it.id
            }
            val rows = ArrayList<MindMapRow>()
            source.mindMaps.forEachIndexed { mapIndex, tree ->
                val pending = ArrayDeque<Pair<MindMapNode, Int>>()
                pending.add(tree.node(tree.rootId) to 0)
                while (pending.isNotEmpty()) {
                    checkpoint()
                    val (node, depth) = pending.removeLast()
                    val children = tree.childrenOf(node.id)
                    rows.add(
                        MindMapRow(
                            node,
                            tree.id,
                            objects.getValue(node.id).content() as Note,
                            depth,
                            checkNotNull(source.mindMapIndex).isVisible(node.id),
                            children.size,
                            mapIndex + 1
                        )
                    )
                    for (index in children.indices.reversed()) pending.add(children[index] to depth + 1)
                }
            }
            val selected = nodeId?.let { source.mindMapIndex?.ownerOfObject(it)?.nodeForObject(it)?.id }
            checkpoint()
            return MindMapEditorRequest(boardId, source, position, rows, selected, owner)
        }
    }
}

sealed interface MindMapAction {
    data class Create(val title: String, val color: Int) : MindMapAction
    data class Child(val parentId: String, val title: String, val color: Int) : MindMapAction
    data class Sibling(val nodeId: String, val title: String, val color: Int) : MindMapAction
    data class Edit(val nodeId: String, val title: String, val color: Int) : MindMapAction
    data class Collapse(val nodeId: String, val collapsed: Boolean) : MindMapAction
    data class Focus(val nodeId: String) : MindMapAction
    data class Reparent(val nodeId: String, val parentId: String) : MindMapAction
    data class Move(val nodeId: String, val delta: Point) : MindMapAction
    data class SelectMap(val nodeId: String) : MindMapAction
}
