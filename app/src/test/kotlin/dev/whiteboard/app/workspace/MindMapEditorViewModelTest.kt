package dev.whiteboard.app.workspace

import androidx.lifecycle.viewModelScope
import dev.whiteboard.canvas.CanvasEvent
import dev.whiteboard.core.concurrency.AppDispatchers
import dev.whiteboard.core.model.*
import dev.whiteboard.core.testing.*
import dev.whiteboard.core.usecase.DocumentTransfers
import dev.whiteboard.core.usecase.EditorPersistence
import dev.whiteboard.core.usecase.ManagePreferences
import dev.whiteboard.ui.R
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MindMapEditorViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val repository = InMemoryBoardRepository()
    private val root = Note(id = "root", bounds = Bounds(0f, 0f, 100f, 80f), text = "Root", color = 0xFFD5E7CF.toInt())
    private val child = root.copy(id = "child", bounds = Bounds(200f, 0f, 300f, 80f), text = "Child")
    private val leaf = root.copy(id = "leaf", bounds = Bounds(400f, 0f, 500f, 80f), text = "Hidden leaf")
    private val sibling = root.copy(id = "sibling", bounds = Bounds(200f, 200f, 300f, 280f), text = "Sibling")
    private fun edge(id: String, start: String, end: String) =
        Connector(id, ConnectorEnd.Bound(start, ConnectionPort.RIGHT), ConnectorEnd.Bound(end, ConnectionPort.LEFT))
    private val a = edge("a", root.id, child.id)
    private val b = edge("b", child.id, leaf.id)
    private val c = edge("c", root.id, sibling.id)
    private val tree =
        MindMapTree(
            "map",
            listOf(
                MindMapNode(root.id),
                MindMapNode(child.id, root.id, a.id, collapsed = true),
                MindMapNode(leaf.id, child.id, b.id),
                MindMapNode(sibling.id, root.id, c.id)
            )
        )
    private val page = Page(elements = listOf(root, a, child, b, leaf, c, sibling), mindMaps = listOf(tree))
    private val board = Board(name = "Node editor", pages = listOf(page))
    private var cpu: CoroutineDispatcher? = null
    private val model by lazy {
        WorkspaceViewModel(
            EditorPersistence(repository),
            ManagePreferences(InMemorySettingsRepository(UserSettings(onboarded = true))),
            DocumentTransfers(FakeDocumentStorage()),
            object : AppDispatchers {
                override val io = main.dispatcher
                override val computation = cpu ?: main.dispatcher
            }
        )
    }

    @After fun cleanup() = model.viewModelScope.cancel()
    private suspend fun open() {
        repository.save(board)
        model.open(board.id).join()
    }

    @Test fun creatingAMapOnABlankPagePublishesOneUndoableEditableRootAndFocus() = runTest {
        model.create("Blank").join()
        model.showMindMaps(position = Point(30f, 40f)).join()
        assertTrue(model.mindMaps.state.value.request!!.rows.isEmpty())
        model.mindMaps.apply(MindMapAction.Create("Ideja Ћирилица", root.color)).join()
        val created = model.state.value.editor!!.page
        val note = created.elements.single() as Note
        assertEquals("Ideja Ћирилица", note.text)
        assertEquals(note.id, created.mindMaps.single().rootId)
        assertEquals(setOf(note.id), model.state.value.editor!!.selectedIds)
        assertEquals(note.id, model.mindMaps.state.value.focusId)
        assertNull(model.mindMaps.state.value.request)
        model.undo()
        assertTrue(model.state.value.editor!!.page.mindMaps.isEmpty())
        model.redo()
        assertEquals(created, model.state.value.editor!!.page)
        model.save().join()
        assertEquals(created, repository.load(model.state.value.editor!!.boardId).activePage)
    }

    @Test fun hierarchyIncludesHiddenNodesStableSiblingDepthAndManagedEdgeOwnership() = runTest {
        open()
        model.showMindMaps(b.id).join()
        val request = model.mindMaps.state.value.request!!
        assertEquals(leaf.id, request.selectedNodeId)
        assertEquals(listOf(root.id, child.id, leaf.id, sibling.id), request.rows.map { it.node.id })
        assertEquals(listOf(0, 1, 2, 1), request.rows.map { it.depth })
        assertFalse(request.byId.getValue(leaf.id).visible)
        assertEquals(leaf.text, request.byId.getValue(leaf.id).note.text)
        assertThrows(UnsupportedOperationException::class.java) { (request.rows as MutableList).clear() }
        assertFalse(model.state.value.editor!!.dirty)
        model.mindMaps.dismiss()
        assertEquals(page, model.state.value.editor!!.page)
    }

    @Test fun oversizedOrBlankTitlesAreRejectedBeforeReplacingTheExistingNoteOrItsHistory() = runTest {
        open()
        model.showMindMaps(root.id).join()
        val request = model.mindMaps.state.value.request
        for (invalid in listOf(" ", "x".repeat(20_001))) {
            model.mindMaps.apply(MindMapAction.Edit(root.id, invalid, root.color)).join()
            assertEquals(R.string.mind_map_invalid, model.mindMaps.state.value.error)
            assertSame(request, model.mindMaps.state.value.request)
            assertEquals(page, model.state.value.editor!!.page)
            assertFalse(model.state.value.editor!!.canUndo)
            assertFalse(model.state.value.editor!!.dirty)
        }
    }

    @Test fun addingAChildAndSiblingAndEditingColorUseIndependentAtomicHistoryEntries() = runTest {
        open()
        model.showMindMaps(child.id).join()
        model.mindMaps.apply(MindMapAction.Child(child.id, "New child", 0xFF112233.toInt())).join()
        val created = model.state.value.editor!!.selectedId!!
        assertEquals(child.id, model.state.value.editor!!.page.mindMaps.single().node(created).parentId)
        assertFalse(model.state.value.editor!!.page.mindMaps.single().node(child.id).collapsed)
        model.showMindMaps(created).join()
        model.mindMaps.apply(MindMapAction.Sibling(created, "New sibling", root.color)).join()
        val added = model.state.value.editor!!.selectedId!!
        model.showMindMaps(added).join()
        model.mindMaps.apply(MindMapAction.Edit(added, "Edited", 0xFFABCDEF.toInt())).join()
        val edited = model.state.value.editor!!.page.elements.first { it.id == added } as Note
        assertEquals("Edited", edited.text)
        assertEquals(0xFFABCDEF.toInt(), edited.color)
        model.undo()
        model.undo()
        model.undo()
        assertEquals(page, model.state.value.editor!!.page)
        assertFalse(model.state.value.editor!!.dirty)
    }

    @Test fun revealIsUndoableAndSelectMapOnlySelectsVisibleCanonicalObjectsWithoutDirtyingIt() = runTest {
        open()
        model.showMindMaps().join()
        model.mindMaps.apply(MindMapAction.SelectMap(root.id)).join()
        assertEquals(setOf(root.id, a.id, child.id, c.id, sibling.id), model.state.value.editor!!.selectedIds)
        assertEquals(Bounds(0f, 0f, 300f, 280f), model.mindMaps.state.value.focusBounds)
        assertFalse(model.state.value.editor!!.dirty)
        model.showMindMaps(leaf.id).join()
        model.mindMaps.apply(MindMapAction.Focus(leaf.id)).join()
        assertTrue(model.state.value.editor!!.page.mindMapIndex!!.isVisible(leaf.id))
        assertEquals(setOf(leaf.id), model.state.value.editor!!.selectedIds)
        model.undo()
        assertEquals(page, model.state.value.editor!!.page)
        assertTrue(model.state.value.editor!!.selectedIds.isEmpty())
    }

    @Test fun invalidReparentRemainsInTheDialogAndValidRetryChangesItsBoundParentAndOneUndo() = runTest {
        open()
        model.showMindMaps(child.id).join()
        val request = model.mindMaps.state.value.request
        model.mindMaps.apply(MindMapAction.Reparent(child.id, leaf.id)).join()
        assertEquals(R.string.mind_map_invalid, model.mindMaps.state.value.error)
        assertSame(request, model.mindMaps.state.value.request)
        assertEquals(page, model.state.value.editor!!.page)
        model.mindMaps.apply(MindMapAction.Reparent(leaf.id, sibling.id)).join()
        assertEquals(sibling.id, model.state.value.editor!!.page.mindMaps.single().node(leaf.id).parentId)
        assertEquals(
            ConnectorEnd.Bound(sibling.id, ConnectionPort.RIGHT),
            (
                model.state.value.editor!!.page.elements.first {
                    it.id == b.id
                } as Connector
                ).start
        )
        model.undo()
        assertEquals(page, model.state.value.editor!!.page)
        assertFalse(model.state.value.editor!!.canUndo)
    }

    @Test fun movingACollapsedBranchKeepsItsHiddenStateAndStaleRequestsCannotEditAChangedPage() = runTest {
        open()
        model.showMindMaps(child.id).join()
        model.mindMaps.apply(MindMapAction.Move(child.id, Point(60f, 80f))).join()
        val moved = model.state.value.editor!!.page
        assertEquals(page.mindMaps, moved.mindMaps)
        assertEquals(leaf.bounds.center + Point(60f, 80f), moved.geometry.selectionBounds(moved.elements.first { it.id == leaf.id }).center)
        model.showMindMaps(child.id).join()
        model.canvas(CanvasEvent.Fill(root.bounds.center, 0xFF123456.toInt()))
        val changed = model.state.value.editor!!.page
        model.mindMaps.apply(MindMapAction.Collapse(root.id, true)).join()
        assertEquals(changed, model.state.value.editor!!.page)
        assertNull(model.mindMaps.state.value.request)
        assertEquals(R.string.mind_map_changed, model.state.value.message?.resource)
    }

    @Test fun newBoardsAndCancellationPreventLateEditorPreparationFromOpeningAnOldDialog() = runTest {
        val computation = StandardTestDispatcher(TestCoroutineScheduler())
        cpu = computation
        open()
        val pending = model.showMindMaps(root.id)
        runCurrent()
        assertTrue(model.mindMaps.state.value.busy)
        model.create("New board").join()
        computation.scheduler.runCurrent()
        runCurrent()
        assertTrue(pending.isCancelled)
        assertNull(model.mindMaps.state.value.request)
        assertFalse(model.mindMaps.state.value.busy)
        assertEquals("New board", model.state.value.editor!!.name)
        assertNull(model.state.value.message)
    }

    @Test fun dismissCancelsTheActualWorkerEvenAfterASecondApplyWasIgnored() = runTest {
        val computation = StandardTestDispatcher(TestCoroutineScheduler())
        cpu = computation
        open()
        val opening = model.showMindMaps(root.id)
        runCurrent()
        computation.scheduler.runCurrent()
        runCurrent()
        opening.join()
        val first = model.mindMaps.apply(MindMapAction.Child(root.id, "Cancelled", root.color))
        runCurrent()
        val ignored = model.mindMaps.apply(MindMapAction.Child(root.id, "Ignored", root.color))
        runCurrent()
        assertTrue(ignored.isCompleted)
        model.mindMaps.dismiss()
        computation.scheduler.runCurrent()
        runCurrent()
        assertTrue(first.isCancelled)
        assertFalse(model.mindMaps.state.value.busy)
        assertEquals(page, model.state.value.editor!!.page)
        assertFalse(model.state.value.editor!!.canUndo)
    }

    @Test fun cameraOnlyChangesArePreservedDuringWorkerApplyAndAStaleSourceCannotOpenAnotherBoard() = runTest {
        val computation = StandardTestDispatcher(TestCoroutineScheduler())
        cpu = computation
        open()
        val old = model.state.value.editor!!
        val opening = model.showMindMaps(root.id)
        runCurrent()
        computation.scheduler.runCurrent()
        runCurrent()
        opening.join()
        val apply = model.mindMaps.apply(MindMapAction.Child(root.id, "Valid", root.color))
        runCurrent()
        val camera = Viewport(Point(30f, 40f), 2f)
        model.canvas(CanvasEvent.Camera(camera))
        computation.scheduler.runCurrent()
        runCurrent()
        apply.join()
        assertEquals(camera, model.state.value.editor!!.page.viewport)
        assertEquals(5, model.state.value.editor!!.page.mindMaps.single().nodes.size)
        model.create("Other").join()
        model.showMindMaps(root.id, source = old).join()
        assertNull(model.mindMaps.state.value.request)
        assertEquals("Other", model.state.value.editor!!.name)
        assertEquals(R.string.mind_map_changed, model.state.value.message?.resource)
    }
}
