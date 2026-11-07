package dev.whiteboard.core

import dev.whiteboard.core.editor.*
import dev.whiteboard.core.model.*
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class MindMapEditsTest {
    private val root = Note(id = "root", bounds = Bounds(0f, 0f, 100f, 80f), text = "Root", color = 0xFF123456.toInt())
    private val a = root.copy(id = "a", bounds = Bounds(200f, 0f, 300f, 80f), text = "A")
    private val b = root.copy(id = "b", bounds = Bounds(200f, 200f, 300f, 280f), text = "B")
    private val leaf = root.copy(id = "leaf", bounds = Bounds(400f, 0f, 500f, 80f), text = "Leaf")
    private val external = root.copy(id = "external", bounds = Bounds(600f, 200f, 700f, 280f))
    private fun edge(id: String, first: String, last: String) =
        Connector(id, ConnectorEnd.Bound(first, ConnectionPort.RIGHT), ConnectorEnd.Bound(last, ConnectionPort.LEFT))
    private val ea = edge("edge-a", root.id, a.id)
    private val eb = edge("edge-b", root.id, b.id)
    private val el = edge("edge-leaf", a.id, leaf.id)
    private val link = edge("external-edge", leaf.id, external.id)
    private val tree =
        MindMapTree(
            "map",
            listOf(
                MindMapNode(root.id),
                MindMapNode(a.id, root.id, ea.id),
                MindMapNode(leaf.id, a.id, el.id),
                MindMapNode(b.id, root.id, eb.id)
            )
        )
    private val page = Page(elements = listOf(root, ea, a, el, leaf, eb, b, external, link), mindMaps = listOf(tree))
    private fun session(source: Page = page, budget: Long = 32L * 1024 * 1024) =
        DocumentSession(Board(name = "Map", pages = listOf(source)), historyByteLimit = budget)
    private fun ids(prefix: String = "new"): () -> String {
        var value = 0
        return { "$prefix-${++value}" }
    }

    @Test fun creatingAMapAvoidsExistingObjectsAndHasOneStableUndoRedoEntry() {
        val obstacle = Shape(id = "obstacle", bounds = Bounds(-100f, -80f, 200f, 200f), kind = Tool.RECTANGLE, style = InkStyle())
        val source = Page(elements = listOf(obstacle))
        val session = session(source)
        val plan = MindMapEdits.create(source, Point(0f, 0f), "Ideja Ћирилица", root.color, identity = ids())
        assertTrue(session.applyMindMapEdit(plan))
        val changed = session.page
        assertEquals(1L, session.revision)
        val note = changed.elements.filterIsInstance<Note>().single()
        assertFalse(note.bounds.intersects(obstacle.bounds))
        assertEquals(note.id, changed.mindMaps.single().rootId)
        assertEquals(note.id, plan.focusId)
        session.undo()
        assertEquals(source, session.page)
        session.redo()
        assertEquals(changed, session.page)
    }

    @Test fun addingAChildRevealsItsAncestorsAndKeepsAllOriginalPositionsAndContent() {
        val hidden = tree.withCollapsed(root.id, true).withCollapsed(a.id, true)
        val source = page.copy(mindMaps = listOf(hidden))
        val session = session(source)
        val plan = MindMapEdits.addChild(source, leaf.id, "New child", identity = ids())
        assertTrue(session.applyMindMapEdit(plan))
        val changed = session.page
        assertEquals(
            listOf(root.id, a.id, leaf.id, plan.focusId),
            changed.mindMaps.single().pathTo(checkNotNull(plan.focusId)).map {
                it.id
            }
        )
        assertTrue(checkNotNull(changed.mindMapIndex).isVisible(checkNotNull(plan.focusId)))
        assertEquals(source.elements, changed.elements.filter { it.id in source.elements.map { old -> old.id } })
        assertEquals(root.color, (changed.elements.last() as Note).color)
        assertTrue(hidden.node(root.id).collapsed)
        session.undo()
        assertEquals(source, session.page)
    }

    @Test fun siblingOrderIsExplicitAndARootCannotAcquireASibling() {
        val session = session()
        val plan = MindMapEdits.addSibling(page, a.id, "Sibling", identity = ids())
        assertTrue(session.applyMindMapEdit(plan))
        assertEquals(listOf(a.id, plan.focusId, b.id), session.page.mindMaps.single().childrenOf(root.id).map { it.id })
        assertThrows(IllegalArgumentException::class.java) { MindMapEdits.addSibling(page, root.id, "Invalid") }
    }

    @Test fun editingARotatedNodeKeepsItsWorldPoseAndUsesTheSingleNoteContentOwner() {
        val rotated = AffineElement.fromDocument(a, AffineTransform2D.rotation(37.0, a.bounds.center))
        val source = page.copy(elements = page.elements.map { if (it.id == a.id) rotated else it })
        val session = session(source)
        val before = rotated.renderBounds
        val plan = checkNotNull(MindMapEdits.edit(source, a.id, "Promenjen naslov", 0xFF00FF00.toInt()))
        assertTrue(session.applyMindMapEdit(plan))
        val changed = session.page.elements.first { it.id == a.id } as AffineElement
        assertEquals(before.left, changed.renderBounds.left, 0.001f)
        assertEquals(before.bottom, changed.renderBounds.bottom, 0.001f)
        assertEquals("Promenjen naslov", (changed.content() as Note).text)
        assertEquals(0xFF00FF00.toInt(), (changed.content() as Note).color)
        session.undo()
        assertEquals(source, session.page)
    }

    @Test fun collapseIsAStandaloneSemanticUndoWithoutReplacingCanonicalObjects() {
        val session = session()
        val frame = page.contentBounds
        assertTrue(session.applyMindMapEdit(checkNotNull(MindMapEdits.collapse(page, root.id, true))))
        val collapsed = session.page
        assertSame(page.elements, collapsed.elements)
        assertSame(frame, collapsed.contentBounds)
        assertEquals(listOf(root, external), collapsed.mindMapIndex?.visibleObjects)
        session.undo()
        assertEquals(page, session.page)
        session.redo()
        assertEquals(collapsed, session.page)
    }

    @Test fun editingHiddenContentDoesNotExpandItAndRevealIsAnExplicitSeparateCommand() {
        val source = page.copy(mindMaps = listOf(tree.withCollapsed(root.id, true)))
        val session = session(source)
        val edit = checkNotNull(MindMapEdits.edit(source, leaf.id, "Hidden edit", leaf.color))
        assertEquals(root.id, edit.focusId)
        assertTrue(session.applyMindMapEdit(edit))
        assertFalse(checkNotNull(session.page.mindMapIndex).isVisible(leaf.id))
        assertTrue(session.applyMindMapEdit(checkNotNull(MindMapEdits.reveal(session.page, leaf.id))))
        assertTrue(checkNotNull(session.page.mindMapIndex).isVisible(leaf.id))
        session.undo()
        assertFalse(checkNotNull(session.page.mindMapIndex).isVisible(leaf.id))
    }

    @Test fun reparentUpdatesTheRelationAndItsBoundConnectionAsOneUndoableEdit() {
        val source = page.copy(mindMaps = listOf(tree.withCollapsed(b.id, true)))
        val session = session(source)
        val plan = checkNotNull(MindMapEdits.reparent(source, leaf.id, b.id))
        assertTrue(session.applyMindMapEdit(plan))
        assertEquals(b.id, session.page.mindMaps.single().node(leaf.id).parentId)
        val changedEdge = session.page.elements.first { it.id == el.id } as Connector
        assertEquals(ConnectorEnd.Bound(b.id, ConnectionPort.RIGHT), changedEdge.start)
        assertEquals(leaf.bounds, (session.page.elements.first { it.id == leaf.id } as Note).bounds)
        assertTrue(checkNotNull(session.page.mindMapIndex).isVisible(leaf.id))
        session.undo()
        assertEquals(source, session.page)
        session.redo()
        assertEquals(b.id, session.page.mindMaps.single().node(leaf.id).parentId)
    }

    @Test fun cyclesCrossMapParentsRootMovesAndUnknownTargetsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { MindMapEdits.reparent(page, a.id, leaf.id) }
        assertThrows(IllegalArgumentException::class.java) { MindMapEdits.reparent(page, root.id, b.id) }
        assertThrows(IllegalArgumentException::class.java) { MindMapEdits.reparent(page, leaf.id, "foreign") }
        val other = MindMapTree("other-map", listOf(MindMapNode(external.id)))
        val source = page.copy(mindMaps = listOf(tree, other))
        assertThrows(IllegalArgumentException::class.java) { MindMapEdits.reparent(source, leaf.id, external.id) }
        assertEquals(a.id, tree.node(leaf.id).parentId)
    }

    @Test fun aNewParentUsesFacingBoundaryPortsInsteadOfKeepingTheOldParentsUnrelatedDirection() {
        val session = session()
        session.applyMindMapEdit(checkNotNull(MindMapEdits.reparent(page, a.id, b.id)))
        val relation = session.page.elements.first { it.id == ea.id } as Connector
        assertEquals(ConnectorEnd.Bound(b.id, ConnectionPort.TOP), relation.start)
        assertEquals(ConnectorEnd.Bound(a.id, ConnectionPort.BOTTOM), relation.end)
        assertEquals(ea.style, relation.style)
        assertEquals(ea.arrow, relation.arrow)
        assertEquals(a.bounds, (session.page.elements.first { it.id == a.id } as Note).bounds)
        session.undo()
        assertEquals(page, session.page)
    }

    @Test fun overlappingBranchSelectionRemovesHiddenDescendantsAndDetachesExternalLinksAtomically() {
        val source = page.copy(mindMaps = listOf(tree.withCollapsed(a.id, true)))
        val session = session(source)
        val last = (source.geometry.resolve(link) as Line).start
        session.delete(setOf(a.id, leaf.id, ea.id, el.id))
        assertEquals(setOf(root.id, b.id), session.page.mindMaps.single().nodes.map { it.id }.toSet())
        assertEquals(1L, session.revision)
        val detached = session.page.elements.first { it.id == link.id } as Connector
        assertEquals(ConnectorEnd.Free(last), detached.start)
        assertEquals(link.end, detached.end)
        session.undo()
        assertEquals(source, session.page)
        session.redo()
        assertEquals(ConnectorEnd.Free(last), (session.page.elements.first { it.id == link.id } as Connector).start)
    }

    @Test fun deletingAManagedEdgeDeletesItsChildBranchAndDeletingTheRootRemovesTheMap() {
        val session = session()
        session.delete(setOf(eb.id))
        assertFalse(session.page.elements.any { it.id == b.id || it.id == eb.id })
        session.undo()
        session.delete(setOf(root.id))
        assertTrue(session.page.mindMaps.isEmpty())
        assertEquals(listOf(external.id, link.id), session.page.elements.map { it.id })
        session.undo()
        assertEquals(page, session.page)
    }

    @Test fun multipleMapsAndUnrelatedSelectedObjectsShareOneDeleteAndUndo() {
        val otherRoot = root.copy(id = "other-root", bounds = Bounds(800f, 0f, 900f, 80f))
        val other = MindMapTree("other-map", listOf(MindMapNode(otherRoot.id)))
        val source = page.copy(elements = page.elements + otherRoot, mindMaps = listOf(tree, other))
        val session = session(source)
        session.delete(setOf(a.id, otherRoot.id))
        assertEquals(listOf(tree.id), session.page.mindMaps.map { it.id })
        assertFalse(session.page.elements.any { it.id == otherRoot.id || it.id == leaf.id })
        session.undo()
        assertEquals(source, session.page)
    }

    @Test fun noOpEditsDoNotCreateHistoryAndManagedNodesCannotBeGrouped() {
        assertNull(MindMapEdits.collapse(page, root.id, false))
        assertNull(MindMapEdits.edit(page, a.id, a.text, a.color))
        assertNull(MindMapEdits.reparent(page, leaf.id, a.id))
        val session = session()
        assertNull(session.group(setOf(root.id, a.id)))
        assertFalse(session.canUndo)
        assertEquals(page, session.page)
    }

    @Test fun planAndSessionBudgetsBothRejectBeforePublishingAnEdit() {
        val failure = assertThrows(DocumentFailure::class.java) { MindMapEdits.collapse(page, root.id, true, byteBudget = 1) }
        assertEquals(DocumentError.EDIT_LIMIT, failure.error)
        val source = Page()
        val session = session(source, 1)
        val plan = MindMapEdits.create(source, Point(0f, 0f), "Root", root.color)
        assertThrows(DocumentFailure::class.java) { session.applyMindMapEdit(plan) }
        assertEquals(source, session.page)
        assertFalse(session.canUndo)
        assertEquals(0L, session.revision)
    }

    @Test fun cancellationAndInvalidIdentitySourcesCannotMutateTheOriginalSession() {
        val session = session()
        var calls = 0
        assertThrows(CancellationException::class.java) {
            MindMapEdits.addChild(page, root.id, "Cancelled", checkpoint = { if (++calls == 25) throw CancellationException() })
        }
        assertThrows(IllegalArgumentException::class.java) { MindMapEdits.addChild(page, root.id, "Invalid", identity = { tree.id }) }
        assertThrows(IllegalArgumentException::class.java) {
            MindMapEdits.create(page, Point(0f, 0f), "Invalid", root.color, identity = { "same-id" })
        }
        assertEquals(page, session.page)
        assertFalse(session.canUndo)
    }

    @Test fun plansAreRejectedAfterGeometryOrSemanticChangesButCameraChangesRemainIndependent() {
        val session = session()
        val stale = MindMapEdits.addChild(page, root.id, "Stale", identity = ids())
        session.replace(a.copy(text = "Changed elsewhere"))
        assertFalse(session.applyMindMapEdit(stale))
        val current = session.page
        val staleSemantic = MindMapEdits.addChild(current, root.id, "Stale after collapse", identity = ids())
        session.applyMindMapEdit(checkNotNull(MindMapEdits.collapse(current, root.id, true)))
        assertFalse(session.applyMindMapEdit(staleSemantic))
        val fresh = MindMapEdits.addChild(session.page, root.id, "Fresh", identity = ids())
        val camera = Viewport(offset = Point(20f, 30f), scale = 2f)
        session.updateViewport(camera)
        assertTrue(session.applyMindMapEdit(fresh))
        assertEquals(camera, session.page.viewport)
    }

    @Test fun concurrentPreparationCannotLoseAnEditOrModifyTheSharedSource() {
        val executor = Executors.newFixedThreadPool(4)
        try {
            val plans = (0 until 8).map { index ->
                executor.submit<MindMapEditPlan> { MindMapEdits.addChild(page, root.id, "Child $index", identity = ids("task-$index")) }
            }
                .map { it.get(10, TimeUnit.SECONDS) }
            val session = session()
            assertTrue(session.applyMindMapEdit(plans.first()))
            plans.drop(1).forEach { assertFalse(session.applyMindMapEdit(it)) }
            assertEquals(tree.nodes.size + 1, session.page.mindMaps.single().nodes.size)
            assertEquals(4, page.mindMaps.single().nodes.size)
        } finally {
            executor.shutdownNow()
        }
    }
}
