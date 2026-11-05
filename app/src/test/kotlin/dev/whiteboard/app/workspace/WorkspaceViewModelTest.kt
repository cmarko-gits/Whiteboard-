package dev.whiteboard.app.workspace

import androidx.lifecycle.viewModelScope
import dev.whiteboard.canvas.CanvasEvent
import dev.whiteboard.core.concurrency.AppDispatchers
import dev.whiteboard.core.editor.LayerStep
import dev.whiteboard.core.editor.ObjectAlignment
import dev.whiteboard.core.model.*
import dev.whiteboard.core.testing.*
import dev.whiteboard.core.usecase.DocumentTransfers
import dev.whiteboard.core.usecase.EditorPersistence
import dev.whiteboard.core.usecase.ManagePreferences
import dev.whiteboard.ui.R
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule : TestWatcher() {
    val dispatcher = StandardTestDispatcher()
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }
    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class WorkspaceViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val repository = InMemoryBoardRepository()
    private val settings = InMemorySettingsRepository(UserSettings(onboarded = true))
    private val files = FakeDocumentStorage()
    private val models = mutableListOf<WorkspaceViewModel>()

    @Test fun nativeConnectionAddMoveSaveAndUndoUseTheSamePageGeometry() = runTest {
        val model = model()
        model.create("Native graph").join()
        val first = note("A")
        val second = note("B").copy(bounds = Bounds(300f, 100f, 500f, 300f))
        val connection =
            Connector(start = ConnectorEnd.Bound(first.id, ConnectionPort.RIGHT), end = ConnectorEnd.Bound(second.id, ConnectionPort.LEFT))
        model.canvas(CanvasEvent.Add(first))
        model.canvas(CanvasEvent.Add(second))
        model.canvas(CanvasEvent.Add(connection))
        val moved = first.copy(bounds = Bounds(500f, 600f, 700f, 800f))
        model.canvas(CanvasEvent.Replace(listOf(moved)))
        val page = model.state.value.editor!!.page
        assertEquals(Point(700f, 700f), (page.geometry.resolve(connection) as Line).start)
        assertEquals(connection, page.elements.last())
        model.save().join()
        assertEquals(page, repository.load(model.state.value.editor!!.boardId).activePage)
        model.undo()
        val original = model.state.value.editor!!.page
        assertEquals(first, original.elements.first())
        assertEquals(Point(first.bounds.right, first.bounds.center.y), (original.geometry.resolve(connection) as Line).start)
        model.redo()
        assertEquals(page, model.state.value.editor!!.page)
    }

    @Test fun partialGraphCutAndPasteRestoreInTwoIndependentUndoSteps() = runTest {
        val model = model()
        model.create("Cut graph").join()
        val first = note("A")
        val second = note("B").copy(bounds = Bounds(300f, 100f, 500f, 300f))
        val connection =
            Connector(start = ConnectorEnd.Bound(first.id, ConnectionPort.RIGHT), end = ConnectorEnd.Bound(second.id, ConnectionPort.LEFT))
        listOf(first, second, connection).forEach { model.canvas(CanvasEvent.Add(it)) }
        model.save().join()
        val original = model.state.value.editor!!.page
        model.canvas(CanvasEvent.Select(setOf(first.id, connection.id)))
        model.copySelected(cut = true).join()
        assertEquals(listOf(second), model.state.value.editor!!.page.elements)
        assertTrue(model.state.value.editor!!.canPaste)
        model.paste().join()
        val pastedPage = model.state.value.editor!!.page
        val copied = pastedPage.elements.last() as Connector
        val copiedFirst = pastedPage.elements[1]
        assertEquals(ConnectorEnd.Bound(copiedFirst.id, ConnectionPort.RIGHT), copied.start)
        assertEquals(ConnectorEnd.Free(Point(324f, 224f)), copied.end)
        assertEquals(setOf(copiedFirst.id, copied.id), model.state.value.editor!!.selectedIds)
        model.undo()
        assertEquals(listOf(second), model.state.value.editor!!.page.elements)
        model.undo()
        assertEquals(original, model.state.value.editor!!.page)
        assertFalse(model.state.value.editor!!.dirty)
    }

    @Test fun removingAConnectedTargetAndItsDetachedEndUndoTogetherInTheWorkspace() = runTest {
        val model = model()
        model.create("Delete graph").join()
        val first = note("A")
        val second = note("B").copy(bounds = Bounds(300f, 100f, 500f, 300f))
        val connection =
            Connector(start = ConnectorEnd.Bound(first.id, ConnectionPort.RIGHT), end = ConnectorEnd.Bound(second.id, ConnectionPort.LEFT))
        listOf(first, second, connection).forEach { model.canvas(CanvasEvent.Add(it)) }
        model.save().join()
        val original = model.state.value.editor!!.page
        model.canvas(CanvasEvent.Select(setOf(first.id)))
        model.deleteSelected()
        val detached = model.state.value.editor!!.page.elements.filterIsInstance<Connector>().single()
        assertEquals(ConnectorEnd.Free(Point(first.bounds.right, first.bounds.center.y)), detached.start)
        assertEquals(connection.end, detached.end)
        model.undo()
        assertEquals(original, model.state.value.editor!!.page)
        assertFalse(model.state.value.editor!!.dirty)
    }

    @Test fun connectionStyleChangesPreserveBindingsAndHaveAnExactUndo() = runTest {
        val model = model()
        model.create("Style graph").join()
        val first = note("A")
        val second = note("B").copy(bounds = Bounds(300f, 100f, 500f, 300f))
        val connection =
            Connector(start = ConnectorEnd.Bound(first.id, ConnectionPort.RIGHT), end = ConnectorEnd.Bound(second.id, ConnectionPort.LEFT))
        listOf(first, second, connection).forEach { model.canvas(CanvasEvent.Add(it)) }
        model.canvas(CanvasEvent.Select(setOf(connection.id)))
        val style = InkStyle(0xFFFF0000.toInt(), 8f, 0.5f)
        model.applySelectedStyle(style, 0xFF123456.toInt()).join()
        val changed = model.state.value.editor!!.page.elements.last() as Connector
        assertEquals(connection.copy(style = style), changed)
        model.undo()
        assertEquals(connection, model.state.value.editor!!.page.elements.last())
        model.redo()
        model.save().join()
        assertEquals(changed, repository.load(model.state.value.editor!!.boardId).activePage.elements.last())
    }

    @Test fun staleTextEditorCannotReplaceANewConnectionThatReusesItsOldNoteId() = runTest {
        val model = model()
        model.create("Stale text").join()
        val first = note("A")
        val second = note("B").copy(bounds = Bounds(300f, 100f, 500f, 300f))
        val third = note("C").copy(bounds = Bounds(600f, 100f, 800f, 300f))
        listOf(first, second, third).forEach { model.canvas(CanvasEvent.Add(it)) }
        model.canvas(CanvasEvent.EditText(first.bounds.center, first, true))
        val replacement =
            Connector(
                id = first.id,
                start = ConnectorEnd.Bound(second.id, ConnectionPort.RIGHT),
                end = ConnectorEnd.Bound(third.id, ConnectionPort.LEFT)
            )
        model.canvas(CanvasEvent.Replace(listOf(replacement)))
        val before = model.state.value.editor!!.page
        model.applyText("Must not overwrite a connection", 22f, false, false, TextAlignment.LEFT, first.color)
        assertEquals(before, model.state.value.editor!!.page)
        assertEquals(replacement, model.state.value.editor!!.page.elements.first())
        assertNull(model.state.value.textRequest)
    }

    @Test fun anInvalidCanvasConnectionReportsAnErrorWithoutMutatingTheSavedDocument() = runTest {
        val model = model()
        model.create("Invalid graph").join()
        val first = note("A")
        model.canvas(CanvasEvent.Add(first))
        model.save().join()
        val before = model.state.value.editor!!.page
        val invalid =
            Connector(start = ConnectorEnd.Bound(first.id, ConnectionPort.RIGHT), end = ConnectorEnd.Bound("missing", ConnectionPort.LEFT))
        model.canvas(CanvasEvent.Add(invalid))
        assertEquals(before, model.state.value.editor!!.page)
        assertFalse(model.state.value.editor!!.dirty)
        assertEquals(R.string.operation_failed, model.state.value.message?.resource)
        assertEquals(before, repository.load(model.state.value.editor!!.boardId).activePage)
        model.undo()
        assertTrue(model.state.value.editor!!.page.elements.isEmpty())
    }

    @Test fun aRejectedPasteReportsAnErrorWithoutChangingTheSavedBoardOrItsHistory() = runTest {
        val model = model()
        model.create("Coordinate limit").join()
        val original = Note(bounds = Bounds(999_999_936f, 0f, 1_000_000_000f, 100f), text = "Boundary")
        model.canvas(CanvasEvent.Add(original))
        model.selectAll()
        model.copySelected()
        model.paste().join()
        assertEquals(2, model.state.value.editor!!.page.elements.size)
        model.save().join()
        val before = model.state.value.editor!!.page
        model.paste().join()
        assertEquals(before, model.state.value.editor!!.page)
        assertFalse(model.state.value.editor!!.dirty)
        assertEquals(R.string.operation_failed, model.state.value.message?.resource)
        model.undo()
        assertEquals(listOf(original), model.state.value.editor!!.page.elements)
    }

    @Test fun duplicatingAGroupKeepsItsChildrenEditableAndRestoresOneExactUndoEntry() = runTest {
        val model = model()
        model.create("Copy group").join()
        val first = note("First")
        val second = note("Second").copy(bounds = Bounds(300f, 300f, 500f, 500f))
        val original = ElementGroup(children = listOf(first, second))
        model.canvas(CanvasEvent.Add(original))
        model.selectAll()
        model.duplicateSelected().join()
        val copied = model.state.value.editor!!.page.elements.last() as ElementGroup
        assertEquals(setOf(copied.id), model.state.value.editor!!.selectedIds)
        assertTrue(copied.children.none { it.id in setOf(first.id, second.id) })
        assertEquals(first.bounds.left + 24f, copied.children.first().bounds.left, 0f)
        model.save().join()
        val editor = model.state.value.editor!!
        assertEquals(listOf(original, copied), repository.load(editor.boardId).activePage.elements)
        model.undo()
        assertEquals(listOf(original), model.state.value.editor!!.page.elements)
        model.redo()
        assertEquals(listOf(original, copied), model.state.value.editor!!.page.elements)
    }

    @Test fun duplicatingAPageRemapsGroupChildrenAndSavesTheIndependentPage() = runTest {
        val model = model()
        model.create("Copy page").join()
        val group = ElementGroup(children = listOf(note("A"), note("B")))
        model.canvas(CanvasEvent.Add(group))
        val before = model.state.value.editor!!.page
        model.duplicatePage(before.id).join()
        val copiedPage = model.state.value.editor!!.page
        val copied = copiedPage.elements.single() as ElementGroup
        assertNotEquals(before.id, copiedPage.id)
        assertNotEquals(group.id, copied.id)
        assertTrue(copied.children.map { it.id }.toSet().intersect(group.children.map { it.id }.toSet()).isEmpty())
        assertEquals(group.bounds, copied.bounds)
        model.save().join()
        assertEquals(copiedPage, repository.load(model.state.value.editor!!.boardId).activePage)
        model.undo()
        assertEquals(listOf(before), model.state.value.editor!!.pages)
        model.redo()
        assertEquals(copiedPage, model.state.value.editor!!.page)
    }
    private fun model(cpu: CoroutineDispatcher = main.dispatcher): WorkspaceViewModel {
        val dispatchers = object : AppDispatchers {
            override val io = main.dispatcher
            override val computation = cpu
        }
        return WorkspaceViewModel(EditorPersistence(repository), ManagePreferences(settings), DocumentTransfers(files), dispatchers)
            .also(models::add)
    }
    private fun note(text: String) = Note(bounds = Bounds(10f, 10f, 200f, 200f), text = text)

    @Test fun creatingATemplateSavesTheCapturedLanguageAndOpeningNeverRelabelsUserContent() = runTest {
        val model = model()
        val content = TemplateContent(TemplateLabel.entries.associateWith { "Prevod ${it.name}" })
        model.create("Moj naziv", template = BoardTemplate.KANBAN, content = content).join()
        val editor = checkNotNull(model.state.value.editor)
        val saved = repository.load(editor.boardId)
        assertEquals("Moj naziv", saved.name)
        assertEquals(content[TemplateLabel.PAGE_ONE], saved.activePage.name)
        assertEquals(content[TemplateLabel.KANBAN_TITLE], (saved.activePage.elements.first() as TextElement).text)
        val original = saved.activePage.elements.filterIsInstance<Note>().first()
        model.canvas(CanvasEvent.Replace(listOf(original.copy(text = "Moj ručno izmenjeni zadatak"))))
        model.save().join()
        model.open(editor.boardId).join()
        val reopened = checkNotNull(model.state.value.editor)
        assertEquals("Moj ručno izmenjeni zadatak", (reopened.page.elements.first { it.id == original.id } as Note).text)
        assertEquals(content[TemplateLabel.KANBAN_TITLE], (reopened.page.elements.first() as TextElement).text)
    }

    @Test fun staleCanvasTextRequestsCannotRevertNewerGeometry() = runTest {
        val model = model()
        model.create("Accessible edits").join()
        val original = note("Before")
        model.canvas(CanvasEvent.Add(original))
        model.canvas(CanvasEvent.TransformBy(original.id, Point(20f, 0f), 1f))
        model.canvas(CanvasEvent.TransformBy(original.id, Point(30f, 0f), 1f))
        model.canvas(CanvasEvent.EditText(original.bounds.center, original, true))
        model.applyText("After", 22f, false, false, TextAlignment.LEFT, original.color)
        val result = model.state.value.editor!!.page.elements.single() as Note
        assertEquals(60f, result.bounds.left)
        assertEquals("After", result.text)
        model.undo()
        assertEquals("Before", (model.state.value.editor!!.page.elements.single() as Note).text)
        assertEquals(60f, model.state.value.editor!!.page.geometry.resolve(model.state.value.editor!!.page.elements.single()).bounds.left)
    }

    @After fun clearModels() {
        models.forEach { it.viewModelScope.cancel() }
    }

    @Test fun alignmentPreservesSelectionAndSavedContentThroughUndoRedoAndNoop() = runTest {
        val model = model()
        model.create("Align").join()
        val first = note("First")
        val second = note("Second").copy(bounds = Bounds(300f, 300f, 500f, 500f))
        model.canvas(CanvasEvent.Add(first))
        model.canvas(CanvasEvent.Add(second))
        model.selectAll()
        model.alignSelected(ObjectAlignment.LEFT).join()
        val expected = listOf(first, second.copy(bounds = Bounds(10f, 300f, 210f, 500f)))
        assertEquals(expected, model.state.value.editor!!.page.elements)
        assertEquals(setOf(first.id, second.id), model.state.value.editor!!.selectedIds)
        assertFalse(model.state.value.aligning)
        assertEquals(1L, model.state.value.alignmentSequence)
        model.save().join()
        assertEquals(expected, repository.load(model.state.value.editor!!.boardId).activePage.elements)
        model.alignSelected(ObjectAlignment.LEFT).join()
        assertFalse(model.state.value.editor!!.dirty)
        model.undo()
        assertEquals(listOf(first, second), model.state.value.editor!!.page.elements)
        model.redo()
        assertEquals(expected, model.state.value.editor!!.page.elements)
    }

    @Test fun aConcurrentEditCannotBeOverwrittenByAnAlignmentPlan() = runTest {
        val cpu = StandardTestDispatcher(TestCoroutineScheduler())
        val model = model(cpu)
        model.create("Concurrent align").join()
        val first = note("First")
        val second = note("Second").copy(bounds = Bounds(300f, 300f, 500f, 500f))
        model.canvas(CanvasEvent.Add(first))
        model.canvas(CanvasEvent.Add(second))
        model.selectAll()
        val operation = model.alignSelected(ObjectAlignment.LEFT)
        runCurrent()
        assertTrue(model.state.value.aligning)
        val updated = second.copy(text = "Keep newer content")
        model.canvas(CanvasEvent.Replace(listOf(updated)))
        cpu.scheduler.runCurrent()
        runCurrent()
        assertTrue(operation.isCompleted)
        assertFalse(model.state.value.aligning)
        assertEquals(listOf(first, updated), model.state.value.editor!!.page.elements)
        assertEquals(R.string.alignment_changed, model.state.value.message?.resource)
    }

    @Test fun cancellingAlignmentReleasesBusyStateAndKeepsTheOriginalDocument() = runTest {
        val cpu = StandardTestDispatcher(TestCoroutineScheduler())
        val model = model(cpu)
        model.create("Cancel align").join()
        model.canvas(CanvasEvent.Add(note("First")))
        model.canvas(CanvasEvent.Add(note("Second").copy(bounds = Bounds(300f, 300f, 500f, 500f))))
        model.selectAll()
        val before = model.state.value.editor!!.page
        val operation = model.alignSelected(ObjectAlignment.LEFT)
        runCurrent()
        operation.cancel()
        cpu.scheduler.runCurrent()
        runCurrent()
        assertTrue(operation.isCancelled)
        assertFalse(model.state.value.aligning)
        assertEquals(before, model.state.value.editor!!.page)
        assertNull(model.state.value.message)
    }

    @Test fun aPendingAlignmentRejectsDuplicateAlignmentAndRotation() = runTest {
        val cpu = StandardTestDispatcher(TestCoroutineScheduler())
        val model = model(cpu)
        model.create("Serialize transforms").join()
        model.canvas(CanvasEvent.Add(note("First")))
        model.canvas(CanvasEvent.Add(note("Second").copy(bounds = Bounds(300f, 300f, 500f, 500f))))
        model.selectAll()
        val operation = model.alignSelected(ObjectAlignment.LEFT)
        runCurrent()
        val duplicate = model.alignSelected(ObjectAlignment.RIGHT)
        val rotation = model.rotateSelected(45.0)
        runCurrent()
        assertTrue(duplicate.isCompleted)
        assertTrue(rotation.isCompleted)
        assertEquals(0L, model.state.value.rotationSequence)
        assertEquals(0L, model.state.value.alignmentSequence)
        cpu.scheduler.runCurrent()
        runCurrent()
        assertTrue(operation.isCompleted)
        assertEquals(1L, model.state.value.alignmentSequence)
        assertEquals(10f, model.state.value.editor!!.page.geometry.resolve(model.state.value.editor!!.page.elements[1]).bounds.left, 0f)
    }

    @Test fun aLayerStepSavesTheNewOrderAndANoopDoesNotDirtyTheSavedBoard() = runTest {
        val model = model()
        model.create("Layer steps").join()
        val first = note("First")
        val second = note("Second")
        model.canvas(CanvasEvent.Add(first))
        model.canvas(CanvasEvent.Add(second))
        model.canvas(CanvasEvent.Select(setOf(first.id)))
        model.stepSelectedLayer(LayerStep.FORWARD).join()
        assertEquals(listOf(second, first), model.state.value.editor!!.page.elements)
        assertEquals(setOf(first.id), model.state.value.editor!!.selectedIds)
        model.save().join()
        val id = model.state.value.editor!!.boardId
        assertEquals(listOf(second, first), repository.load(id).activePage.elements)
        model.stepSelectedLayer(LayerStep.FORWARD).join()
        assertFalse(model.state.value.editor!!.dirty)
        model.undo()
        assertEquals(listOf(first, second), model.state.value.editor!!.page.elements)
        model.redo()
        assertEquals(listOf(second, first), model.state.value.editor!!.page.elements)
    }

    @Test fun rotatingThenEditingAndSavingANoteKeepsItsPlacementAndUndoHistory() = runTest {
        val model = model()
        model.create("Placed note").join()
        val original = note("Before")
        model.canvas(CanvasEvent.Add(original))
        model.selectAll()
        model.rotateSelected(37.0).join()
        val placed = model.state.value.editor!!.page.elements.single() as AffineElement
        assertFalse(model.state.value.rotating)
        assertEquals(1L, model.state.value.rotationSequence)
        model.canvas(CanvasEvent.EditText(placed.bounds.center, placed, true))
        model.applyText("After", 28f, false, false, TextAlignment.LEFT, original.color)
        val edited = model.state.value.editor!!.page.elements.single() as AffineElement
        assertEquals(placed.placement, edited.placement)
        assertEquals("After", (edited.source as Note).text)
        model.save().join()
        val editor = model.state.value.editor!!
        assertEquals(edited, repository.load(editor.boardId).activePage.elements.single())
        model.undo()
        assertEquals(placed, model.state.value.editor!!.page.elements.single())
        model.undo()
        assertEquals(original, model.state.value.editor!!.page.elements.single())
    }

    @Test fun aStyleChangeInsideARotatedGroupKeepsTheGroupAndChildFrames() = runTest {
        val model = model()
        model.create("Placed group style").join()
        val shape = Shape(bounds = Bounds(0f, 0f, 100f, 100f), kind = Tool.RECTANGLE, style = InkStyle())
        val group = ElementGroup(children = listOf(shape, note("Keep text")))
        model.canvas(CanvasEvent.Add(group))
        model.selectAll()
        model.rotateSelected(-45.0).join()
        val before = model.state.value.editor!!.page.elements.single() as ElementGroup
        val ink = InkStyle(color = 0xFF12AB34.toInt(), width = 8f)
        model.applySelectedStyle(ink, 0xFF334455.toInt()).join()
        val after = model.state.value.editor!!.page.elements.single() as ElementGroup
        assertEquals(before.id, after.id)
        assertEquals(before.children.map { (it as AffineElement).placement }, after.children.map { (it as AffineElement).placement })
        val changed = after.children.first().content() as Shape
        assertEquals(ink, changed.style)
        assertEquals(0xFF334455.toInt(), changed.fill)
        assertEquals(before.children.last(), after.children.last())
        model.undo()
        assertEquals(before, model.state.value.editor!!.page.elements.single())
    }

    @Test fun rejectingAnInvalidRotationReleasesTheDialogAndKeepsTheDocument() = runTest {
        val model = model()
        model.create("Rejected rotation").join()
        model.canvas(CanvasEvent.Add(note("Keep me")))
        model.selectAll()
        val before = model.state.value.editor!!.page
        model.rotateSelected(Double.NaN).join()
        assertEquals(before, model.state.value.editor!!.page)
        assertFalse(model.state.value.rotating)
        assertEquals(1L, model.state.value.rotationSequence)
        assertNotNull(model.state.value.message)
    }

    @Test fun anEditDuringRotationPlanningCannotBeOverwrittenByTheComputedResult() = runTest {
        val computation = StandardTestDispatcher(TestCoroutineScheduler())
        val model = model(computation)
        model.create("Intervening edit").join()
        val original = note("Before")
        model.canvas(CanvasEvent.Add(original))
        model.selectAll()
        val rotation = model.rotateSelected(45.0)
        runCurrent()
        assertTrue(model.state.value.rotating)
        val edited = original.copy(text = "Newer edit")
        model.canvas(CanvasEvent.Replace(listOf(edited)))
        computation.scheduler.runCurrent()
        runCurrent()
        assertTrue(rotation.isCompleted)
        assertFalse(model.state.value.rotating)
        assertEquals(listOf(edited), model.state.value.editor!!.page.elements)
        assertEquals(R.string.rotation_changed, model.state.value.message?.resource)
        model.undo()
        assertEquals(listOf(original), model.state.value.editor!!.page.elements)
    }

    @Test fun cancellingQueuedRotationReleasesBusyStateWithoutChangingTheDocumentOrReportingFailure() = runTest {
        val computation = StandardTestDispatcher(TestCoroutineScheduler())
        val model = model(computation)
        model.create("Cancelled rotation").join()
        model.canvas(CanvasEvent.Add(note("Keep me")))
        model.selectAll()
        val before = model.state.value.editor!!.page
        val rotation = model.rotateSelected(45.0)
        runCurrent()
        assertTrue(model.state.value.rotating)
        rotation.cancel()
        computation.scheduler.runCurrent()
        runCurrent()
        assertTrue(rotation.isCancelled)
        assertFalse(model.state.value.rotating)
        assertEquals(before, model.state.value.editor!!.page)
        assertNull(model.state.value.message)
    }

    @Test fun settingsAreObservedThroughDomainContract() = runTest {
        val model = model()
        runCurrent()
        assertTrue(model.state.value.settingsLoaded)
        settings.theme(ThemeMode.DARK)
        runCurrent()
        assertEquals(ThemeMode.DARK, model.state.value.settings.theme)
    }

    @Test fun creationFailureClearsLoadingAndExposesRecoverableMessage() = runTest {
        repository.beforeSave = { throw IOException("fixture storage error") }
        val model = model()
        model.create("Cannot save").join()
        assertFalse(model.state.value.loading)
        assertNull(model.state.value.editor)
        assertEquals(R.string.storage_unavailable, model.state.value.message?.resource)
        assertFalse(model.state.value.message!!.retry)
    }

    @Test fun saveAndCloseKeepAnEditMadeWhileWriteWasSuspended() = runTest {
        val model = model()
        model.create("Concurrent Save").join()
        model.canvas(CanvasEvent.Add(note("First")))
        val captured = model.state.value.editor!!.page
        val barrier = CompletableDeferred<Unit>()
        repository.beforeSave = { barrier.await() }
        val saving = model.save(closeAfter = true)
        runCurrent()
        assertTrue(model.state.value.editor!!.saving)
        model.canvas(CanvasEvent.Add(note("Second")))
        barrier.complete(Unit)
        saving.join()
        val editor = model.state.value.editor!!
        assertFalse(editor.saving)
        assertTrue(editor.dirty)
        assertEquals(captured, repository.load(editor.boardId).activePage)
        assertEquals(editor.page, repository.draft(editor.boardId)!!.activePage)
        assertEquals(captured, files.thumbnails.single().activePage)
    }

    @Test fun overlappingSaveCannotReleaseTheFirstWriteOrStartAnotherWrite() = runTest {
        val model = model()
        model.create("Serialized Save").join()
        model.canvas(CanvasEvent.Add(note("First")))
        val barrier = CompletableDeferred<Unit>()
        var writes = 0
        repository.beforeSave = {
            writes++
            barrier.await()
        }
        val first = model.save()
        runCurrent()
        model.save().join()
        assertEquals(1, writes)
        assertTrue(model.state.value.editor!!.saving)
        barrier.complete(Unit)
        first.join()
        assertFalse(model.state.value.editor!!.saving)
        assertFalse(model.state.value.editor!!.dirty)
    }

    @Test fun autosaveReschedulesEditsMadeDuringASlowSuccessfulSave() = runTest {
        settings.autosave(true)
        val model = model()
        model.create("Slow Save").join()
        model.canvas(CanvasEvent.Add(note("First")))
        val barrier = CompletableDeferred<Unit>()
        var writes = 0
        repository.beforeSave = { if (++writes == 1) barrier.await() }
        val first = model.save()
        runCurrent()
        model.canvas(CanvasEvent.Add(note("Second")))
        advanceTimeBy(700)
        runCurrent()
        barrier.complete(Unit)
        first.join()
        assertTrue(model.state.value.editor!!.dirty)
        advanceTimeBy(700)
        runCurrent()
        val editor = model.state.value.editor!!
        assertEquals(2, writes)
        assertFalse(editor.dirty)
        assertEquals(2, repository.load(editor.boardId).activePage.elements.size)
        assertNull(repository.draft(editor.boardId))
    }

    @Test fun failedAutosaveDoesNotStartAnUnboundedAutomaticRetryLoop() = runTest {
        settings.autosave(true)
        val model = model()
        model.create("Failed autosave").join()
        var writes = 0
        repository.beforeSave = {
            writes++
            throw IOException("fixture storage full")
        }
        model.canvas(CanvasEvent.Add(note("Keep the recovery")))
        advanceTimeBy(10_000)
        runCurrent()
        val editor = model.state.value.editor!!
        assertEquals(1, writes)
        assertTrue(editor.dirty)
        assertFalse(editor.saving)
        assertNotNull(repository.draft(editor.boardId))
        assertNotNull(model.state.value.message)
    }

    @Test fun failedSaveKeepsOriginalAndAllowsRetry() = runTest {
        val model = model()
        model.create("Save retry").join()
        val id = model.state.value.editor!!.boardId
        val original = repository.load(id)
        model.canvas(CanvasEvent.Add(note("Keep this edit")))
        repository.beforeSave = { throw IOException("fixture write failed") }
        model.save().join()
        assertEquals(original, repository.load(id))
        assertTrue(model.state.value.editor!!.dirty)
        assertFalse(model.state.value.editor!!.saving)
        assertNotNull(model.state.value.message)
        assertTrue(model.state.value.message!!.retry)
        repository.beforeSave = {}
        model.retryFailedOperation()
        runCurrent()
        assertFalse(model.state.value.editor!!.dirty)
        assertEquals(model.state.value.editor!!.page, repository.load(id).activePage)
    }

    @Test fun aDocumentBudgetFailurePreservesDirtyWorkAndDoesNotOfferARetryLoop() = runTest {
        val model = model()
        model.create("Resource limit").join()
        val id = model.state.value.editor!!.boardId
        val saved = repository.load(id)
        model.canvas(CanvasEvent.Add(note("Keep the unsaved edit")))
        repository.beforeSave = { throw DocumentFailure(DocumentError.DOCUMENT_LIMIT) }
        model.save().join()
        assertEquals(saved, repository.load(id))
        assertTrue(model.state.value.editor!!.dirty)
        assertFalse(model.state.value.editor!!.saving)
        val notice = checkNotNull(model.state.value.message)
        assertEquals(R.string.document_limit, notice.resource)
        assertFalse(notice.retry)
    }

    @Test fun acknowledgingAnOldMessageDoesNotDropANewerFailure() = runTest {
        val model = model()
        model.create("Notices").join()
        files.export = { throw IOException("fixture export failed") }
        model.exportPdf("content://fixture/first").join()
        val previous = model.state.value.message!!
        model.exportPdf("content://fixture/second").join()
        val current = model.state.value.message!!
        assertNotEquals(previous.id, current.id)
        model.dismissMessage(previous.id)
        assertEquals(current, model.state.value.message)
        model.dismissMessage(current.id)
        assertNull(model.state.value.message)
    }

    @Test fun recoveredDraftDoesNotReplaceSavedContentUntilSave() = runTest {
        val original = Board(name = "Recovery")
        repository.save(original)
        val draft = original.withPage(original.activePage.copy(elements = listOf(note("Recover me"))))
        repository.saveDraft(draft)
        val model = model()
        model.open(original.id).join()
        assertNotNull(model.state.value.recovery)
        assertNull(model.state.value.editor)
        model.recover(true).join()
        assertTrue(model.state.value.editor!!.dirty)
        assertEquals(draft.activePage, model.state.value.editor!!.page)
        assertEquals(original, repository.load(original.id))
        model.leave(discard = true).join()
        assertNull(model.state.value.editor)
        assertNull(repository.draft(original.id))
        assertEquals(original, repository.load(original.id))
    }

    @Test fun brushPersistenceIsDebouncedWithVirtualTime() = runTest {
        val model = model()
        model.create("Brush").join()
        val color = InkStyle(color = 0xFF12AB34.toInt())
        model.ink(color)
        runCurrent()
        advanceTimeBy(249)
        runCurrent()
        assertNotEquals(color, settings.values.value.lastInk)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(color, settings.values.value.lastInk)
    }

    @Test fun autosaveCommitsCompletedEditsAfterRecoveryDelay() = runTest {
        settings.autosave(true)
        val model = model()
        model.create("Autosave").join()
        model.canvas(CanvasEvent.Add(note("Durable")))
        assertTrue(model.state.value.editor!!.dirty)
        runCurrent()
        advanceTimeBy(700)
        runCurrent()
        val editor = model.state.value.editor!!
        assertFalse(editor.dirty)
        assertEquals(editor.page, repository.load(editor.boardId).activePage)
        assertNull(repository.draft(editor.boardId))
    }

    @Test fun cancellationIsNotDisplayedAsFailureAndBusyStateIsReleased() = runTest {
        val model = model()
        model.create("Cancellation").join()
        files.export = { throw CancellationException("fixture cancellation") }
        val export = model.exportPdf("content://fixture/document")
        export.join()
        assertTrue(export.isCancelled)
        assertFalse(model.state.value.exporting)
        assertNull(model.state.value.message)
        model.theme(ThemeMode.DARK).join()
        runCurrent()
        assertEquals(ThemeMode.DARK, model.state.value.settings.theme)
    }
}
