package dev.whiteboard.core.cloud

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One bounded synchronization pass. Scheduling/backoff belongs to the Android background-work adapter. */
class SyncCoordinator(private val accounts: AccountGateway, private val boards: BoardGateway, private val store: SyncStore) {
    private val mutex = Mutex()

    suspend fun reconcile(accountId: String, boardId: String, maximumOperations: Int = 20): SyncedDocument = mutex.withLock {
        require(maximumOperations in 1..20)
        requireCurrent(accountId)
        val cached = store.load(accountId, boardId)
        var state = if (cached == null) {
            val snapshot = boards.load(accountId, boardId)
            requireCurrent(accountId)
            store.install(accountId, snapshot)
        } else {
            cached
        }
        if (cached == null && state.pending.isEmpty()) return@withLock state
        if (state.pending.isEmpty()) {
            val snapshot = boards.load(accountId, boardId)
            requireCurrent(accountId)
            return@withLock store.advance(accountId, snapshot)
        }
        repeat(maximumOperations) {
            requireCurrent(accountId)
            val head = state.pending.firstOrNull() ?: return@withLock state
            if (state.shadow.role !in setOf(CloudBoardRole.OWNER, CloudBoardRole.EDITOR)) return@withLock state
            // Never rewrite a queued body/base or mint another ID after an ambiguous network completion.
            val revision = boards.submit(accountId, boardId, head.id, head.baseRevision, head.delta)
            requireCurrent(accountId)
            val snapshot = boards.load(accountId, boardId)
            requireCurrent(accountId)
            state = store.advance(accountId, snapshot, OperationAcknowledgment(head.id, revision))
            if (state.conflict != null) return@withLock state
        }
        state
    }

    private fun requireCurrent(accountId: String) {
        if (accounts.account.value?.id != accountId) throw CloudFailure(CloudError.CREDENTIALS)
    }
}
