package dev.whiteboard.core.cloud

import kotlinx.coroutines.flow.first

data class SyncPassResult(val moreWork: Boolean, val needsAttention: Boolean, val reason: SyncStopReason? = null)

/** Processes only captures/edits that were already explicitly queued. It never reads private originals. */
class BackgroundSyncRunner(
    private val accounts: AccountGateway,
    private val store: SyncStore,
    private val publications: PublicationStore,
    private val publisher: PublicationCoordinator,
    private val synchronization: SyncCoordinator
) {
    suspend fun run(accountId: String): SyncPassResult {
        requireCurrent(accountId)
        val blockedPublications = mutableSetOf<String>()
        val blockedBoards = mutableSetOf<String>()
        var attention = false
        var reason: SyncStopReason? = null
        suspend fun process(id: String, blocked: MutableSet<String>, action: suspend () -> Unit) {
            requireCurrent(accountId)
            try {
                action()
            } catch (error: CloudFailure) {
                if (error.error != CloudError.REQUEST || error.status !in setOf(400, 403, 404, 409, 413)) throw error
                blocked += id
                attention = true
                reason = when (error.status) {
                    409 -> SyncStopReason.CONFLICT
                    413 -> SyncStopReason.LIMIT
                    else -> SyncStopReason.ACCESS
                }
            } catch (error: PublicationFailure) {
                blocked += id
                attention = true
                reason =
                    if (error.error in
                        setOf(PublicationError.TOO_LARGE, PublicationError.QUEUE_FULL)
                    ) {
                        SyncStopReason.LIMIT
                    } else {
                        SyncStopReason.ACCESS
                    }
            } catch (_: SyncFailure) {
                blocked += id
                attention = true
                reason = SyncStopReason.CONFLICT
            }
            requireCurrent(accountId)
        }
        publications.observePending(accountId).first().take(3).forEach { request ->
            process(request.sourceId, blockedPublications) { publisher.retry(accountId, request.sourceId) }
        }
        val cached = store.observeBoards(accountId).first()
        attention = attention || cached.any { it.conflicted || it.pending > 0 && it.role !in WRITABLE }
        if (reason == null && attention) reason = if (cached.any { it.conflicted }) SyncStopReason.CONFLICT else SyncStopReason.ACCESS
        cached.filter { it.pending > 0 && !it.conflicted && it.role in WRITABLE }.take(3).forEach { board ->
            process(board.id, blockedBoards) {
                val result = synchronization.reconcile(accountId, board.id, maximumOperations = 2)
                if (result.conflict != null || result.shadow.role !in WRITABLE) {
                    blockedBoards += board.id
                    attention = true
                    reason = if (result.conflict != null) SyncStopReason.CONFLICT else SyncStopReason.ACCESS
                }
            }
        }
        requireCurrent(accountId)
        val remainingPublications = publications.observePending(accountId).first().any { it.sourceId !in blockedPublications }
        val remainingBoards = store.observeBoards(accountId).first().any {
            it.pending > 0 && !it.conflicted && it.role in WRITABLE && it.id !in blockedBoards
        }
        requireCurrent(accountId)
        // A permanently blocked head must not create an endless chain that retries the same first batch.
        return SyncPassResult((remainingPublications || remainingBoards) && !attention, attention, reason)
    }
    private fun requireCurrent(accountId: String) {
        if (accounts.account.value?.id != accountId) throw CloudFailure(CloudError.CREDENTIALS)
    }
    companion object {
        private val WRITABLE = setOf(CloudBoardRole.OWNER, CloudBoardRole.EDITOR)
    }
}
