package dev.whiteboard.core

import dev.whiteboard.core.model.*
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

class MindMapPageIndexTest {
    private fun note(id: String) = Note(id = id, bounds = Bounds(0f, 0f, 100f, 80f), text = id)
    private val root = note("root")
    private val child = note("child")
    private val external = note("external")
    private fun edge(id: String, first: String, second: String) = Connector(
        id = id,
        start = ConnectorEnd.Bound(first, ConnectionPort.RIGHT),
        end = ConnectorEnd.Bound(second, ConnectionPort.LEFT)
    )
    private val managed = edge("managed", root.id, child.id)
    private val externalEdge = edge("external-edge", child.id, external.id)
    private val independent = edge("independent", root.id, external.id)
    private val free = Connector(id = "free", start = ConnectorEnd.Free(Point(0f, 0f)), end = ConnectorEnd.Free(Point(200f, 100f)))
    private val page = Page(elements = listOf(root, externalEdge, child, managed, independent, external, free))
    private val tree = MindMapTree("map", listOf(MindMapNode(root.id), MindMapNode(child.id, root.id, managed.id)))

    @Test fun collapseHidesDescendantsAndAllConnectionsWithHiddenEndsWithoutChangingCanonicalData() {
        val index = MindMapPageIndex(page, listOf(tree.withCollapsed(root.id, true)))
        assertEquals(listOf(root, independent, external, free), index.visibleObjects)
        assertFalse(index.isVisible(child.id))
        assertFalse(index.isVisible(managed.id))
        assertFalse(index.isVisible(externalEdge.id))
        assertTrue(index.isVisible(root.id))
        assertTrue(index.isVisible(independent.id))
        assertFalse(index.isVisible("unknown"))
        assertEquals(7, page.elements.size)
        assertEquals(Point(100f, 40f), (page.geometry.resolve(externalEdge) as Line).start)
        val expanded = MindMapPageIndex(page, listOf(tree))
        assertEquals(page.elements, expanded.visibleObjects)
        assertTrue(expanded.isVisible(child.id))
    }

    @Test fun lookupDistinguishesManagedObjectsFromOrdinaryExternalConnections() {
        val index = MindMapPageIndex(page, listOf(tree))
        assertSame(tree, index.tree(tree.id))
        assertSame(tree, index.ownerOfObject(root.id))
        assertSame(tree, index.ownerOfObject(child.id))
        assertSame(tree, index.ownerOfObject(managed.id))
        assertNull(index.ownerOfObject(externalEdge.id))
        assertNull(index.ownerOfObject(external.id))
        assertNull(index.ownerOfObject("unknown"))
        assertThrows(IllegalArgumentException::class.java) { index.tree("unknown") }
    }

    @Test fun onePageCanContainIndependentMapsButCannotShareNodesOrMapIdentities() {
        val other = MindMapTree("other-map", listOf(MindMapNode(external.id)))
        val index = MindMapPageIndex(page, listOf(tree, other))
        assertSame(other, index.ownerOfObject(external.id))
        assertThrows(IllegalArgumentException::class.java) { MindMapPageIndex(page, listOf(tree, tree)) }
        val shared = MindMapTree("shared-map", listOf(MindMapNode(child.id)))
        assertThrows(IllegalArgumentException::class.java) { MindMapPageIndex(page, listOf(tree, shared)) }
        assertThrows(IllegalArgumentException::class.java) { MindMapPageIndex(page, listOf(MindMapTree(root.id, other.nodes))) }
    }

    @Test fun bindingRejectsForeignPagesAndIndexesRejectChangedGeometryButAcceptMetadataCopies() {
        val index = MindMapPageIndex(page, listOf(tree))
        assertTrue(index.matchesGeometry(page.copy(name = "Renamed", viewport = Viewport(offset = Point(20f, 30f)))))
        assertFalse(index.matchesGeometry(page.copy(id = "other-page")))
        val changed = page.copy(elements = page.elements.map { if (it === child) child.copy(text = "Updated") else it })
        assertFalse(index.matchesGeometry(changed))
        assertThrows(IllegalArgumentException::class.java) { MindMapPageIndex(Page(), listOf(tree)) }
        val collapsed = MindMapPageIndex(page, listOf(tree.withCollapsed(root.id, true)))
        assertTrue(collapsed.matchesGeometry(page))
        assertNotSame(index, collapsed)
        assertNotEquals(index.visibleObjects, collapsed.visibleObjects)
    }

    @Test fun emptyMapsPreserveOrdinaryObjectsAndGroupChildVisibility() {
        val group = ElementGroup(id = "group", children = listOf(root, child))
        val source = Page(elements = listOf(group, free))
        val index = MindMapPageIndex(source, emptyList())
        assertEquals(source.elements, index.visibleObjects)
        assertTrue(index.isVisible(group.id))
        assertTrue(index.isVisible(root.id))
        assertTrue(index.isVisible(child.id))
        assertNull(index.ownerOfObject(root.id))
        assertTrue(MindMapPageIndex(Page(), emptyList()).visibleObjects.isEmpty())
    }

    @Test fun treeInputsAndExposedCollectionsCannotChangePreparedOwnershipOrVisibility() {
        val input = arrayListOf(tree)
        val index = MindMapPageIndex(page, input)
        input.clear()
        assertThrows(UnsupportedOperationException::class.java) { (index.trees as MutableList).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (index.visibleObjects as MutableList).clear() }
        assertEquals(page.elements, index.visibleObjects)
        assertSame(tree, index.ownerOfObject(child.id))
    }

    @Test fun cancelledPreparationDoesNotAffectAnExistingIndexOrTheSourcePage() {
        val existing = MindMapPageIndex(page, listOf(tree))
        var calls = 0
        MindMapPageIndex(page, listOf(tree)) { calls++ }
        var visits = 0
        assertThrows(CancellationException::class.java) {
            MindMapPageIndex(page, listOf(tree)) { if (++visits == calls - 1) throw CancellationException() }
        }
        assertEquals(page.elements, existing.visibleObjects)
        assertSame(tree, existing.ownerOfObject(managed.id))
    }

    @Test fun excessiveMapCountsAreRejectedBeforePreparingObjectOrTreeIndexes() {
        var visits = 0
        assertThrows(IllegalArgumentException::class.java) {
            MindMapPageIndex(page, List(MindMapPageIndex.MAX_TREES + 1) { tree }) { visits++ }
        }
        assertEquals(1, visits)
    }
}
