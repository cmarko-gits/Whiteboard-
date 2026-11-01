package dev.whiteboard.core

import dev.whiteboard.core.cloud.*
import dev.whiteboard.core.model.*
import dev.whiteboard.core.testing.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BackgroundSyncTest {
    private val identity = CloudAccount(newId(), "fixture@example.com")
    private val accounts = InMemoryAccounts(identity)
    private val store = InMemorySyncStore()
    private val requests = InMemoryPublications(store)
    private val remote = InMemoryRemoteBoards()
    private val publisher = PublicationCoordinator(accounts, remote, requests)
    private val coordinator = SyncCoordinator(accounts, remote, store)
    private val runner = BackgroundSyncRunner(accounts, store, requests, publisher, coordinator)

    private fun queued(count: Int, role: CloudBoardRole = CloudBoardRole.OWNER): SyncedDocument {
        val board = Board(name = "Queued")
        val snapshot = CloudBoardSnapshot(board, 0L, role)
        remote.snapshots[board.id] = snapshot
        val state = SyncedDocument(
            identity.id,
            snapshot,
            board.copy(name = "Change $count"),
            count.toLong(),
            (1..count).map { QueuedOperation(newId(), 0L, DocumentDelta(name = ValueChange("Queued", "Change $it"))) }
        )
        store.put(state)
        return state
    }

    @Test fun noExplicitlyQueuedWorkProducesNoNetworkRequests() = runTest {
        assertEquals(SyncPassResult(false, false), runner.run(identity.id))
        assertEquals(0, remote.created)
        assertTrue(remote.submitted.isEmpty())
    }

    @Test fun boundedPassKeepsOriginalOperationIdentitiesAndRequestsContinuation() = runTest {
        val initial = queued(5)
        val result = runner.run(identity.id)
        assertEquals(initial.pending.take(2).map { it.id }, remote.submitted)
        assertEquals(initial.pending.drop(2), store.load(identity.id, initial.working.id)!!.pending)
        assertTrue(result.moreWork)
        assertFalse(result.needsAttention)
    }

    @Test fun permanentBlockedCapturesDoNotProduceEndlessContinuationChains() = runTest {
        repeat(4) { requests.stage(identity.id, Board(name = "Explicit capture $it")) }
        remote.beforeCreate = { throw CloudFailure(CloudError.REQUEST, 403) }
        val result = runner.run(identity.id)
        assertFalse(result.moreWork)
        assertTrue(result.needsAttention)
        assertEquals(SyncStopReason.ACCESS, result.reason)
        assertEquals(0, requests.completed)
    }

    @Test fun conflictPreservesEveryQueuedEditAndRequestsManualReview() = runTest {
        val initial = queued(2)
        remote.beforeSubmit = { throw CloudFailure(CloudError.REQUEST, 409) }
        val result = runner.run(identity.id)
        assertEquals(SyncStopReason.CONFLICT, result.reason)
        assertFalse(result.moreWork)
        assertEquals(initial, store.load(identity.id, initial.working.id))
    }

    @Test fun viewerPendingWorkIsKeptWithoutUnauthorizedSubmission() = runTest {
        val initial = queued(2, CloudBoardRole.VIEWER)
        val result = runner.run(identity.id)
        assertEquals(SyncStopReason.ACCESS, result.reason)
        assertTrue(remote.submitted.isEmpty())
        assertEquals(initial, store.load(identity.id, initial.working.id))
    }

    @Test fun identityChangedDuringRequestCannotInstallItsResponse() = runTest {
        requests.stage(identity.id, Board(name = "Private queued capture"))
        val barrier = CompletableDeferred<Unit>()
        remote.beforeCreate = { barrier.await() }
        val sending = async { runCatching { runner.run(identity.id) }.exceptionOrNull() }
        runCurrent()
        accounts.account.value = CloudAccount(newId(), "other@example.com")
        barrier.complete(Unit)
        assertEquals(CloudError.CREDENTIALS, (sending.await() as CloudFailure).error)
        assertEquals(0, requests.completed)
        assertTrue(store.documents.isEmpty())
    }

    @Test fun observerCancelsPreviousAccountWorkAndNeverUploadsUnqueuedBoards() = runTest {
        val scheduler = RecordingSyncScheduler()
        val observer = ObservePendingSyncWork(accounts, store, requests, scheduler)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { observer() }
        runCurrent()
        assertTrue(scheduler.enqueued.isEmpty())
        requests.stage(identity.id, Board(name = "Queued by explicit action"))
        runCurrent()
        assertEquals(listOf(identity.id), scheduler.enqueued)
        val other = CloudAccount(newId(), "other@example.com")
        accounts.account.value = other
        runCurrent()
        assertEquals(listOf(identity.id), scheduler.cancelled)
        accounts.account.value = null
        runCurrent()
        assertEquals(listOf(identity.id, other.id), scheduler.cancelled)
    }
}
