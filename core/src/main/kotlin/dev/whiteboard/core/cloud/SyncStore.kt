package dev.whiteboard.core.cloud

import dev.whiteboard.core.model.Board
import kotlinx.coroutines.flow.Flow

data class QueuedOperation(val id: String, val baseRevision: Long, val delta: DocumentDelta)
data class SyncConflict(val reason: DeltaConflictReason, val targetId: String)
data class SyncedDocument(
    val accountId: String,
    val shadow: CloudBoardSnapshot,
    val working: Board,
    val localVersion: Long,
    val pending: List<QueuedOperation>,
    val conflict: SyncConflict? = null
)
data class OperationAcknowledgment(val id: String, val revision: Long)
enum class SyncError { MISSING, STALE_EDIT, CONFLICT, READ_ONLY, QUEUE_FULL, INVALID_ACKNOWLEDGMENT, STALE_SNAPSHOT }
class SyncFailure(val error: SyncError) : Exception(error.name)

data class CachedCloudBoard(
    val id: String,
    val name: String,
    val revision: Long,
    val role: CloudBoardRole,
    val pending: Int,
    val conflicted: Boolean
)

/** Account-partitioned cache. Local edits and their immutable outgoing operations commit in one transaction. */
interface SyncStore {
    fun observeBoards(accountId: String): Flow<List<CachedCloudBoard>>
    suspend fun install(accountId: String, snapshot: CloudBoardSnapshot): SyncedDocument
    suspend fun load(accountId: String, boardId: String): SyncedDocument?
    suspend fun edit(accountId: String, board: Board, expectedLocalVersion: Long, operationId: String): SyncedDocument

    /** Only a fresh authoritative snapshot covering the acknowledgment can retire the queue head. */
    suspend fun advance(accountId: String, snapshot: CloudBoardSnapshot, acknowledgment: OperationAcknowledgment? = null): SyncedDocument
}
