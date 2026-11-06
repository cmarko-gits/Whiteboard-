package dev.whiteboard.core

import dev.whiteboard.core.editor.*
import dev.whiteboard.core.model.*
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

class MindMapClipboardTest {
    private val root = Note(id = "root", bounds = Bounds(0f, 0f, 100f, 80f), text = "Root")
    private val a = root.copy(id = "a", bounds = Bounds(200f, 0f, 300f, 80f), text = "A")
    private val leaf = root.copy(id = "leaf", bounds = Bounds(400f, 0f, 500f, 80f), text = "Hidden leaf")
    private val b = root.copy(id = "b", bounds = Bounds(200f, 200f, 300f, 280f), text = "B")
    private val external = root.copy(id = "external", bounds = Bounds(600f, 200f, 700f, 280f))
    private fun edge(id: String, start: String, end: String) =
        Connector(id, ConnectorEnd.Bound(start, ConnectionPort.RIGHT), ConnectorEnd.Bound(end, ConnectionPort.LEFT))
    private val ea = edge("edge-a", root.id, a.id)
    private val el = edge("edge-leaf", a.id, leaf.id)
    private val eb = edge("edge-b", root.id, b.id)
    private val link = edge("link", leaf.id, external.id)
    private val tree =
        MindMapTree(
            "map",
            listOf(
                MindMapNode(root.id),
                MindMapNode(a.id, root.id, ea.id, collapsed = true),
                MindMapNode(leaf.id, a.id, el.id),
                MindMapNode(b.id, root.id, eb.id)
            )
        )
    private val page = Page(elements = listOf(root, ea, a, el, leaf, eb, b, external, link), mindMaps = listOf(tree))
    private fun ids(): () -> String {
        var next = 0
        return { "copied-${++next}" }
    }
    private fun asPage(fragment: PageFragment) = Page(elements = fragment.objects, mindMaps = fragment.mindMaps)

    @Test fun copyingAHiddenBranchKeepsDescendantsAndMakesAnIndependentRootWithoutItsIncomingEdge() {
        val copied = MindMapCopies.branches(page, setOf(a.id), identity = ids())
        val result = asPage(copied)
        val map = result.mindMaps.single()
        assertEquals(listOf("A", "Hidden leaf"), result.elements.filterIsInstance<Note>().map { it.text })
        assertEquals(3, result.elements.size)
        assertEquals(2, map.nodes.size)
        assertTrue(map.node(map.rootId).collapsed)
        assertNull(map.node(map.rootId).parentId)
        assertNull(map.node(map.rootId).connectionId)
        assertEquals(1, result.visibleElements.size)
        assertTrue((result.elements.map { it.id } + map.id).none { it in page.elements.map { old -> old.id } || it == tree.id })
        assertEquals(listOf(a.bounds, leaf.bounds), result.elements.filterIsInstance<Note>().map { it.bounds })
    }

    @Test fun overlappingNodesAndManagedEdgesAreDeduplicatedWithoutChangingLayerOrder() {
        val single = MindMapCopies.branches(page, setOf(a.id), identity = ids())
        val overlapping = MindMapCopies.branches(page, setOf(a.id, leaf.id, ea.id, el.id), identity = ids())
        assertEquals(single.objects, overlapping.objects)
        assertEquals(single.mindMaps, overlapping.mindMaps)
        assertEquals(listOf(Note::class.java, Connector::class.java, Note::class.java), single.objects.map { it.javaClass })
    }

    @Test fun separateSiblingBranchesBecomeSeparateMapsAndAnOrdinarySelectedConnectionDetachesOnlyItsOutsideEnd() {
        val copied = MindMapCopies.branches(page, setOf(a.id, b.id, link.id), offset = Point(24f, 24f), identity = ids())
        assertEquals(2, copied.mindMaps.size)
        val notes = copied.objects.filterIsInstance<Note>().associateBy { it.text }
        val ordinary = copied.objects.filterIsInstance<Connector>().single {
            it.id !in
                copied.mindMaps.flatMap { map -> map.nodes.mapNotNull { node -> node.connectionId } }
        }
        assertEquals(ConnectorEnd.Bound(notes.getValue("Hidden leaf").id, ConnectionPort.RIGHT), ordinary.start)
        val oldEnd = (page.geometry.resolve(link) as Line).end
        assertEquals(ConnectorEnd.Free(oldEnd + Point(24f, 24f)), ordinary.end)
        assertEquals(a.bounds.left + 24f, notes.getValue("A").bounds.left, 0f)
        asPage(copied).contentBounds
    }

    @Test fun copyingAManagedIncomingEdgeMeansCopyingItsChildBranch() {
        val node = MindMapCopies.branches(page, setOf(a.id), identity = ids())
        val connection = MindMapCopies.branches(page, setOf(ea.id), identity = ids())
        assertEquals(node.objects, connection.objects)
        assertEquals(node.mindMaps, connection.mindMaps)
    }

    @Test fun copyingTheRootPreservesTheWholeHiddenMapAndAllInternalBindings() {
        val copied = MindMapCopies.branches(page, setOf(root.id), identity = ids())
        val result = asPage(copied)
        val map = result.mindMaps.single()
        assertEquals(4, map.nodes.size)
        assertEquals(7, result.elements.size)
        assertEquals(5, result.visibleElements.size)
        assertEquals(3, result.elements.filterIsInstance<Connector>().size)
        map.validateObjects(result)
    }

    @Test fun repeatedPrivatePastesRekeyMapsNodesAndEdgesAndKeepTheOffsetCycle() {
        val clipboard = ObjectClipboard()
        clipboard.copy(page, setOf(a.id))
        val allocated = mutableSetOf<String>()
        repeat(21) { index ->
            val copied = clipboard.pasteFragment()
            val map = copied.mindMaps.single()
            assertTrue(allocated.add(map.id))
            copied.objects.forEach { assertTrue(allocated.add(it.id)) }
            val note = copied.objects.filterIsInstance<Note>().first()
            assertEquals(a.bounds.left + 24f * (index % 20 + 1), note.bounds.left, 0f)
            assertEquals(1, asPage(copied).visibleElements.size)
        }
        assertThrows(IllegalArgumentException::class.java) { clipboard.paste() }
    }

    @Test fun fragmentInsertionAndUndoRedoPublishTopologyAndObjectsAsOneHistoryEntry() {
        val fragment = MindMapCopies.branches(page, setOf(a.id), identity = ids())
        val session = DocumentSession(Board(name = "Paste", pages = listOf(page)))
        session.addAll(fragment)
        val changed = session.page
        assertEquals(2, changed.mindMaps.size)
        assertEquals(page.elements + fragment.objects, changed.elements)
        assertEquals(1L, session.revision)
        session.undo()
        assertEquals(page, session.page)
        session.redo()
        assertEquals(changed, session.page)
    }

    @Test fun invalidCopyCancellationIdentityCollisionsAndBudgetsLeaveTheSourceUntouched() {
        assertThrows(IllegalArgumentException::class.java) { MindMapCopies.branches(page, setOf("foreign")) }
        assertThrows(IllegalArgumentException::class.java) { MindMapCopies.branches(page, setOf(a.id), identity = { tree.id }) }
        var calls = 0
        assertThrows(CancellationException::class.java) {
            MindMapCopies.branches(page, setOf(a.id), checkpoint = { if (++calls == 25) throw CancellationException() })
        }
        val limit = assertThrows(DocumentFailure::class.java) { MindMapCopies.branches(page, setOf(a.id), byteBudget = 1) }
        assertEquals(DocumentError.EDIT_LIMIT, limit.error)
        assertEquals(4, tree.nodes.size)
        assertEquals(9, page.elements.size)
    }

    @Test fun failedClipboardCopyPreservesThePreviousValidContentAndPasteSequence() {
        val clipboard = ObjectClipboard()
        clipboard.copy(listOf(external))
        assertEquals(external.bounds.left + 24f, (clipboard.paste().single() as Note).bounds.left, 0f)
        assertThrows(IllegalArgumentException::class.java) { clipboard.copy(page, setOf("foreign")) }
        assertEquals(external.bounds.left + 48f, (clipboard.paste().single() as Note).bounds.left, 0f)
        assertTrue(clipboard.available)
    }

    @Test fun collidingOrOverBudgetFragmentInsertionCannotPublishAnyPartialDocumentOrHistory() {
        val session = DocumentSession(Board(name = "Collision", pages = listOf(page)))
        val same = PageFragment(page.elements, page.mindMaps)
        assertThrows(IllegalArgumentException::class.java) { session.addAll(same) }
        assertEquals(page, session.page)
        assertFalse(session.canUndo)
        val limited = DocumentSession(Board(name = "Limited", pages = listOf(Page())), historyByteLimit = 1)
        val original = limited.page
        val copied = MindMapCopies.branches(page, setOf(root.id), identity = ids())
        assertThrows(DocumentFailure::class.java) { limited.addAll(copied) }
        assertEquals(original, limited.page)
        assertFalse(limited.canUndo)
        assertEquals(0L, limited.revision)
    }

    @Test fun anOldPastePlanCannotConsumeANewClipboardOrAdvanceItsOffset() {
        val clipboard = ObjectClipboard()
        clipboard.copy(page, setOf(a.id))
        val stale = clipboard.pasteRequest().prepare()
        clipboard.copy(listOf(external))
        assertFalse(clipboard.isCurrent(stale))
        assertFalse(clipboard.accept(stale))
        assertEquals(external.bounds.left + 24f, (clipboard.paste().single() as Note).bounds.left, 0f)
    }

    @Test fun preparingOrCancellingAPasteDoesNotConsumeItAndOnlyOneParallelPlanCanBeAccepted() {
        val clipboard = ObjectClipboard()
        clipboard.copy(page, setOf(a.id))
        val request = clipboard.pasteRequest()
        assertThrows(CancellationException::class.java) { request.prepare { throw CancellationException() } }
        val first = request.prepare()
        val other = request.prepare()
        assertEquals(a.bounds.left + 24f, (first.fragment.objects.first() as Note).bounds.left, 0f)
        assertEquals(a.bounds.left + 24f, (other.fragment.objects.first() as Note).bounds.left, 0f)
        assertTrue(clipboard.accept(first))
        assertFalse(clipboard.accept(other))
        assertEquals(a.bounds.left + 48f, (clipboard.pasteFragment().objects.first() as Note).bounds.left, 0f)
    }
}
