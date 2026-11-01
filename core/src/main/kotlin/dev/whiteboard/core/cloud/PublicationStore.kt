package dev.whiteboard.core.cloud

import dev.whiteboard.core.model.Board
import kotlinx.coroutines.flow.Flow

data class Publication(
    val accountId: String,
    val sourceId: String,
    val name: String,
    val operationId: String,
    val document: Board?,
    val remoteId: String? = null
)
data class PendingPublication(val sourceId: String, val name: String)
enum class PublicationError { MISSING, MEDIA_UNAVAILABLE, TOO_LARGE, QUEUE_FULL, INVALID_RECEIPT }
class PublicationFailure(val error: PublicationError) : Exception(error.name)

interface PublicationStore {
    fun observePending(accountId: String): Flow<List<PendingPublication>>
    suspend fun stage(accountId: String, source: Board): Publication
    suspend fun find(accountId: String, sourceId: String): Publication?

    /** Installs the shared cache and completes the creation receipt in one transaction. */
    suspend fun complete(accountId: String, sourceId: String, operationId: String, snapshot: CloudBoardSnapshot): SyncedDocument
}
