package dev.whiteboard.core.cloud

import kotlinx.coroutines.flow.Flow

enum class SyncWorkState { IDLE, WAITING, RUNNING, COMPLETED, NEEDS_ATTENTION }
enum class SyncStopReason { ACCOUNT, ACCESS, CONFLICT, LIMIT, UNAVAILABLE, SERVER }
data class SyncWorkStatus(val state: SyncWorkState, val reason: SyncStopReason? = null)

interface SyncScheduler {
    fun enqueue(accountId: String)
    fun cancel(accountId: String)
    fun observe(accountId: String): Flow<SyncWorkStatus>
}
