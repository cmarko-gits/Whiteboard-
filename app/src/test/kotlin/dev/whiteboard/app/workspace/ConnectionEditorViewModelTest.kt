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
class ConnectionEditorViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val repository = InMemoryBoardRepository()
    private val settings = InMemorySettingsRepository(UserSettings(onboarded = true))
    private val files = FakeDocumentStorage()
    private val models = mutableListOf<WorkspaceViewModel>()
    private val first = Note(bounds = Bounds(40f, 40f, 140f, 140f), text = "A")
    private val second = Note(bounds = Bounds(260f, 180f, 360f, 280f), text = "B")
    private val definition =
        Connector(start = ConnectorEnd.Bound(first.id, ConnectionPort.RIGHT), end = ConnectorEnd.Bound(second.id, ConnectionPort.LEFT))

    private fun model(cpu: CoroutineDispatcher = main.dispatcher): WorkspaceViewModel {
        val dispatchers = object : AppDispatchers {
            override val io = main.dispatcher
            override val computation = cpu
        }
        return WorkspaceViewModel(EditorPersistence(repository), ManagePreferences(settings), DocumentTransfers(files), dispatchers)
            .also { models.add(it) }
    }
    private suspend fun prepare(model: WorkspaceViewModel, connection: Boolean = false) {
        model.create("Connection editor").join()
        model.canvas(CanvasEvent.Add(first))
        model.canvas(CanvasEvent.Add(second))
        if (connection) model.canvas(CanvasEvent.Add(definition))
        model.selectAll()
        model.save().join()
    }

    @After fun cleanup() {
        models.forEach { it.viewModelScope.cancel() }
    }

    @Test fun creatingFromTwoSelectedObjectsSavesTheChosenBindingAndOneUndo() = runTest {
        val model = model()
        prepare(model)
        model.showConnection().join()
        val request = checkNotNull(model.state.value.connectionRequest)
        assertEquals(setOf(first.id, second.id), request.targets.map { it.id }.toSet())
        assertEquals(definition.start, request.start)
        assertEquals(definition.end, request.end)
        model.applyConnection(request.start, request.end, false).join()
        val created = model.state.value.editor!!.page.elements.last() as Connector
        assertEquals(Connector(request.id, request.start, request.end, request.style, false), created)
        assertEquals(setOf(created.id), model.state.value.editor!!.selectedIds)
        assertEquals(Tool.SELECT, model.state.value.editor!!.tool)
        assertNull(model.state.value.connectionRequest)
        assertFalse(model.state.value.connectionBusy)
        model.save().join()
        assertEquals(created, repository.load(model.state.value.editor!!.boardId).activePage.elements.last())
        model.undo()
        assertEquals(listOf(first, second), model.state.value.editor!!.page.elements)
        model.redo()
        assertEquals(created, model.state.value.editor!!.page.elements.last())
    }

    @Test fun editingAConnectionPreservesItsIdentityAndRestoresTheOldDefinitionOnUndo() = runTest {
        val model = model()
        prepare(model, connection = true)
        model.showConnection(definition.id).join()
        val free = ConnectorEnd.Free(Point(-10f, -20f))
        val bound = ConnectorEnd.Bound(second.id, ConnectionPort.TOP)
        model.applyConnection(free, bound, false).join()
        assertEquals(definition.copy(start = free, end = bound, arrow = false), model.state.value.editor!!.page.elements.last())
        model.undo()
        assertEquals(definition, model.state.value.editor!!.page.elements.last())
        assertFalse(model.state.value.editor!!.dirty)
    }

    @Test fun detachingKeepsLastPositionsAndTheTargetsStopMovingTheFreeConnection() = runTest {
        val model = model()
        prepare(model, connection = true)
        val source = checkNotNull(model.state.value.editor)
        model.detachConnection(definition.id, source).join()
        val detached = model.state.value.editor!!.page.elements.last() as Connector
        assertEquals(ConnectorEnd.Free(Point(140f, 90f)), detached.start)
        assertEquals(ConnectorEnd.Free(Point(260f, 230f)), detached.end)
        model.canvas(CanvasEvent.Replace(listOf(first.copy(bounds = Bounds(500f, 500f, 600f, 600f)))))
        assertEquals(detached, model.state.value.editor!!.page.elements.last())
        model.undo()
        model.undo()
        assertEquals(definition, model.state.value.editor!!.page.elements.last())
    }

    @Test fun aConcurrentTargetEditCannotBeOverwrittenByAPendingConnectionApply() = runTest {
        val cpu = StandardTestDispatcher(TestCoroutineScheduler())
        val model = model(cpu)
        prepare(model)
        model.showConnection()
        runCurrent()
        cpu.scheduler.runCurrent()
        runCurrent()
        val request = checkNotNull(model.state.value.connectionRequest)
        val operation = model.applyConnection(request.start, request.end, true)
        runCurrent()
        assertTrue(model.state.value.connectionBusy)
        val changed = first.copy(text = "Keep newer text")
        model.canvas(CanvasEvent.Replace(listOf(changed)))
        cpu.scheduler.runCurrent()
        runCurrent()
        assertTrue(operation.isCompleted)
        assertEquals(listOf(changed, second), model.state.value.editor!!.page.elements)
        assertNull(model.state.value.connectionRequest)
        assertEquals(R.string.connection_changed, model.state.value.message?.resource)
    }

    @Test fun dismissingDuringApplyPreventsALateCommit() = runTest {
        val cpu = StandardTestDispatcher(TestCoroutineScheduler())
        val model = model(cpu)
        prepare(model)
        model.showConnection()
        runCurrent()
        cpu.scheduler.runCurrent()
        runCurrent()
        val request = checkNotNull(model.state.value.connectionRequest)
        model.applyConnection(request.start, request.end, true)
        runCurrent()
        model.dismissConnection()
        cpu.scheduler.runCurrent()
        runCurrent()
        assertEquals(listOf(first, second), model.state.value.editor!!.page.elements)
        assertFalse(model.state.value.editor!!.dirty)
        assertNull(model.state.value.connectionRequest)
        assertFalse(model.state.value.connectionBusy)
    }

    @Test fun dismissingDuringPreparationCannotOpenTheCancelledDialogLater() = runTest {
        val cpu = StandardTestDispatcher(TestCoroutineScheduler())
        val model = model(cpu)
        prepare(model)
        val operation = model.showConnection()
        runCurrent()
        assertTrue(model.state.value.connectionBusy)
        model.dismissConnection()
        cpu.scheduler.runCurrent()
        runCurrent()
        assertTrue(operation.isCompleted)
        assertNull(model.state.value.connectionRequest)
        assertFalse(model.state.value.connectionBusy)
        assertFalse(model.state.value.editor!!.dirty)
    }

    @Test fun cancellationReleasesBusyStateWithoutPublishingAPreparedRequest() = runTest {
        val cpu = StandardTestDispatcher(TestCoroutineScheduler())
        val model = model(cpu)
        prepare(model)
        val operation = model.showConnection()
        runCurrent()
        operation.cancel()
        cpu.scheduler.runCurrent()
        runCurrent()
        assertTrue(operation.isCancelled)
        assertNull(model.state.value.connectionRequest)
        assertFalse(model.state.value.connectionBusy)
        assertEquals(listOf(first, second), model.state.value.editor!!.page.elements)
    }

    @Test fun duplicateConfirmCannotCreateTwoConnectionsOrTwoUndoEntries() = runTest {
        val cpu = StandardTestDispatcher(TestCoroutineScheduler())
        val model = model(cpu)
        prepare(model)
        model.showConnection()
        runCurrent()
        cpu.scheduler.runCurrent()
        runCurrent()
        val request = checkNotNull(model.state.value.connectionRequest)
        model.applyConnection(request.start, request.end, true)
        runCurrent()
        val duplicate = model.applyConnection(request.start, request.end, true)
        runCurrent()
        assertTrue(duplicate.isCompleted)
        cpu.scheduler.runCurrent()
        runCurrent()
        assertEquals(1, model.state.value.editor!!.page.elements.filterIsInstance<Connector>().size)
        model.undo()
        assertEquals(listOf(first, second), model.state.value.editor!!.page.elements)
        assertFalse(model.state.value.editor!!.dirty)
    }

    @Test fun invalidBindingsStayInTheEditorWithoutChangingTheDocument() = runTest {
        val model = model()
        prepare(model)
        model.showConnection().join()
        val request = checkNotNull(model.state.value.connectionRequest)
        model.applyConnection(request.start, ConnectorEnd.Bound(first.id, ConnectionPort.LEFT), true).join()
        assertEquals(listOf(first, second), model.state.value.editor!!.page.elements)
        assertEquals(request, model.state.value.connectionRequest)
        assertEquals(R.string.connection_invalid, model.state.value.connectionError)
        assertFalse(model.state.value.editor!!.dirty)
        model.applyConnection(request.start, request.end, true).join()
        assertEquals(1, model.state.value.editor!!.page.elements.filterIsInstance<Connector>().size)
    }

    @Test fun anEmptyPageCanCreateAnEditableFreeConnectionAtTheRequestedWorldPosition() = runTest {
        val model = model()
        model.create("Free connection").join()
        model.showConnection(position = Point(-10f, 20f)).join()
        val request = checkNotNull(model.state.value.connectionRequest)
        assertTrue(request.targets.isEmpty())
        assertEquals(ConnectorEnd.Free(Point(-10f, 20f)), request.start)
        assertEquals(ConnectorEnd.Free(Point(150f, 20f)), request.end)
        model.applyConnection(request.start, request.end, true).join()
        assertEquals(1, model.state.value.editor!!.page.elements.size)
        assertTrue(model.state.value.editor!!.page.elements.single().canTransformIndependently)
    }

    @Test fun aStaleNativeCallbackCannotModifyAnotherBoardEvenWithSharedPageIdsAndObjects() = runTest {
        val model = model()
        prepare(model)
        val source = checkNotNull(model.state.value.editor)
        val other = Board(name = "Other board", pages = listOf(source.page))
        repository.save(other)
        model.open(other.id).join()
        model.canvas(CanvasEvent.Delete(setOf(first.id)), source)
        assertEquals(other.id, model.state.value.editor!!.boardId)
        assertEquals(listOf(first, second), model.state.value.editor!!.page.elements)
        assertFalse(model.state.value.editor!!.dirty)
        assertFalse(model.state.value.editor!!.canUndo)
    }

    @Test fun aStaleNativeCallbackCannotModifyAnotherPageWithTheSameObjectList() = runTest {
        val model = model()
        prepare(model)
        val source = checkNotNull(model.state.value.editor)
        val other = source.page.copy(id = newId(), name = "Other page")
        repository.save(repository.load(source.boardId).copy(pages = listOf(source.page, other), activePageId = other.id))
        model.open(source.boardId).join()
        model.canvas(CanvasEvent.Delete(setOf(first.id)), source)
        assertEquals(other.id, model.state.value.editor!!.page.id)
        assertEquals(listOf(first, second), model.state.value.editor!!.page.elements)
        assertFalse(model.state.value.editor!!.dirty)
    }

    @Test fun metadataOnlyCameraChangesDoNotRejectValidNativeInputOrClobberTheLatestViewport() = runTest {
        val model = model()
        prepare(model)
        val source = checkNotNull(model.state.value.editor)
        val viewport = Viewport(Point(100f, 200f), 3f)
        model.canvas(CanvasEvent.Camera(viewport), source)
        model.canvas(CanvasEvent.Add(definition), source)
        assertEquals(viewport, model.state.value.editor!!.page.viewport)
        assertEquals(definition, model.state.value.editor!!.page.elements.last())
    }
}
