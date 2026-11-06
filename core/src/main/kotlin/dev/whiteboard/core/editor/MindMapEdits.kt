package dev.whiteboard.core.editor

import dev.whiteboard.core.model.*
import java.util.ArrayDeque
import java.util.Collections

class MindMapEditPlan internal constructor(val original: Page, internal val command: EditCommand, val focusId: String?) {
    val removedObjectCount: Int = command.deltas.count { it.after == null }
    val removedNodeCount: Int = command.mindMaps?.let { change ->
        (change.before.sumOf { it.nodes.size } - change.after.sumOf { it.nodes.size }).coerceAtLeast(0)
    } ?: 0
    init {
        require(command.pageId == original.id)
    }
}

/** Pure, cancellable plans; the owning session checks the scene again before publishing one edit. */
object MindMapEdits {
    private const val WIDTH = 220f
    private const val HEIGHT = 140f
    private const val GAP = 32f
    private const val LIMIT = 1_000_000_000f
    private const val DEFAULT_BUDGET = 32L * 1024 * 1024

    fun create(
        page: Page,
        position: Point,
        title: String,
        color: Int,
        byteBudget: Long = DEFAULT_BUDGET,
        identity: () -> String = ::newId,
        checkpoint: () -> Unit = {}
    ): MindMapEditPlan {
        checkpoint()
        if (page.mindMaps.size >= MindMapPageIndex.MAX_TREES || page.content.objectCount >= Page.MAX_ELEMENTS) limit()
        val next = identities(page, identity, checkpoint)
        val note =
            Note(id = next(), bounds = slot(page, position.x - WIDTH / 2, position.y - HEIGHT / 2, checkpoint), text = title, color = color)
        val tree = MindMapTree(next(), listOf(MindMapNode(note.id)), checkpoint)
        return checkNotNull(plan(page, page.elements + note, page.mindMaps + tree, note.id, byteBudget, checkpoint))
    }

    fun addChild(
        page: Page,
        parentId: String,
        title: String,
        color: Int? = null,
        style: InkStyle = InkStyle(),
        byteBudget: Long = DEFAULT_BUDGET,
        identity: () -> String = ::newId,
        checkpoint: () -> Unit = {}
    ): MindMapEditPlan = add(page, parentId, null, title, color, style, byteBudget, identity, checkpoint)

    fun addSibling(
        page: Page,
        nodeId: String,
        title: String,
        color: Int? = null,
        style: InkStyle = InkStyle(),
        byteBudget: Long = DEFAULT_BUDGET,
        identity: () -> String = ::newId,
        checkpoint: () -> Unit = {}
    ): MindMapEditPlan {
        val tree = owner(page, nodeId)
        val parent = requireNotNull(tree.node(nodeId).parentId) { "The root cannot have a sibling" }
        return add(page, parent, nodeId, title, color, style, byteBudget, identity, checkpoint)
    }

    private fun add(
        page: Page,
        parentId: String,
        siblingId: String?,
        title: String,
        color: Int?,
        style: InkStyle,
        budget: Long,
        identity: () -> String,
        checkpoint: () -> Unit
    ): MindMapEditPlan {
        checkpoint()
        val tree = owner(page, parentId)
        tree.node(parentId)
        if (tree.nodes.size >= MindMapTree.MAX_NODES || page.content.objectCount + 2 > Page.MAX_ELEMENTS) limit()
        val next = identities(page, identity, checkpoint)
        val parent = page.elements.first { it.id == parentId }
        val noteStyle = parent.content() as Note
        val frame = page.geometry.selectionBounds(parent)
        val note = Note(next(), slot(page, frame.right + 80f, frame.top, checkpoint), title, color ?: noteStyle.color, noteStyle.fontSize)
        val edge =
            Connector(
                next(),
                ConnectorEnd.Bound(parentId, ConnectionPort.RIGHT),
                ConnectorEnd.Bound(note.id, ConnectionPort.LEFT),
                style,
                false
            )
        val node = MindMapNode(note.id, parentId, edge.id)
        val expanded = tree.reveal(parentId, checkpoint).withCollapsed(parentId, false, checkpoint)
        val nodes = expanded.nodes.toMutableList()
        if (siblingId == null) nodes.add(node) else nodes.add(nodes.indexOfFirst { it.id == siblingId } + 1, node)
        val changed = MindMapTree(tree.id, nodes, checkpoint)
        return checkNotNull(plan(page, page.elements + edge + note, replace(page, changed), note.id, budget, checkpoint))
    }

    fun edit(
        page: Page,
        nodeId: String,
        title: String,
        color: Int,
        byteBudget: Long = DEFAULT_BUDGET,
        checkpoint: () -> Unit = {}
    ): MindMapEditPlan? {
        val tree = owner(page, nodeId)
        tree.node(nodeId)
        val objects = page.elements.map {
            checkpoint()
            if (it.id == nodeId) it.withContent((it.content() as Note).copy(text = title, color = color)) else it
        }
        return plan(page, objects, page.mindMaps, visibleFocus(page, tree, nodeId, checkpoint), byteBudget, checkpoint)
    }

    fun collapse(
        page: Page,
        nodeId: String,
        collapsed: Boolean,
        byteBudget: Long = DEFAULT_BUDGET,
        checkpoint: () -> Unit = {}
    ): MindMapEditPlan? {
        val tree = owner(page, nodeId)
        val changed = tree.withCollapsed(nodeId, collapsed, checkpoint)
        return plan(page, page.elements, replace(page, changed), visibleFocus(page, tree, nodeId, checkpoint), byteBudget, checkpoint)
    }

    fun reveal(page: Page, nodeId: String, byteBudget: Long = DEFAULT_BUDGET, checkpoint: () -> Unit = {}): MindMapEditPlan? {
        val changed = owner(page, nodeId).reveal(nodeId, checkpoint)
        return plan(page, page.elements, replace(page, changed), nodeId, byteBudget, checkpoint)
    }

    fun reparent(
        page: Page,
        nodeId: String,
        parentId: String,
        byteBudget: Long = DEFAULT_BUDGET,
        checkpoint: () -> Unit = {}
    ): MindMapEditPlan? {
        val tree = owner(page, nodeId)
        val node = tree.node(nodeId)
        require(node.parentId != null && owner(page, parentId).id == tree.id) { "A branch must stay in its own map" }
        require(tree.pathTo(parentId, checkpoint).none { it.id == nodeId }) { "A branch cannot be moved below itself" }
        if (node.parentId == parentId) return null
        val expanded = tree.reveal(parentId, checkpoint).withCollapsed(parentId, false, checkpoint)
        val nodes = expanded.nodes.filterNot { it.id == nodeId } + node.copy(parentId = parentId)
        val changed = MindMapTree(tree.id, nodes, checkpoint)
        val objects = page.elements.map {
            checkpoint()
            if (it.id == node.connectionId) {
                val edge = it as Connector
                edge.copy(start = ConnectorEnd.Bound(parentId, (edge.start as ConnectorEnd.Bound).port))
            } else {
                it
            }
        }
        return plan(page, objects, replace(page, changed), nodeId, byteBudget, checkpoint)
    }

    /** Normalizes overlapping branches in one traversal per affected tree, including hidden nodes. */
    fun deleteSelection(page: Page, ids: Set<String>, byteBudget: Long = DEFAULT_BUDGET, checkpoint: () -> Unit = {}): MindMapEditPlan? {
        val selected = HashMap<String, MutableSet<String>>()
        ids.forEach { id ->
            checkpoint()
            page.mindMapIndex?.ownerOfObject(id)?.let { tree ->
                selected.getOrPut(tree.id) { HashSet() }.add(checkNotNull(tree.nodeForObject(id)).id)
            }
        }
        val removed = page.elements.filter { it.id in ids }.mapTo(HashSet()) { it.id }
        var focus: String? = null
        val maps = page.mindMaps.mapNotNull { tree ->
            checkpoint()
            val chosen = selected[tree.id] ?: return@mapNotNull tree
            val pending = ArrayDeque<MindMapNode>()
            pending.add(tree.node(tree.rootId))
            while (pending.isNotEmpty()) {
                checkpoint()
                val node = pending.removeLast()
                if (node.id in chosen) {
                    if (focus == null) focus = node.parentId
                    tree.subtree(node.id, checkpoint).forEach {
                        checkpoint()
                        removed.add(it.id)
                        it.connectionId?.let(removed::add)
                    }
                } else {
                    val children = tree.childrenOf(node.id)
                    for (index in children.indices.reversed()) pending.add(children[index])
                }
            }
            val retained = tree.nodes.filter {
                checkpoint()
                it.id !in removed
            }
            if (retained.isEmpty()) null else MindMapTree(tree.id, retained, checkpoint)
        }.map { tree ->
            if (focus != null &&
                tree.nodeForObject(checkNotNull(focus)) != null
            ) {
                tree.reveal(checkNotNull(focus), checkpoint)
            } else {
                tree
            }
        }
        val objects = page.elements.filter {
            checkpoint()
            it.id !in removed
        }
        return plan(page, objects, maps, focus, byteBudget, checkpoint)
    }

    private fun owner(page: Page, nodeId: String): MindMapTree = requireNotNull(page.mindMapIndex?.ownerOfObject(nodeId)) {
        "Unknown mind-map node"
    }
    private fun replace(page: Page, tree: MindMapTree): List<MindMapTree> = page.mindMaps.map { if (it.id == tree.id) tree else it }
    private fun visibleFocus(page: Page, tree: MindMapTree, id: String, checkpoint: () -> Unit): String =
        tree.pathTo(id, checkpoint).last { checkNotNull(page.mindMapIndex).isVisible(it.id) }.id

    private fun identities(page: Page, identity: () -> String, checkpoint: () -> Unit): () -> String {
        val allocated = HashSet<String>()
        val maps = page.mindMaps.map { it.id }.toHashSet()
        return {
            checkpoint()
            identity().also {
                require(isValidConnectionId(it) && !page.content.containsId(it) && it !in maps && allocated.add(it)) {
                    "Invalid mind-map identity"
                }
            }
        }
    }

    private fun slot(page: Page, x: Float, y: Float, checkpoint: () -> Unit): Bounds {
        val left = x.coerceIn(-LIMIT, LIMIT - WIDTH)
        var top = y.coerceAtLeast(-LIMIT)
        val obstacles = page.elements.filterIsInstance<Element>().filterNot { it is Stroke || it is Line }.map {
            checkpoint()
            it.selectionBounds
        }.filter { it.right + GAP > left && it.left - GAP < left + WIDTH }.sortedBy {
            checkpoint()
            it.top
        }
        for (frame in obstacles) {
            checkpoint()
            if (top + HEIGHT + GAP <= frame.top) break
            if (top - GAP < frame.bottom) top = frame.bottom + GAP
        }
        checkpoint()
        return Bounds(left, top, left + WIDTH, top + HEIGHT)
    }

    private fun plan(
        page: Page,
        requested: List<PageObject>,
        maps: List<MindMapTree>,
        focus: String?,
        budget: Long,
        checkpoint: () -> Unit
    ): MindMapEditPlan? {
        require(budget > 0)
        checkpoint()
        var objects = requested
        val retained = requested.allObjectIds()
        if (page.elements.any { it.id !in retained }) {
            val surviving = requested.filterIsInstance<Connector>().filter { page.content.connections[it.id] == it }
            val changes = ConnectionTargetRemoval.plan(
                page.copy(elements = page.elements.filterIsInstance<Element>(), mindMaps = emptyList()),
                page.copy(elements = requested.filterIsInstance<Element>(), mindMaps = emptyList()),
                surviving,
                checkpoint
            ).associateBy { it.before.id }
            objects = requested.map { changes[it.id]?.after ?: it }
        }
        val afterById = objects.withIndex().associate { it.value.id to it }
        val oldIds = page.elements.map { it.id }.toHashSet()
        val deltas = page.elements.mapIndexedNotNull { index, before ->
            checkpoint()
            val after = afterById[before.id]
            if (after?.value ==
                before
            ) {
                null
            } else {
                ElementDelta(LocatedElement(index, before), after?.let { LocatedElement(it.index, it.value) })
            }
        } + objects.mapIndexedNotNull { index, value ->
            checkpoint()
            if (value.id in oldIds) null else ElementDelta(null, LocatedElement(index, value))
        }
        val semantic = if (maps == page.mindMaps) null else MindMapChange(page.mindMaps, maps)
        if (deltas.isEmpty() && semantic == null) return null
        val command = EditCommand(page.id, Collections.unmodifiableList(deltas), semantic)
        if (command.estimatedBytes > budget) limit()
        val validated = page.copy(
            elements = if (deltas.isEmpty()) page.elements else objects,
            mindMaps = if (semantic ==
                null
            ) {
                page.mindMaps
            } else {
                maps
            }
        )
        validated.contentBounds
        require(focus == null || validated.mindMapIndex?.isVisible(focus) == true) { "Focus must remain visible" }
        checkpoint()
        return MindMapEditPlan(page, command, focus)
    }

    private fun limit(): Nothing = throw DocumentFailure(DocumentError.EDIT_LIMIT)
}
