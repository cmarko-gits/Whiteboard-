package dev.whiteboard.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import dev.whiteboard.core.cloud.*
import dev.whiteboard.core.model.Board
import dev.whiteboard.core.model.newId
import dev.whiteboard.core.testing.*
import dev.whiteboard.sync.CloudSyncWorker
import dev.whiteboard.sync.CloudWorkerFactory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real Worker/Factory with disposable in-memory account partitions. */
@RunWith(AndroidJUnit4::class)
class CloudWorkerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val identity = CloudAccount(newId(), "worker@example.com")
    private val accounts = InMemoryAccounts(identity)
    private val store = InMemorySyncStore()
    private val publications = InMemoryPublications(store)
    private val remote = InMemoryRemoteBoards()
    private val origin = "https://worker.example.com"
    private val runner = BackgroundSyncRunner(
        accounts,
        store,
        publications,
        PublicationCoordinator(accounts, remote, publications),
        SyncCoordinator(accounts, remote, store)
    )

    private fun worker(accountId: String = identity.id, requestOrigin: String = origin, attempt: Int = 0) =
        TestListenableWorkerBuilder<CloudSyncWorker>(context)
            .setWorkerFactory(CloudWorkerFactory(accounts, runner, origin))
            .setInputData(workDataOf("account" to accountId, "origin" to requestOrigin))
            .setRunAttemptCount(attempt).build()

    @Test fun restoredWorkCannotCrossAccountOrOriginBoundaries(): Unit = runBlocking {
        publications.stage(identity.id, Board(name = "Queued"))
        assertEquals(ListenableWorker.Result.failure(workDataOf("reason" to "ACCOUNT")), worker(accountId = newId()).doWork())
        assertEquals(
            ListenableWorker.Result.failure(workDataOf("reason" to "UNAVAILABLE")),
            worker(requestOrigin = "https://other.example.com").doWork()
        )
        assertEquals(0, remote.created)
        assertEquals(0, publications.completed)
    }

    @Test fun successfulPassPublishesOnlyExplicitCapture(): Unit = runBlocking {
        publications.stage(identity.id, Board(name = "Queued"))
        assertEquals(ListenableWorker.Result.success(), worker().doWork())
        assertEquals(1, remote.created)
        assertEquals(1, publications.completed)
    }

    @Test fun temporaryNetworkFailureIsRetriedWithAFiniteAttemptLimit(): Unit = runBlocking {
        publications.stage(identity.id, Board(name = "Queued"))
        remote.beforeCreate = { throw CloudFailure(CloudError.OFFLINE) }
        assertEquals(ListenableWorker.Result.retry(), worker().doWork())
        assertEquals(ListenableWorker.Result.failure(workDataOf("reason" to "SERVER")), worker(attempt = 5).doWork())
        assertEquals(0, publications.completed)
    }

    @Test fun rejectedCertificateAndDeniedAccessStopWithoutDroppingCapture(): Unit = runBlocking {
        publications.stage(identity.id, Board(name = "Queued"))
        remote.beforeCreate = { throw CloudFailure(CloudError.SERVER, code = "tls_validation_failed") }
        assertEquals(ListenableWorker.Result.failure(workDataOf("reason" to "SERVER")), worker().doWork())
        remote.beforeCreate = { throw CloudFailure(CloudError.REQUEST, 403) }
        assertEquals(ListenableWorker.Result.failure(workDataOf("reason" to "ACCESS")), worker().doWork())
        assertEquals(0, publications.completed)
    }
}
