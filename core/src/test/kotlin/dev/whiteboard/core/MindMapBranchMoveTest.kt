package dev.whiteboard.core

import dev.whiteboard.core.editor.*
import dev.whiteboard.core.model.*
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

class MindMapBranchMoveTest {
    private val root = Note(id = "root", bounds = Bounds(0f, 0f, 100f, 80f), text = "Root")
    private val child = root.copy(id = "child", bounds = Bounds(200f, 0f, 300f, 80f), text = "Child")
    private val leaf = root.copy(id = "leaf", bounds = Bounds(400f, 0f, 500f, 80f), text = "Hidden leaf")
    private val outside = root.copy(id = "outside", bounds = Bounds(600f, 200f, 700f, 280f))
    private fun edge(id: String, start: String, end: String) =
        Connector(id, ConnectorEnd.Bound(start, ConnectionPort.RIGHT), ConnectorEnd.Bound(end, ConnectionPort.LEFT))
    private val first = edge("first", root.id, child.id)
    private val second = edge("second", child.id, leaf.id)
    private val external = edge("external", leaf.id, outside.id)
    private val tree =
        MindMapTree(
            "map",
            listOf(
                MindMapNode(root.id),
                MindMapNode(child.id, root.id, first.id, collapsed = true),
                MindMapNode(leaf.id, child.id, second.id)
            )
        )
    private val page = Page(elements = listOf(root, first, child, second, leaf, outside, external), mindMaps = listOf(tree))
    private val delta = Point(40f, 60f)

    @Test fun aCollapsedBranchMovesHiddenDescendantsAndAllBoundLinksFollowWithoutExpandingIt() {
        val session = DocumentSession(Board(name = "Move", pages = listOf(page)))
        val plan = checkNotNull(MindMapEdits.moveBranch(page, child.id, delta))
        assertTrue(session.applyMindMapEdit(plan))
        val changed = session.page
        assertEquals(page.mindMaps, changed.mindMaps)
        assertEquals(child.bounds.center + delta, changed.geometry.selectionBounds(changed.elements.first { it.id == child.id }).center)
        assertEquals(leaf.bounds.center + delta, changed.geometry.selectionBounds(changed.elements.first { it.id == leaf.id }).center)
        assertEquals(root, changed.elements.first { it.id == root.id })
        assertEquals(outside, changed.elements.first { it.id == outside.id })
        assertFalse(changed.mindMapIndex!!.isVisible(leaf.id))
        assertEquals(page.elements.filterIsInstance<Connector>(), changed.elements.filterIsInstance<Connector>())
        assertEquals((page.geometry.resolve(external) as Line).start + delta, (changed.geometry.resolve(external) as Line).start)
        assertEquals((page.geometry.resolve(external) as Line).end, (changed.geometry.resolve(external) as Line).end)
        assertEquals(1L, session.revision)
        session.undo()
        assertEquals(page, session.page)
        session.redo()
        assertEquals(changed, session.page)
    }

    @Test fun affineChildrenKeepTheirLocalContentAndPoseWhileTheirWorldTranslationChanges() {
        val rotated = AffineElement.fromDocument(child, AffineTransform2D.rotation(33.0, child.bounds.center))
        val source = page.copy(elements = page.elements.map { if (it.id == child.id) rotated else it })
        val session = DocumentSession(Board(name = "Affine branch", pages = listOf(source)))
        session.applyMindMapEdit(checkNotNull(MindMapEdits.moveBranch(source, child.id, delta)))
        val result = session.page.elements.first { it.id == child.id } as AffineElement
        assertSame(rotated.source, result.source)
        assertEquals(rotated.placement.xx, result.placement.xx, 0.0)
        assertEquals(rotated.placement.yx, result.placement.yx, 0.0)
        assertEquals(rotated.renderBounds.left + delta.x, result.renderBounds.left, 0.001f)
        assertEquals(rotated.renderBounds.top + delta.y, result.renderBounds.top, 0.001f)
        assertEquals(source.mindMaps, session.page.mindMaps)
    }

    @Test fun movingTheRootIncludesAllMapNodesButLeavesUnrelatedObjectsAtTheirOriginalPosition() {
        val session = DocumentSession(Board(name = "Whole map", pages = listOf(page)))
        session.applyMindMapEdit(checkNotNull(MindMapEdits.moveBranch(page, root.id, delta)))
        assertEquals(outside, session.page.elements.first { it.id == outside.id })
        for (note in listOf(root, child, leaf)) {
            assertEquals(
                note.bounds.center + delta,
                session.page.geometry.selectionBounds(
                    session.page.elements.first {
                        it.id == note.id
                    }
                ).center
            )
        }
        assertEquals(tree, session.page.mindMaps.single())
    }

    @Test fun zeroMovementLimitsCancellationAndInvalidCoordinatesCannotPublishAPartialBranch() {
        assertNull(MindMapEdits.moveBranch(page, child.id, Point(0f, 0f)))
        val failure = assertThrows(DocumentFailure::class.java) { MindMapEdits.moveBranch(page, child.id, delta, byteBudget = 1) }
        assertEquals(DocumentError.EDIT_LIMIT, failure.error)
        assertThrows(CancellationException::class.java) {
            MindMapEdits.moveBranch(page, root.id, delta, checkpoint = { throw CancellationException() })
        }
        assertThrows(IllegalArgumentException::class.java) { MindMapEdits.moveBranch(page, child.id, Point(1_000_000_000f, 0f)) }
        assertEquals(child, page.elements.first { it.id == child.id })
        assertEquals(tree, page.mindMaps.single())
    }

    @Test fun deletionPreviewCountsHiddenObjectsButDoesNotCountARewrittenExternalLinkAsRemoved() {
        val plan = checkNotNull(MindMapEdits.deleteSelection(page, setOf(child.id, leaf.id, first.id)))
        assertEquals(2, plan.removedNodeCount)
        assertEquals(4, plan.removedObjectCount)
        val move = checkNotNull(MindMapEdits.moveBranch(page, child.id, delta))
        assertEquals(0, move.removedNodeCount)
        assertEquals(0, move.removedObjectCount)
    }
}
