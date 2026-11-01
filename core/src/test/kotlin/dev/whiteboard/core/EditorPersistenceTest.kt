package dev.whiteboard.core

import dev.whiteboard.core.editor.DocumentSession
import dev.whiteboard.core.model.*
import dev.whiteboard.core.testing.InMemoryBoardRepository
import dev.whiteboard.core.usecase.EditorPersistence
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditorPersistenceTest {
    @Test fun editsDuringSaveRemainDirtyAndRecoverable() = runTest {
        val repository = InMemoryBoardRepository()
        val original = Board(name = "Before")
        repository.save(original)
        val session = DocumentSession(original)
        session.add(Note(bounds = Bounds(0f, 0f, 100f, 100f), text = "First edit"))
        val captured = session.board
        val barrier = CompletableDeferred<Unit>()
        repository.beforeSave = { barrier.await() }
        val saving = async { EditorPersistence(repository).save(session) }
        runCurrent()
        session.add(Note(bounds = Bounds(200f, 0f, 300f, 100f), text = "Edit during Save"))
        barrier.complete(Unit)
        saving.await()
        assertEquals(captured, repository.load(original.id))
        assertEquals(session.board, repository.draft(original.id))
        assertTrue(session.dirty)
    }

    @Test fun failedOrCancelledSaveDoesNotMarkRevisionSaved() = runTest {
        for (failure in listOf(IOException("fixture write failed"), CancellationException("fixture cancellation"))) {
            val repository = InMemoryBoardRepository()
            val original = Board(name = "Keep")
            repository.save(original)
            val session = DocumentSession(original)
            session.add(Note(bounds = Bounds(0f, 0f, 100f, 100f), text = "Unsaved"))
            repository.beforeSave = { throw failure }
            assertSame(failure, runCatching { EditorPersistence(repository).save(session) }.exceptionOrNull())
            assertEquals(original, repository.load(original.id))
            assertTrue(session.dirty)
            assertEquals(0L, session.savedRevision)
        }
    }

    @Test fun recoveryUsesCurrentPrivateMetadataWithoutReplacingDraftContent() = runTest {
        val repository = InMemoryBoardRepository()
        val original = Board(name = "Renamed", favorite = true, folderId = "current-folder")
        repository.save(original)
        val draft = original.copy(
            name = "Old name",
            favorite = false,
            folderId = null,
            pages = listOf(original.activePage.copy(backgroundColor = 0xFF123456.toInt()))
        )
        repository.saveDraft(draft)
        val opened = EditorPersistence(repository).open(original.id)
        assertEquals(original, opened.saved)
        assertEquals(draft.copy(name = original.name, favorite = true, folderId = original.folderId), opened.recovery)
    }

    @Test fun recoveryAndDiscardWaitForSaveToFinish() = runTest {
        val repository = InMemoryBoardRepository()
        val session = DocumentSession(Board(name = "Serialized"))
        session.add(Note(bounds = Bounds(0f, 0f, 100f, 100f), text = "Draft"))
        val persistence = EditorPersistence(repository)
        val barrier = CompletableDeferred<Unit>()
        repository.beforeSave = { barrier.await() }
        val saving = async { persistence.save(session) }
        runCurrent()
        val flushing = async { persistence.flush(session) }
        val discarding = async { persistence.discardDraft(session.board.id) }
        runCurrent()
        assertFalse(flushing.isCompleted)
        assertFalse(discarding.isCompleted)
        barrier.complete(Unit)
        saving.await()
        flushing.await()
        discarding.await()
        assertFalse(session.dirty)
        assertNull(repository.draft(session.board.id))
    }

    @Test fun privateCopyHasIndependentOwnershipMetadataAndContent() = runTest {
        val repository = InMemoryBoardRepository()
        val source = Board(name = "Shared", favorite = true, folderId = "owner-folder", trashedAt = 50L)
        val copy = EditorPersistence(repository).createPrivateCopy(source, "My copy")
        assertNotEquals(source.id, copy.id)
        assertFalse(copy.favorite)
        assertNull(copy.folderId)
        assertNull(copy.trashedAt)
        assertEquals(source.pages, copy.pages)
        assertEquals(copy, repository.load(copy.id))
        assertFalse(repository.saved.containsKey(source.id))
    }
}
