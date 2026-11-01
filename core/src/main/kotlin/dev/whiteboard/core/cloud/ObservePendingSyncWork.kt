package dev.whiteboard.core.cloud

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*

/** Application-lifetime observer. Account changes cancel scheduling for the preceding partition. */
class ObservePendingSyncWork(
    private val accounts: AccountGateway,
    private val store: SyncStore,
    private val publications: PublicationStore,
    private val scheduler: SyncScheduler
) {
    suspend operator fun invoke() {
        var previous: String? = null
        accounts.account.collectLatest { identity ->
            val accountId = identity?.id
            previous?.takeIf { it != accountId }?.let(scheduler::cancel)
            previous = accountId
            if (accountId != null) {
                combine(store.observeBoards(accountId), publications.observePending(accountId)) { boards, pending ->
                    pending.isNotEmpty() || boards.any {
                        it.pending > 0 && !it.conflicted &&
                            it.role in setOf(CloudBoardRole.OWNER, CloudBoardRole.EDITOR)
                    }
                }.distinctUntilChanged().retryWhen { error, attempt ->
                    if (error is CancellationException) throw error
                    if (attempt >= 3) {
                        false
                    } else {
                        delay(1000L * (attempt + 1))
                        true
                    }
                }.catch {
                    /* Cache errors must not crash startup or trigger unbounded retries. The cloud UI exposes its own read failure. */
                }
                    .collect { pending -> if (pending && accounts.account.value?.id == accountId) scheduler.enqueue(accountId) }
            }
        }
    }
}
