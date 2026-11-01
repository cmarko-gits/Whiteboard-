package dev.whiteboard.data.cloud

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.whiteboard.core.cloud.*
import dev.whiteboard.core.model.*
import dev.whiteboard.data.WhiteboardDatabase
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomSyncStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "sync-store-${newId()}.db"
    private var database = open()
    private var store = RoomSyncStore(database)
    private val first = Note(bounds = Bounds(0f, 0f, 100f, 100f), text = "First")
    private val second = Note(bounds = Bounds(200f, 0f, 300f, 100f), text = "Second")
    private val board = Board(name = "Shared fixture", pages = listOf(Page(elements = listOf(first, second))))
    private val initial = CloudBoardSnapshot(board, 0, CloudBoardRole.OWNER)
    private fun open() = Room.databaseBuilder(context, WhiteboardDatabase::class.java, databaseName).build()
    private fun changed(source: Board, note: Note) = source.withPage(
        source.activePage.copy(elements = source.activePage.elements.map { if (it.id == note.id) note else it })
    )

    @After fun clean() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test fun editsSurviveDatabaseReopenAndRemainPartitionedByAccount(): Unit = runBlocking {
        store.install("alice", initial)
        store.install("bob", initial)
        val content = "Ћ 🖊".repeat(130_000)
        val operationId = newId()
        val edited = changed(board, first.copy(text = content))
        store.edit("alice", edited, 0, operationId)
        database.close()
        database = open()
        store = RoomSyncStore(database)
        val restored = checkNotNull(store.load("alice", board.id))
        assertEquals(edited, restored.working)
        assertEquals(board, restored.shadow.document)
        assertEquals(operationId, restored.pending.single().id)
        assertEquals(0L, restored.pending.single().baseRevision)
        assertEquals(board, store.load("bob", board.id)?.working)
        assertTrue(checkNotNull(store.load("bob", board.id)).pending.isEmpty())
        assertNull(store.load("unknown", board.id))
        assertTrue(database.sync().operationBody("alice", operationId).size > 1)
    }

    @Test fun failedChunkWriteRollsBackVisibleEditAndQueueTogether(): Unit = runBlocking {
        store.install("alice", initial)
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_sync_chunk BEFORE INSERT ON sync_operation_chunks WHEN NEW.part = 1 BEGIN SELECT RAISE(ABORT, 'test fault'); END"
        )
        try {
            store.edit("alice", changed(board, first.copy(text = "🙂".repeat(140_000))), 0, newId())
            fail("Expected injected storage failure")
        } catch (_: SQLiteException) { }
        val unchanged = checkNotNull(store.load("alice", board.id))
        assertEquals(board, unchanged.working)
        assertEquals(0L, unchanged.localVersion)
        assertTrue(unchanged.pending.isEmpty())
        assertEquals(0L, database.sync().queuedBytes("alice"))
    }

    @Test fun competingLocalEditsCannotOverwriteEachOther(): Unit = runBlocking {
        store.install("alice", initial)
        val results = coroutineScope {
            listOf(
                async { runCatching { store.edit("alice", changed(board, first.copy(text = "A")), 0, newId()) } },
                async { runCatching { store.edit("alice", changed(board, second.copy(text = "B")), 0, newId()) } }
            ).map { it.await() }
        }
        assertEquals(1, results.count { it.isSuccess })
        assertEquals(SyncError.STALE_EDIT, (results.single { it.isFailure }.exceptionOrNull() as SyncFailure).error)
        assertEquals(1, store.load("alice", board.id)?.pending?.size)
    }

    @Test fun failedAcknowledgmentWriteKeepsQueueAndPreviousShadow(): Unit = runBlocking {
        store.install("alice", initial)
        val local = changed(board, first.copy(text = "Accepted on server"))
        val id = newId()
        val before = store.edit("alice", local, 0, id)
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_sync_ack BEFORE INSERT ON sync_documents WHEN NEW.kind = 'working' BEGIN SELECT RAISE(ABORT, 'test fault'); END"
        )
        try {
            store.advance("alice", initial.copy(document = local, revision = 1), OperationAcknowledgment(id, 1))
            fail("Expected injected storage failure")
        } catch (_: SQLiteException) { }
        assertEquals(before, store.load("alice", board.id))
        assertTrue(database.sync().operationBody("alice", id).isNotEmpty())
    }

    @Test fun remoteIndependentEditRebasesQueueAndAcknowledgmentRetiresOnlyItsHead(): Unit = runBlocking {
        store.install("alice", initial)
        val local = changed(board, first.copy(text = "Local edit"))
        val id = newId()
        val firstEdit = store.edit("alice", local, 0, id)
        val secondId = newId()
        store.edit("alice", changed(local, first.copy(text = "Second local edit")), firstEdit.localVersion, secondId)
        val remote = changed(board, second.copy(text = "Remote edit"))
        val rebased = store.advance("alice", initial.copy(document = remote, revision = 1))
        assertNull(rebased.conflict)
        assertEquals(changed(remote, first.copy(text = "Second local edit")).activePage.elements, rebased.working.activePage.elements)
        assertEquals(listOf(0L, 0L), rebased.pending.map { it.baseRevision })
        val latest = initial.copy(document = changed(remote, first.copy(text = "Local edit")), revision = 2)
        rejected(SyncError.INVALID_ACKNOWLEDGMENT) { store.advance("alice", latest, OperationAcknowledgment(newId(), 2)) }
        rejected(SyncError.INVALID_ACKNOWLEDGMENT) { store.advance("alice", latest, OperationAcknowledgment(id, 3)) }
        assertEquals(2, store.load("alice", board.id)?.pending?.size)
        val accepted = store.advance("alice", latest, OperationAcknowledgment(id, 2))
        assertEquals(secondId, accepted.pending.single().id)
        assertEquals(latest.document, accepted.shadow.document)
        assertEquals(rebased.working, accepted.working)
        assertTrue(database.sync().operationBody("alice", id).isEmpty())
        val completed = store.advance(
            "alice",
            latest.copy(document = accepted.working, revision = 3),
            OperationAcknowledgment(secondId, 3)
        )
        assertTrue(completed.pending.isEmpty())
    }

    @Test fun remoteConflictKeepsLocalWorkAndRejectsFurtherUntrackedEdits(): Unit = runBlocking {
        store.install("alice", initial)
        val local = changed(board, first.copy(text = "Local text"))
        val queued = store.edit("alice", local, 0, newId())
        val remote = changed(board, first.copy(text = "Someone else's text"))
        val conflicted = store.advance("alice", initial.copy(document = remote, revision = 1))
        assertEquals(DeltaConflictReason.OBJECT_MODIFIED, conflicted.conflict?.reason)
        assertEquals(local, conflicted.working)
        assertEquals(remote, conflicted.shadow.document)
        assertEquals(queued.pending, conflicted.pending)
        rejected(SyncError.CONFLICT) { store.edit("alice", local.copy(name = "More changes"), conflicted.localVersion, newId()) }
        rejected(SyncError.STALE_SNAPSHOT) { store.advance("alice", initial) }
        assertEquals(conflicted, store.load("alice", board.id))
    }

    @Test fun readOnlyGrantPreservesPendingEditsButPreventsNewOnes(): Unit = runBlocking {
        store.install("alice", initial)
        val queued = store.edit("alice", changed(board, first.copy(text = "Pending")), 0, newId())
        val restricted = store.advance("alice", initial.copy(role = CloudBoardRole.VIEWER))
        assertEquals(queued.pending, restricted.pending)
        rejected(SyncError.READ_ONLY) { store.edit("alice", restricted.working.copy(name = "Forbidden"), restricted.localVersion, newId()) }
    }

    private suspend fun rejected(error: SyncError, action: suspend () -> Unit) {
        try {
            action()
            fail("Expected $error")
        } catch (failure: SyncFailure) {
            assertEquals(error, failure.error)
        }
    }
}
