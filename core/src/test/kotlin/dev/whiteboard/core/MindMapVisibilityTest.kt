package dev.whiteboard.core

import dev.whiteboard.core.editor.FillEngine
import dev.whiteboard.core.model.*
import org.junit.Assert.*
import org.junit.Test

class MindMapVisibilityTest {
    private val root = Note(id = "root", bounds = Bounds(0f, 0f, 100f, 80f), text = "Root")
    private val child = root.copy(id = "child", bounds = Bounds(300f, 200f, 400f, 280f), text = "Hidden")
    private val external = root.copy(id = "external", bounds = Bounds(600f, 0f, 700f, 80f))
    private val edge =
        Connector("edge", ConnectorEnd.Bound(root.id, ConnectionPort.RIGHT), ConnectorEnd.Bound(child.id, ConnectionPort.LEFT))
    private val link = edge.copy(
        id = "external-edge",
        start = ConnectorEnd.Bound(child.id, ConnectionPort.RIGHT),
        end = ConnectorEnd.Bound(external.id, ConnectionPort.LEFT)
    )
    private val tree = MindMapTree("map", listOf(MindMapNode(root.id), MindMapNode(child.id, root.id, edge.id)))
    private val page = Page(elements = listOf(root, edge, child), mindMaps = listOf(tree))
    private val collapsed = page.copy(mindMaps = listOf(tree.withCollapsed(root.id, true)))

    @Test fun visibleFrameExcludesHiddenObjectsWithoutChangingTheCanonicalFrameOrDocument() {
        assertEquals(listOf(root), collapsed.visibleElements)
        assertEquals(root.renderBounds, collapsed.visibleContentBounds)
        assertSame(page.contentBounds, collapsed.contentBounds)
        assertTrue(checkNotNull(collapsed.contentBounds).right >= child.bounds.right)
        assertEquals(page.elements, collapsed.elements)
        assertEquals(page.mindMaps.single().nodes.size, collapsed.mindMaps.single().nodes.size)
    }

    @Test fun plainAndFullyExpandedPagesReuseCanonicalFramesAndLists() {
        val plain = Page(elements = listOf(root, child))
        assertSame(plain.elements, plain.visibleElements)
        assertSame(plain.contentBounds, plain.visibleContentBounds)
        assertSame(page.contentBounds, page.visibleContentBounds)
        assertEquals(page.elements, page.visibleElements)
        assertNull(Page().visibleContentBounds)
    }

    @Test fun hiddenObjectsCannotBePickedOrUsedAsConnectionTargetsButExplicitGeometryStillWorks() {
        assertNull(collapsed.geometry.topmostAt(child.bounds.center, 0f))
        assertNull(collapsed.geometry.connectionTargetAt(child.bounds.center, 0f))
        assertEquals(listOf(root), collapsed.geometry.connectionTargets())
        assertEquals(child, page.geometry.topmostAt(child.bounds.center, 0f))
        assertEquals(child.renderBounds, collapsed.geometry.renderBounds(child))
        assertTrue(collapsed.geometry.hitTest(child, child.bounds.center, 0f))
    }

    @Test fun hiddenTopmostNoteDoesNotBlockAnUnrelatedVisibleSurface() {
        val underneath = child.copy(id = "underneath")
        val source = collapsed.copy(elements = listOf(underneath) + collapsed.elements)
        assertEquals(underneath, source.geometry.topmostAt(child.bounds.center, 0f))
        assertEquals(FillEngine.Target.Surface(underneath, underneath.id), FillEngine.target(source, child.bounds.center))
        assertEquals(FillEngine.Target.Background, FillEngine.target(collapsed, child.bounds.center))
    }

    @Test fun ordinaryConnectionsWithHiddenEndsDoNotAppearInTheVisibleFrameOrPicking() {
        val source = collapsed.copy(elements = collapsed.elements + external + link)
        assertEquals(listOf(root, external), source.visibleElements)
        val line = source.geometry.resolve(link) as Line
        val middle = Point((line.start.x + line.end.x) / 2, (line.start.y + line.end.y) / 2)
        assertNull(source.geometry.topmostAt(middle, 1f))
        assertEquals(root.renderBounds.union(external.renderBounds), source.visibleContentBounds)
        assertEquals(listOf(root, external), source.geometry.connectionTargets())
    }

    @Test fun metadataCopiesReuseTheVisibleFrameButSemanticChangesRecomputeIt() {
        val frame = collapsed.visibleContentBounds
        val copy = collapsed.copy(name = "Renamed", viewport = Viewport(scale = 2f))
        assertSame(frame, copy.visibleContentBounds)
        assertSame(collapsed.visibleElements, copy.visibleElements)
        val expanded = collapsed.copy(mindMaps = listOf(tree))
        assertSame(expanded.contentBounds, expanded.visibleContentBounds)
        assertEquals(page.visibleElements, expanded.visibleElements)
        assertEquals(listOf(root), collapsed.visibleElements)
    }

    @Test fun warmPreviewUsesNewVisibleGeometryWhileRetainingHiddenCanonicalContent() {
        collapsed.contentBounds
        val moved = root.copy(bounds = Bounds(-300f, -200f, -200f, -120f))
        val preview = collapsed.geometry.preview(listOf(moved)).page
        assertEquals(listOf(moved), preview.visibleElements)
        assertEquals(moved.renderBounds, preview.visibleContentBounds)
        assertTrue(checkNotNull(preview.contentBounds).right >= child.bounds.right)
        assertEquals(root.renderBounds, collapsed.visibleContentBounds)
        assertNull(preview.geometry.topmostAt(child.bounds.center, 0f))
    }

    @Test fun rotatedVisibleNodesUseTheirWorldBoundsAndTheSamePortSupport() {
        val rotated = AffineElement.fromDocument(root, AffineTransform2D.rotation(31.0, root.bounds.center))
        val source = collapsed.copy(elements = listOf(rotated, edge, child))
        assertEquals(rotated.renderBounds, source.visibleContentBounds)
        assertEquals(listOf(rotated), source.geometry.connectionTargets())
        assertEquals(rotated, source.geometry.topmostAt(root.bounds.center, 0f))
    }
}
