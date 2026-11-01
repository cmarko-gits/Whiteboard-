package dev.whiteboard.core.cloud

import dev.whiteboard.core.model.Board

enum class CloudBoardRole { OWNER, EDITOR, COMMENTER, VIEWER }
data class CloudBoardSummary(val id: String, val name: String, val revision: Long, val role: CloudBoardRole)
data class CloudBoardPage(val items: List<CloudBoardSummary>, val next: String?)
data class CloudBoardSnapshot(val document: Board, val revision: Long, val role: CloudBoardRole)
data class CloudOperation(val id: String, val revision: Long, val actorId: String?, val createdAt: Long, val delta: DocumentDelta)
data class CloudChanges(val operations: List<CloudOperation>, val nextRevision: Long, val currentRevision: Long)

/** Every request names the owning local account partition. A session change invalidates in-flight results. */
interface BoardGateway {
    suspend fun list(accountId: String, after: String? = null): CloudBoardPage
    suspend fun create(accountId: String, operationId: String, document: Board): CloudBoardSnapshot
    suspend fun load(accountId: String, boardId: String): CloudBoardSnapshot
    suspend fun submit(accountId: String, boardId: String, operationId: String, baseRevision: Long, delta: DocumentDelta): Long
    suspend fun changes(accountId: String, boardId: String, after: Long): CloudChanges
    suspend fun delete(accountId: String, boardId: String)
}
