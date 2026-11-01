package dev.whiteboard.app.workspace

import androidx.lifecycle.viewModelScope
import dev.whiteboard.canvas.CanvasEvent
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
    private fun model() =
        WorkspaceViewModel(EditorPersistence(repository), ManagePreferences(settings), DocumentTransfers(files)).also(models::add)
    private fun note(text: String) = Note(bounds = Bounds(10f, 10f, 200f, 200f), text = text)

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
        assertEquals(60f, model.state.value.editor!!.page.elements.single().bounds.left)
    }

    @After fun clearModels() {
        models.forEach { it.viewModelScope.cancel() }
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
