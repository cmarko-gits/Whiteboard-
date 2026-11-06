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
class MindMapVisibilityViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val repository = InMemoryBoardRepository()
    private val files = FakeDocumentStorage()
    private val root = Note(id = "root", bounds = Bounds(0f, 0f, 100f, 80f), text = "Root")
    private val child = root.copy(id = "child", bounds = Bounds(300f, 200f, 400f, 280f), text = "Skriveno Ћирилица")
    private val edge =
        Connector("edge", ConnectorEnd.Bound(root.id, ConnectionPort.RIGHT), ConnectorEnd.Bound(child.id, ConnectionPort.LEFT))
    private val tree = MindMapTree("map", listOf(MindMapNode(root.id, collapsed = true), MindMapNode(child.id, root.id, edge.id)))
    private val page = Page(elements = listOf(root, edge, child), mindMaps = listOf(tree))
    private val board = Board(name = "Visibility fixture", pages = listOf(page))
    private var cpu: CoroutineDispatcher? = null
    private val model by lazy {
        WorkspaceViewModel(
            EditorPersistence(repository),
            ManagePreferences(InMemorySettingsRepository(UserSettings(onboarded = true))),
            DocumentTransfers(files),
            object : AppDispatchers {
                override val io = main.dispatcher
                override val computation = cpu ?: main.dispatcher
            }
        )
    }

    @After fun cleanup() = model.viewModelScope.cancel()

    @Test fun selectAllAndNativeSelectionCannotSelectHiddenObjects() = runTest {
        repository.save(board)
        model.open(board.id).join()
        model.selectAll()
        assertEquals(setOf(root.id), model.state.value.editor!!.selectedIds)
        model.canvas(CanvasEvent.Select(setOf(child.id, edge.id)))
        assertTrue(model.state.value.editor!!.selectedIds.isEmpty())
        assertEquals(page, model.state.value.editor!!.page)
        assertFalse(model.state.value.editor!!.dirty)
    }

    @Test fun hiddenNativeEditRequestsAreRejectedWithoutOpeningATextDialog() = runTest {
        repository.save(board)
        model.open(board.id).join()
        model.canvas(CanvasEvent.EditText(child.bounds.center, child, note = true))
        assertNull(model.state.value.textRequest)
        assertEquals(page, model.state.value.editor!!.page)
    }

    @Test fun openingAHiddenSearchResultRevealsItsPathAsOneUndoableRecoverableEdit() = runTest {
        repository.save(board)
        model.open(board.id, "ћирилица").join()
        val visible = checkNotNull(model.state.value.editor)
        assertEquals(setOf(child.id), visible.selectedIds)
        assertTrue(visible.page.mindMapIndex!!.isVisible(child.id))
        assertTrue(visible.dirty)
        assertTrue(visible.canUndo)
        assertEquals(page, repository.load(board.id).activePage)
        advanceTimeBy(700)
        runCurrent()
        assertEquals(visible.page.mindMaps, repository.draft(board.id)!!.activePage.mindMaps)
        model.undo()
        assertEquals(page.mindMaps, model.state.value.editor!!.page.mindMaps)
        assertFalse(model.state.value.editor!!.dirty)
        assertTrue(model.state.value.editor!!.selectedIds.isEmpty())
        model.redo()
        assertEquals(visible.page.mindMaps, model.state.value.editor!!.page.mindMaps)
    }

    @Test fun anOrdinaryOpenDoesNotExpandOrDirtyACollapsedMap() = runTest {
        repository.save(board)
        model.open(board.id).join()
        assertEquals(page, model.state.value.editor!!.page)
        assertFalse(model.state.value.editor!!.dirty)
        assertFalse(model.state.value.editor!!.canUndo)
        assertEquals(listOf(root), model.state.value.editor!!.page.visibleElements)
    }

    @Test fun aLateSearchRevealCannotReplaceANewlyActivatedBoard() = runTest {
        val computation = StandardTestDispatcher(TestCoroutineScheduler())
        cpu = computation
        repository.save(board)
        val pending = model.open(board.id, "Skriveno")
        runCurrent()
        assertFalse(pending.isCompleted)
        model.create("Newly activated").join()
        val active = checkNotNull(model.state.value.editor)
        computation.scheduler.runCurrent()
        runCurrent()
        pending.join()
        assertEquals(active, model.state.value.editor)
        assertEquals(page, repository.load(board.id).activePage)
    }

    @Test fun cancellationDuringSearchRevealKeepsTheCurrentBoardAndNoFailureNotice() = runTest {
        val computation = StandardTestDispatcher(TestCoroutineScheduler())
        cpu = computation
        repository.save(board)
        model.create("Current board").join()
        val active = checkNotNull(model.state.value.editor)
        val pending = model.open(board.id, "Skriveno")
        runCurrent()
        pending.cancel()
        computation.scheduler.runCurrent()
        runCurrent()
        assertTrue(pending.isCancelled)
        assertEquals(active, model.state.value.editor)
        assertFalse(model.state.value.loading)
        assertNull(model.state.value.message)
        assertEquals(page, repository.load(board.id).activePage)
    }

    @Test fun cuttingAndPastingACollapsedRootKeepsTheWholeHiddenMapAndSeparateUndoSteps() = runTest {
        repository.save(board)
        model.open(board.id).join()
        model.selectAll()
        model.copySelected(cut = true).join()
        assertTrue(model.state.value.editor!!.page.elements.isEmpty())
        assertTrue(model.state.value.editor!!.page.mindMaps.isEmpty())
        assertTrue(model.state.value.editor!!.canPaste)
        model.paste().join()
        val pasted = checkNotNull(model.state.value.editor).page
        assertEquals(3, pasted.elements.size)
        assertEquals(1, pasted.mindMaps.size)
        assertEquals(1, pasted.visibleElements.size)
        assertNotEquals(tree.id, pasted.mindMaps.single().id)
        assertEquals(setOf(pasted.mindMaps.single().rootId), model.state.value.editor!!.selectedIds)
        model.undo()
        assertTrue(model.state.value.editor!!.page.elements.isEmpty())
        model.undo()
        assertEquals(page, model.state.value.editor!!.page)
        assertFalse(model.state.value.editor!!.dirty)
        model.redo()
        model.redo()
        assertEquals(pasted, model.state.value.editor!!.page)
    }

    @Test fun duplicateSelectedPreservesHiddenDescendantsAndNewBindingsThroughSaveAndUndo() = runTest {
        repository.save(board)
        model.open(board.id).join()
        model.selectAll()
        model.duplicateSelected().join()
        val duplicated = checkNotNull(model.state.value.editor).page
        assertEquals(6, duplicated.elements.size)
        assertEquals(2, duplicated.mindMaps.size)
        assertEquals(2, duplicated.visibleElements.size)
        assertEquals(1, model.state.value.editor!!.selectedIds.size)
        duplicated.mindMaps.forEach { it.validateObjects(duplicated) }
        model.save().join()
        assertEquals(duplicated, repository.load(board.id).activePage)
        model.undo()
        assertEquals(page, model.state.value.editor!!.page)
        model.redo()
        assertEquals(duplicated, model.state.value.editor!!.page)
    }

    @Test fun aLateCopyCannotChangeTheClipboardOrSelectionOfANewlyActivatedBoard() = runTest {
        val computation = StandardTestDispatcher(TestCoroutineScheduler())
        cpu = computation
        repository.save(board)
        model.open(board.id).join()
        model.selectAll()
        val copy = model.copySelected()
        runCurrent()
        assertTrue(model.state.value.clipboardBusy)
        model.create("New board during copy").join()
        val active = checkNotNull(model.state.value.editor)
        computation.scheduler.runCurrent()
        runCurrent()
        copy.join()
        assertEquals(active, model.state.value.editor)
        assertFalse(model.state.value.editor!!.canPaste)
        assertFalse(model.state.value.clipboardBusy)
        assertNull(model.state.value.message)
        assertEquals(page, repository.load(board.id).activePage)
    }

    @Test fun aSceneChangeWhileDuplicatingRejectsTheOldResultAndReleasesBusyState() = runTest {
        val computation = StandardTestDispatcher(TestCoroutineScheduler())
        cpu = computation
        repository.save(board)
        model.open(board.id).join()
        model.selectAll()
        val duplicate = model.duplicateSelected()
        runCurrent()
        model.canvas(CanvasEvent.Fill(root.bounds.center, 0xFF112233.toInt()))
        val changed = model.state.value.editor!!.page
        computation.scheduler.runCurrent()
        runCurrent()
        duplicate.join()
        assertEquals(changed, model.state.value.editor!!.page)
        assertEquals(3, changed.elements.size)
        assertEquals(R.string.clipboard_changed, model.state.value.message?.resource)
        assertFalse(model.state.value.clipboardBusy)
    }

    @Test fun cancellingACutKeepsTheDocumentHistoryAndClipboardUntouched() = runTest {
        val computation = StandardTestDispatcher(TestCoroutineScheduler())
        cpu = computation
        repository.save(board)
        model.open(board.id).join()
        model.selectAll()
        val cut = model.copySelected(cut = true)
        runCurrent()
        cut.cancel()
        computation.scheduler.runCurrent()
        runCurrent()
        assertTrue(cut.isCancelled)
        assertEquals(page, model.state.value.editor!!.page)
        assertFalse(model.state.value.editor!!.canUndo)
        assertFalse(model.state.value.editor!!.canPaste)
        assertFalse(model.state.value.clipboardBusy)
        assertNull(model.state.value.message)
    }

    @Test fun parallelPasteClicksCommitOnlyOnceAndAnIndependentCameraChangeIsPreserved() = runTest {
        val computation = StandardTestDispatcher(TestCoroutineScheduler())
        cpu = computation
        repository.save(board)
        model.open(board.id).join()
        model.selectAll()
        val copy = model.copySelected()
        runCurrent()
        computation.scheduler.runCurrent()
        runCurrent()
        copy.join()
        val first = model.paste()
        runCurrent()
        val duplicate = model.paste()
        runCurrent()
        assertTrue(duplicate.isCompleted)
        val camera = Viewport(Point(50f, 60f), 2f)
        model.canvas(CanvasEvent.Camera(camera))
        computation.scheduler.runCurrent()
        runCurrent()
        first.join()
        val pasted = model.state.value.editor!!.page
        assertEquals(2, pasted.mindMaps.size)
        assertEquals(6, pasted.elements.size)
        assertEquals(camera, pasted.viewport)
        assertFalse(model.state.value.clipboardBusy)
        model.undo()
        assertEquals(page.elements, model.state.value.editor!!.page.elements)
        assertEquals(camera, model.state.value.editor!!.page.viewport)
    }

    @Test fun changingSelectionDuringCutCannotDeleteTheNewSelection() = runTest {
        val computation = StandardTestDispatcher(TestCoroutineScheduler())
        cpu = computation
        val unrelated = root.copy(id = "ordinary", bounds = Bounds(600f, 0f, 700f, 80f))
        repository.save(board.withPage(page.copy(elements = page.elements + unrelated)))
        model.open(board.id).join()
        model.canvas(CanvasEvent.Select(setOf(root.id)))
        val cut = model.copySelected(cut = true)
        runCurrent()
        model.canvas(CanvasEvent.Select(setOf(unrelated.id)))
        computation.scheduler.runCurrent()
        runCurrent()
        cut.join()
        assertEquals(listOf(unrelated), model.state.value.editor!!.page.elements)
        assertEquals(setOf(unrelated.id), model.state.value.editor!!.selectedIds)
        assertTrue(model.state.value.editor!!.canPaste)
        model.undo()
        assertEquals(page.elements + unrelated, model.state.value.editor!!.page.elements)
    }
}
