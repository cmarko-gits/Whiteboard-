package dev.whiteboard.core.cloud

import dev.whiteboard.core.model.Board
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Explicit publication of one captured saved board. Further private edits never silently update this copy. */
class PublicationCoordinator(
    private val accounts: AccountGateway,
    private val boards: BoardGateway,
    private val publications: PublicationStore
) {
    private val mutex = Mutex()

    suspend fun publish(accountId: String, source: Board): SyncedDocument = mutex.withLock {
        requireCurrent(accountId)
        send(publications.stage(accountId, source))
    }

    suspend fun retry(accountId: String, sourceId: String): SyncedDocument = mutex.withLock {
        requireCurrent(accountId)
        send(publications.find(accountId, sourceId) ?: throw PublicationFailure(PublicationError.MISSING))
    }

    private suspend fun send(request: Publication): SyncedDocument {
        requireCurrent(request.accountId)
        val snapshot = request.remoteId?.let { boards.load(request.accountId, it) } ?: boards.create(
            request.accountId,
            request.operationId,
            checkNotNull(request.document)
        )
        requireCurrent(request.accountId)
        return publications.complete(request.accountId, request.sourceId, request.operationId, snapshot)
    }

    private fun requireCurrent(id: String) {
        if (accounts.account.value?.id != id) throw CloudFailure(CloudError.CREDENTIALS)
    }
}
