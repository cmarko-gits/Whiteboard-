package dev.whiteboard.core

import dev.whiteboard.core.editor.ConnectionCopies
import dev.whiteboard.core.editor.DocumentSession
import dev.whiteboard.core.editor.PageListChange
import dev.whiteboard.core.editor.SelectionEditPlan
import dev.whiteboard.core.model.*
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

class MindMapPageSemanticsTest {
    private val root = Note(id = "root", bounds = Bounds(0f, 0f, 100f, 80f), text = "Root")
    private val child = root.copy(id = "child", bounds = Bounds(200f, 120f, 300f, 200f), text = "Child")
    private val edge =
        Connector(
            id = "edge",
            start = ConnectorEnd.Bound(root.id, ConnectionPort.RIGHT),
            end = ConnectorEnd.Bound(child.id, ConnectionPort.LEFT)
        )
    private val tree = MindMapTree("map", listOf(MindMapNode(root.id), MindMapNode(child.id, root.id, edge.id)))
    private val page = Page(elements = listOf(root, edge, child), mindMaps = listOf(tree))
    private fun collapsed() = page.copy(mindMaps = listOf(tree.withCollapsed(root.id, true)))

    @Test fun metadataSharesTheSceneButCollapseChangesSemanticIdentityWithoutRebuildingCanonicalGeometry() {
        val bounds = page.contentBounds
        val ports = page.geometry.connectionPorts()
        val metadata = page.copy(name = "Renamed", backgroundColor = 0, viewport = Viewport(offset = Point(20f, 30f)))
        assertTrue(page.sharesSceneWith(metadata))
        assertSame(page.mindMapIndex, metadata.mindMapIndex)
        val collapsed = collapsed()
        assertFalse(page.sharesSceneWith(collapsed))
        assertSame(bounds, collapsed.contentBounds)
        assertSame(ports, collapsed.geometry.connectionPorts())
        assertEquals(listOf(root), collapsed.mindMapIndex?.visibleObjects)
        assertEquals(page.elements, collapsed.elements)
    }

    @Test fun semanticValueEqualityAndHashingIncludeTopologyWithoutDependingOnIndexIdentity() {
        val copy = Page(id = page.id, elements = page.elements.toList(), mindMaps = listOf(MindMapTree(tree.id, tree.nodes)))
        assertEquals(page, copy)
        assertEquals(page.hashCode(), copy.hashCode())
        assertFalse(page.sharesSceneWith(copy))
        assertNotEquals(page, collapsed())
        assertSame(page.mindMaps, page.component7())
        assertFalse(page.sharesSceneWith(page.copy(id = "different-page")))
    }

    @Test fun callerMutationCannotAddOrRemoveSemanticTreesFromAnExistingPage() {
        val input = arrayListOf(tree)
        val copy = page.copy(mindMaps = input)
        input.clear()
        assertEquals(listOf(tree), copy.mindMaps)
        assertThrows(UnsupportedOperationException::class.java) { (copy.mindMaps as MutableList).clear() }
        val empty = arrayListOf<MindMapTree>()
        val plain = page.copy(mindMaps = empty)
        empty.add(tree)
        assertTrue(plain.mindMaps.isEmpty())
        assertNull(plain.mindMapIndex)
    }

    @Test fun warmPreviewRebindsVisibleObjectsAndPortsWhileSharingValidatedTopology() {
        val source = collapsed().also { it.contentBounds }
        val moved = root.copy(bounds = Bounds(0f, 50f, 100f, 130f))
        val preview = source.geometry.preview(listOf(moved)).page
        assertSame(source.mindMaps, preview.mindMaps)
        assertNotSame(source.mindMapIndex, preview.mindMapIndex)
        assertEquals(listOf(moved), preview.mindMapIndex?.visibleObjects)
        assertEquals(Point(100f, 90f), (preview.geometry.resolve(edge) as Line).start)
        assertEquals(Point(100f, 40f), (source.geometry.resolve(edge) as Line).start)
        assertTrue(checkNotNull(preview.mindMapIndex).matchesGeometry(preview))
        assertFalse(source.sharesSceneWith(preview))
    }

    @Test fun structuralPreviewUsesFullSemanticBindingValidation() {
        page.contentBounds
        val reversed = edge.copy(start = edge.end, end = edge.start)
        assertThrows(IllegalArgumentException::class.java) { page.geometry.preview(listOf(reversed)) }
        val rotated = AffineElement.fromDocument(child, AffineTransform2D.rotation(37.0, child.bounds.center))
        val preview = page.geometry.preview(listOf(rotated)).page
        assertEquals(listOf(tree), preview.mindMaps)
        assertSame(rotated, preview.elements.last())
        assertSame(child, page.elements.last())
    }

    @Test fun cancelledSemanticPreviewLeavesBothOriginalGeometryAndVisibilityReusable() {
        val source = collapsed().also { it.contentBounds }
        val moved = root.copy(bounds = Bounds(0f, 50f, 100f, 130f))
        var total = 0
        source.geometry.preview(listOf(moved)) { total++ }
        var calls = 0
        assertThrows(CancellationException::class.java) {
            source.geometry.preview(listOf(moved)) { if (++calls == total - 2) throw CancellationException() }
        }
        assertEquals(listOf(root), source.mindMapIndex?.visibleObjects)
        assertEquals(listOf(moved), source.geometry.preview(listOf(moved)).page.mindMapIndex?.visibleObjects)
    }

    @Test fun aSelectionPlanCannotApplyAfterCollapseEvenWhenTheElementListIsIdentical() {
        val session = DocumentSession(Board(name = "Map", pages = listOf(page)))
        val moved = root.copy(bounds = Bounds(0f, 50f, 100f, 130f))
        val stale = SelectionEditPlan(page, listOf(moved))
        val collapsed = collapsed()
        session.execute(PageListChange(listOf(page), listOf(collapsed), page.id, page.id))
        assertSame(page.elements, session.page.elements)
        assertFalse(session.applySelectionEdit(stale))
        assertTrue(session.applySelectionEdit(SelectionEditPlan(session.page, listOf(moved))))
        assertEquals(collapsed.mindMaps, session.page.mindMaps)
        session.undo()
        assertEquals(collapsed, session.page)
        session.undo()
        assertEquals(page, session.page)
    }

    @Test fun existingRotationAndUndoKeepSemanticOwnershipAndExactCanonicalObjects() {
        val session = DocumentSession(Board(name = "Map", pages = listOf(page)))
        assertTrue(session.rotate(setOf(child.id), 37.0))
        assertTrue(session.page.elements.last() is AffineElement)
        assertEquals(tree, session.page.mindMapIndex?.ownerOfObject(child.id))
        session.undo()
        assertEquals(page, session.page)
        session.redo()
        assertEquals(listOf(tree), session.page.mindMaps)
    }

    @Test fun anUnsupportedNodeKindCannotPublishBrokenSemanticsOrHistory() {
        val session = DocumentSession(Board(name = "Map", pages = listOf(page)))
        val original = session.board
        assertThrows(IllegalArgumentException::class.java) {
            session.replace(TextElement(id = child.id, bounds = child.bounds, text = child.text))
        }
        assertSame(original, session.board)
        assertEquals(0L, session.revision)
        assertFalse(session.canUndo)
        assertEquals(listOf(tree), session.page.mindMaps)
    }

    @Test fun duplicatePageRekeysCompleteTopologyAndUndoRedoKeepsTheSameCopiedIdentities() {
        val source = collapsed()
        val session = DocumentSession(Board(name = "Map", pages = listOf(source)))
        session.duplicatePage(source.id)
        val copied = session.page
        assertNotEquals(source.id, copied.id)
        assertNotEquals(tree.id, copied.mindMaps.single().id)
        assertTrue(copied.elements.map { it.id }.toSet().intersect(source.elements.map { it.id }.toSet() + tree.id).isEmpty())
        assertTrue(copied.mindMaps.single().node(copied.mindMaps.single().rootId).collapsed)
        assertEquals(listOf(root.text, child.text), copied.elements.filterIsInstance<Note>().map { it.text })
        assertEquals(1, copied.mindMapIndex?.visibleObjects?.size)
        copied.mindMaps.single().validateObjects(copied)
        session.undo()
        assertEquals(listOf(source), session.board.pages)
        session.redo()
        assertEquals(copied, session.page)
        assertThrows(IllegalArgumentException::class.java) {
            ConnectionCopies.duplicate(page, listOf(edge), page.elements.map { it.id }.toSet(), identity = { tree.id })
        }
    }

    @Test fun deletingAnOrdinaryExternalTargetDetachesItsConnectionWithoutDroppingTheMap() {
        val external = root.copy(id = "external", bounds = Bounds(400f, 0f, 500f, 80f))
        val externalEdge = edge.copy(id = "external-edge", end = ConnectorEnd.Bound(external.id, ConnectionPort.LEFT))
        val source = page.copy(elements = page.elements + external + externalEdge)
        val session = DocumentSession(Board(name = "Map", pages = listOf(source)))
        val position = (source.geometry.resolve(externalEdge) as Line).end
        session.delete(setOf(external.id))
        assertEquals(listOf(tree), session.page.mindMaps)
        val detached = session.page.elements.filterIsInstance<Connector>().first { it.id == externalEdge.id }
        assertEquals(ConnectorEnd.Free(position), detached.end)
        session.undo()
        assertEquals(source, session.page)
    }

    @Test fun aMapPageChangeThatCannotRetainItsOwnUndoIsRejectedBeforeAnyMutation() {
        val original = Board(name = "Map", pages = listOf(page))
        val session = DocumentSession(original, historyByteLimit = 1)
        val failure = assertThrows(DocumentFailure::class.java) { session.duplicatePage(page.id) }
        assertEquals(DocumentError.EDIT_LIMIT, failure.error)
        assertSame(original, session.board)
        assertEquals(0L, session.revision)
        assertFalse(session.canUndo)
    }

    @Test fun aChangedExternalBindingRecomputesVisibilityInsteadOfReusingTheOldHiddenEdgeSet() {
        val external = root.copy(id = "external", bounds = Bounds(400f, 0f, 500f, 80f))
        val link = edge.copy(
            id = "external-edge",
            start = ConnectorEnd.Bound(child.id, ConnectionPort.RIGHT),
            end = ConnectorEnd.Bound(external.id, ConnectionPort.LEFT)
        )
        val source = collapsed().copy(elements = page.elements + external + link).also { it.contentBounds }
        assertFalse(checkNotNull(source.mindMapIndex).isVisible(link.id))
        val changed = link.copy(start = ConnectorEnd.Free(Point(350f, 40f)))
        val preview = source.geometry.preview(listOf(changed)).page
        assertTrue(checkNotNull(preview.mindMapIndex).isVisible(link.id))
        assertFalse(checkNotNull(source.mindMapIndex).isVisible(link.id))
        assertEquals(source.mindMaps, preview.mindMaps)
    }
}
