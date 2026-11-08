package dev.whiteboard.app

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteFullException
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.whiteboard.core.model.*
import dev.whiteboard.data.LocalBoardRepository
import dev.whiteboard.data.WhiteboardDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FailedWriteTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "failed-write-${newId()}.db"
    private val database = Room.databaseBuilder(context, WhiteboardDatabase::class.java, name)
        .setJournalMode(RoomDatabase.JournalMode.TRUNCATE).build()
    private val repository = LocalBoardRepository(database)

    @After fun close() {
        database.close()
        context.deleteDatabase(name)
    }

    @Test fun fullDatabaseKeepsSavedBoardAndIndependentDraft(): Unit = runBlocking {
        val saved = Board(name = "Keep this version")
        repository.save(saved)
        val draft = saved.withPage(
            saved.activePage.copy(elements = listOf(Note(bounds = Bounds(0f, 0f, 200f, 200f), text = "Unsaved work")))
        )
        repository.saveDraft(draft)
        val sqlite = database.openHelper.writableDatabase
        val pages = sqlite.query("PRAGMA page_count").use { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
        }
        sqlite.query("PRAGMA max_page_count = ${pages + 2}").use { cursor ->
            check(cursor.moveToFirst())
            assertEquals(pages + 2, cursor.getInt(0))
        }
        val large = saved.copy(
            name = "Must not be committed",
            pages = listOf(
                saved.activePage.copy(
                    elements = listOf(
                        Note(bounds = Bounds(0f, 0f, 200f, 200f), text = "Storage fixture ".repeat(20_000))
                    )
                )
            )
        )
        try {
            repository.save(large)
            fail("Expected an actual SQLite capacity failure")
        } catch (failure: DocumentFailure) {
            assertEquals(DocumentError.STORAGE_FULL, failure.error)
            assertTrue(failure.cause is SQLiteFullException)
            // The test caps only this disposable database; it never fills the device's storage.
        }
        assertEquals(saved, repository.load(saved.id))
        assertEquals(draft, repository.draft(saved.id))
        assertEquals(listOf(saved), repository.savedBoards())
    }

    @Test fun cancelledTransactionDoesNotExposePartialRevision(): Unit = runBlocking {
        val saved = Board(name = "Before cancellation")
        repository.save(saved)
        try {
            database.withTransaction {
                repository.save(saved.copy(name = "Cancelled revision"))
                throw CancellationException("Simulated interruption before commit")
            }
        } catch (_: CancellationException) { }
        assertEquals(saved, repository.load(saved.id))
        assertEquals(listOf(saved), repository.savedBoards())
    }

    @Test fun aLaterImportWriteFailureRollsBackEarlierCopiesAndTheirSearchIndex(): Unit = runBlocking {
        val saved = Board(name = "Preserve saved original")
        val draft = saved.copy(name = "Preserve independent recovery")
        repository.save(saved)
        repository.saveDraft(draft)
        // Prepare the index before fault injection, so a later search cannot hide an orphan.
        assertEquals(setOf(saved.id), repository.search("saved original"))
        val first = Board(name = "Rollback sentinel")
        val blocked = Board(name = "Reject second copy")
        val sqlite = database.openHelper.writableDatabase
        sqlite.execSQL(
            "CREATE TRIGGER reject_import_fixture BEFORE INSERT ON boards " +
                "WHEN NEW.name = 'Reject second copy (imported)' " +
                "BEGIN SELECT RAISE(ABORT, 'fixture second import write failed'); END"
        )
        try {
            try {
                repository.importBoards(listOf(first, blocked))
                fail("The second write must fail after the first copy was inserted")
            } catch (_: SQLiteConstraintException) { }
            assertEquals(listOf(saved), repository.savedBoards())
            assertEquals(saved, repository.load(saved.id))
            assertEquals(draft, repository.draft(saved.id))
            assertTrue(repository.search("Rollback sentinel").isEmpty())
            assertTrue(repository.search("Reject second copy").isEmpty())
            assertEquals(setOf(saved.id), repository.search("saved original"))
            sqlite.query("SELECT COUNT(*) FROM document_chunks WHERE boardId != ?", arrayOf(saved.id)).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
        } finally {
            sqlite.execSQL("DROP TRIGGER reject_import_fixture")
        }
        repository.importBoards(listOf(first, blocked))
        assertEquals(3, repository.savedBoards().size)
        assertEquals(1, repository.search("Rollback sentinel").size)
        assertEquals(1, repository.search("Reject second copy").size)
        assertEquals(saved, repository.load(saved.id))
        assertEquals(draft, repository.draft(saved.id))
    }
}
