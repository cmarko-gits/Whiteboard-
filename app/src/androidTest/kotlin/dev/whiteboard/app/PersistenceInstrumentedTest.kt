package dev.whiteboard.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.whiteboard.core.model.*
import dev.whiteboard.data.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersistenceInstrumentedTest {
    private val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WhiteboardDatabase::class.java).build()
    private val repository = LocalBoardRepository(database)

    @After fun close() = database.close()

    @Test fun savedDocumentAndDraftRemainIndependent() = runBlocking {
        val saved = Board(name = "Storage test")
        repository.save(saved)
        val draft = saved.withPage(saved.activePage.copy(elements = listOf(Note(bounds = Bounds(0f, 0f, 240f, 220f), text = "Unsaved"))))
        repository.saveDraft(draft)
        assertEquals(saved, repository.load(saved.id))
        assertEquals(draft, repository.draft(saved.id))
        repository.discardDraft(saved.id)
        assertNull(repository.draft(saved.id))
        assertEquals(saved, repository.load(saved.id))
    }

    @Test fun trashRestoreAndPermanentDeleteHaveDifferentSemantics() = runBlocking {
        val board = Board(name = "Trash test")
        repository.save(board)
        repository.saveDraft(board)
        repository.trash(board.id)
        assertNotNull(repository.load(board.id).trashedAt)
        repository.restore(board.id)
        assertNull(repository.load(board.id).trashedAt)
        repository.deletePermanently(board.id)
        assertNull(repository.draft(board.id))
        try {
            repository.load(board.id)
            fail("Deleted board should not load")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun backupImportsAsCopiesWithoutOverwritingExistingBoards() = runBlocking {
        val original = Board(name = "Original")
        repository.save(original)
        repository.importBoards(listOf(original))
        val boards = repository.savedBoards()
        assertEquals(2, boards.size)
        assertEquals(original, repository.load(original.id))
        assertEquals(2, boards.map { it.id }.distinct().size)
    }
}
