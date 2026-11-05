package dev.whiteboard.core

import dev.whiteboard.core.model.*
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class MindMapTreeTest {
    private val root = MindMapNode("root")
    private val first = MindMapNode("first", root.id, "first-edge")
    private val leaf = MindMapNode("leaf", first.id, "leaf-edge")
    private val second = MindMapNode("second", root.id, "second-edge")
    private val tree = MindMapTree("map", listOf(leaf, first, second, root))
    private fun ids(nodes: List<MindMapNode>) = nodes.map { it.id }

    @Test fun arbitraryStorageOrderPreservesSiblingOrderAndProducesRootFirstTraversal() {
        assertEquals(root.id, tree.rootId)
        assertEquals(listOf("first", "second"), ids(tree.childrenOf(root.id)))
        assertEquals(listOf("root", "first", "leaf", "second"), ids(tree.visibleNodes()))
        assertEquals(listOf("first", "leaf"), ids(tree.subtree(first.id)))
        assertEquals(listOf("root", "first", "leaf"), ids(tree.pathTo(leaf.id)))
        assertTrue(tree.childrenOf(leaf.id).isEmpty())
    }

    @Test fun collapseChangesVisibilityWithoutDroppingHiddenTopologyOrChangingTheOriginal() {
        val collapsed = tree.withCollapsed(first.id, true)
        assertEquals(listOf("root", "first", "second"), ids(collapsed.visibleNodes()))
        assertEquals(listOf("first", "leaf"), ids(collapsed.subtree(first.id)))
        assertEquals(4, collapsed.nodes.size)
        assertEquals(listOf("root", "first", "leaf", "second"), ids(tree.visibleNodes()))
        assertSame(collapsed, collapsed.withCollapsed(first.id, true))
        assertEquals(tree, collapsed.withCollapsed(first.id, false))
        assertEquals(tree.hashCode(), collapsed.withCollapsed(first.id, false).hashCode())
        assertEquals(listOf("root"), ids(tree.withCollapsed(root.id, true).visibleNodes()))
    }

    @Test fun revealExpandsOnlyAncestorsAndKeepsTheSelectedNodeCollapsed() {
        val collapsed = MindMapTree("map", tree.nodes.map { it.copy(collapsed = true) })
        val revealed = collapsed.reveal(first.id)
        assertEquals(listOf("root", "first", "second"), ids(revealed.visibleNodes()))
        assertTrue(revealed.node(first.id).collapsed)
        assertTrue(revealed.node(second.id).collapsed)
        assertFalse(revealed.node(root.id).collapsed)
        val leafRevealed = collapsed.reveal(leaf.id)
        assertEquals(listOf("root", "first", "leaf", "second"), ids(leafRevealed.visibleNodes()))
        assertTrue(leafRevealed.node(leaf.id).collapsed)
        assertSame(tree, tree.reveal(leaf.id))
        assertSame(collapsed, collapsed.reveal(root.id))
    }

    @Test fun inputAndReturnedCollectionsCannotMutateTheValidatedIndexes() {
        val input = arrayListOf(root, first, leaf)
        val snapshot = MindMapTree("map", input)
        input.clear()
        assertEquals(3, snapshot.nodes.size)
        assertThrows(UnsupportedOperationException::class.java) { (snapshot.nodes as MutableList).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (snapshot.childrenOf(root.id) as MutableList).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (snapshot.visibleNodes() as MutableList).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (snapshot.pathTo(leaf.id) as MutableList).clear() }
        assertEquals(listOf("root", "first", "leaf"), ids(snapshot.visibleNodes()))
    }

    @Test fun malformedNodeIdentitiesAndIncompleteParentRelationsAreRejected() {
        listOf("", " ", "x".repeat(129)).forEach { id ->
            assertThrows(IllegalArgumentException::class.java) { MindMapNode(id) }
            assertThrows(IllegalArgumentException::class.java) { MindMapTree(id, listOf(root)) }
        }
        assertThrows(IllegalArgumentException::class.java) { MindMapNode("child", root.id) }
        assertThrows(IllegalArgumentException::class.java) { MindMapNode("child", connectionId = "edge") }
        assertThrows(IllegalArgumentException::class.java) { MindMapNode("child", "child", "edge") }
        assertThrows(IllegalArgumentException::class.java) { MindMapNode("child", root.id, root.id) }
        assertThrows(IllegalArgumentException::class.java) { MindMapNode("child", root.id, "child") }
    }

    @Test fun duplicateRootNodeEdgeAndGraphIdentityCollisionsAreRejected() {
        val cases = listOf(
            listOf(root, root),
            listOf(root, MindMapNode("another-root")),
            listOf(root, first, second.copy(connectionId = first.connectionId)),
            listOf(root, first, MindMapNode("first-edge", root.id, "another-edge")),
            listOf(root, first.copy(id = "map")),
            listOf(root, first.copy(connectionId = "map"))
        )
        cases.forEach { nodes -> assertThrows(IllegalArgumentException::class.java) { MindMapTree("map", nodes) } }
    }

    @Test fun danglingParentsAndDisconnectedCyclesCannotBecomeValidTrees() {
        assertThrows(IllegalArgumentException::class.java) { MindMapTree("map", listOf(root, first.copy(parentId = "foreign"))) }
        val a = MindMapNode("a", "b", "a-edge")
        val b = MindMapNode("b", "a", "b-edge")
        assertThrows(IllegalArgumentException::class.java) { MindMapTree("map", listOf(root, a, b)) }
        assertThrows(IllegalArgumentException::class.java) { MindMapTree("map", listOf(a, b)) }
    }

    @Test fun emptyOversizedAndUnknownNodeOperationsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { MindMapTree("map", emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { MindMapTree("map", List(MindMapTree.MAX_NODES + 1) { root }) }
        assertThrows(IllegalArgumentException::class.java) { tree.node("foreign") }
        assertThrows(IllegalArgumentException::class.java) { tree.childrenOf("foreign") }
        assertThrows(IllegalArgumentException::class.java) { tree.subtree("foreign") }
        assertThrows(IllegalArgumentException::class.java) { tree.pathTo("foreign") }
        assertThrows(IllegalArgumentException::class.java) { tree.withCollapsed("foreign", true) }
        assertThrows(IllegalArgumentException::class.java) { tree.reveal("foreign") }
    }

    @Test fun maximumDepthUsesIterativeValidationTraversalAndReveal() {
        val nodes = List(MindMapTree.MAX_NODES) { index ->
            if (index == 0) {
                MindMapNode("node-0", collapsed = true)
            } else {
                MindMapNode("node-$index", "node-${index - 1}", "edge-$index")
            }
        }
        val deep = MindMapTree("deep-map", nodes)
        assertEquals(1, deep.visibleNodes().size)
        assertEquals(nodes.size, deep.subtree(deep.rootId).size)
        assertEquals(nodes, deep.pathTo(nodes.last().id))
        val revealed = deep.reveal(nodes.last().id)
        assertEquals(nodes.size, revealed.visibleNodes().size)
        assertEquals(1, deep.visibleNodes().size)
    }

    @Test fun cancellationDuringConstructionOrTraversalNeverMutatesAnExistingTree() {
        var visits = 0
        assertThrows(CancellationException::class.java) {
            MindMapTree("map", tree.nodes) { if (++visits == 8) throw CancellationException() }
        }
        assertThrows(CancellationException::class.java) { tree.visibleNodes { throw CancellationException() } }
        assertThrows(CancellationException::class.java) { tree.subtree(root.id) { throw CancellationException() } }
        assertThrows(CancellationException::class.java) { tree.pathTo(leaf.id) { throw CancellationException() } }
        assertThrows(CancellationException::class.java) { tree.withCollapsed(root.id, true) { throw CancellationException() } }
        assertThrows(CancellationException::class.java) { tree.reveal(leaf.id) { throw CancellationException() } }
        assertEquals(listOf("root", "first", "leaf", "second"), ids(tree.visibleNodes()))
    }

    @Test fun maximumWidthKeepsAllSiblingsAndCollapseDoesNotDiscardTheBranch() {
        val nodes = List(MindMapTree.MAX_NODES) { index ->
            if (index == 0) root else MindMapNode("sibling-$index", root.id, "edge-$index")
        }
        val wide = MindMapTree("wide-map", nodes)
        assertEquals(nodes, wide.visibleNodes())
        assertEquals(nodes.drop(1), wide.childrenOf(root.id))
        val collapsed = wide.withCollapsed(root.id, true)
        assertEquals(listOf(root.copy(collapsed = true)), collapsed.visibleNodes())
        assertEquals(nodes.size, collapsed.subtree(root.id).size)
        var checkpoints = 0
        MindMapTree("map", tree.nodes) { checkpoints++ }
        var visits = 0
        assertThrows(CancellationException::class.java) {
            MindMapTree("map", tree.nodes) { if (++visits == checkpoints - 1) throw CancellationException() }
        }
        assertEquals(tree.nodes, MindMapTree("map", tree.nodes).nodes)
    }

    private fun page(): Page {
        val notes = tree.nodes.mapIndexed { index, node ->
            Note(id = node.id, bounds = Bounds(index * 150f, 0f, index * 150f + 100, 80f), text = node.id)
        }
        val edges = tree.nodes.filter { it.parentId != null }.map {
            Connector(
                id = checkNotNull(it.connectionId),
                start = ConnectorEnd.Bound(checkNotNull(it.parentId), ConnectionPort.RIGHT),
                end = ConnectorEnd.Bound(it.id, ConnectionPort.LEFT)
            )
        }
        return Page(elements = notes + edges)
    }

    @Test fun objectBindingRequiresTopLevelNotesAndExactManagedBoundTargets() {
        val page = page()
        tree.validateObjects(page)
        assertThrows(IllegalArgumentException::class.java) { tree.validateObjects(Page()) }
        val edge = page.elements.filterIsInstance<Connector>().first()
        val wrong = edge.copy(start = ConnectorEnd.Free(Point(0f, 0f)))
        assertThrows(IllegalArgumentException::class.java) {
            tree.validateObjects(page.copy(elements = page.elements.map { if (it.id == edge.id) wrong else it }))
        }
        val reversed = edge.copy(start = edge.end, end = edge.start)
        assertThrows(IllegalArgumentException::class.java) {
            tree.validateObjects(page.copy(elements = page.elements.map { if (it.id == edge.id) reversed else it }))
        }
        val note = page.elements.filterIsInstance<Note>().first()
        val text = TextElement(id = note.id, bounds = note.bounds, text = note.text)
        assertThrows(IllegalArgumentException::class.java) {
            tree.validateObjects(page.copy(elements = page.elements.map { if (it.id == note.id) text else it }))
        }
        assertThrows(IllegalArgumentException::class.java) {
            tree.validateObjects(page.copy(elements = page.elements + Note(id = tree.id, bounds = note.bounds, text = "")))
        }
        val notes = page.elements.filterIsInstance<Note>().take(2)
        val grouped = page.copy(
            elements = page.elements.filterNot { it.id in notes.map(Note::id) } + ElementGroup(id = "group", children = notes)
        )
        assertThrows(IllegalArgumentException::class.java) { tree.validateObjects(grouped) }
        val conflictingGroup = ElementGroup(
            id = "other-group",
            children = listOf(note.copy(id = tree.id), note.copy(id = "unrelated-note"))
        )
        assertThrows(IllegalArgumentException::class.java) {
            tree.validateObjects(page.copy(elements = page.elements + conflictingGroup))
        }
        assertThrows(CancellationException::class.java) { tree.validateObjects(page) { throw CancellationException() } }
    }

    @Test fun independentConcurrentReadersCannotChangeEachOthersCollapseState() {
        val executor = Executors.newFixedThreadPool(4)
        try {
            val futures = (0 until 12).map { index ->
                executor.submit<List<String>> {
                    ids(tree.withCollapsed(first.id, index % 2 == 0).visibleNodes())
                }
            }
            futures.forEachIndexed { index, future ->
                assertEquals(
                    if (index % 2 == 0) listOf("root", "first", "second") else listOf("root", "first", "leaf", "second"),
                    future.get(10, TimeUnit.SECONDS)
                )
            }
        } finally {
            executor.shutdownNow()
        }
        assertFalse(tree.node(first.id).collapsed)
    }
}
