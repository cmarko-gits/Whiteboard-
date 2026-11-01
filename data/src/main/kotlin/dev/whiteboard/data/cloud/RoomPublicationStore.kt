package dev.whiteboard.data.cloud

import androidx.room.withTransaction
import dev.whiteboard.core.cloud.*
import dev.whiteboard.core.model.*
import dev.whiteboard.data.WhiteboardDatabase
import dev.whiteboard.data.chunkDocument
import dev.whiteboard.format.DocumentCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RoomPublicationStore(private val database: WhiteboardDatabase) : PublicationStore {
    private val dao = database.publications()
    private val codec = DocumentCodec()
    private val synchronized = RoomSyncStore(database)

    override fun observePending(accountId: String) = dao.observePending(accountId)

    override suspend fun stage(accountId: String, source: Board): Publication = withContext(Dispatchers.IO) {
        require(accountId.isNotBlank())
        database.withTransaction {
            read(accountId, source.id) ?: run {
                if (source.referencedAssets().isNotEmpty()) throw PublicationFailure(PublicationError.MEDIA_UNAVAILABLE)
                val captured = source.copy(
                    favorite = false,
                    folderId = null,
                    trashedAt = null,
                    activePageId = source.pages.first().id,
                    pages = source.pages.map { it.copy(viewport = Viewport()) }
                )
                val operationId = newId()
                val content = codec.encode(captured)
                if (CloudPayloads.bytes(CloudPayloads.creation(operationId, content)) > CloudPayloads.MAX_REQUEST_BYTES) {
                    throw PublicationFailure(PublicationError.TOO_LARGE)
                }
                val bytes = content.toByteArray(Charsets.UTF_8).size.toLong()
                if (dao.pendingCount(accountId) >= 100 || dao.count(accountId) >= 10_000 ||
                    dao.payloadBytes(accountId) + bytes > 32L * 1024 * 1024
                ) {
                    throw PublicationFailure(PublicationError.QUEUE_FULL)
                }
                dao.insert(PublicationEntity(accountId, source.id, source.name, operationId, null, bytes))
                dao.chunks(chunkDocument(content).mapIndexed { part, value -> PublicationChunk(accountId, source.id, part, value) })
                Publication(accountId, source.id, source.name, operationId, captured)
            }
        }
    }

    override suspend fun find(accountId: String, sourceId: String): Publication? = withContext(Dispatchers.IO) {
        database.withTransaction { read(accountId, sourceId) }
    }

    override suspend fun complete(accountId: String, sourceId: String, operationId: String, snapshot: CloudBoardSnapshot): SyncedDocument =
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val request = dao.find(accountId, sourceId) ?: throw PublicationFailure(PublicationError.MISSING)
                if (request.operationId != operationId || snapshot.role != CloudBoardRole.OWNER ||
                    (request.remoteId != null && request.remoteId != snapshot.document.id)
                ) {
                    throw PublicationFailure(PublicationError.INVALID_RECEIPT)
                }
                val installed = synchronized.install(accountId, snapshot)
                val state = if (installed.pending.isEmpty() && snapshot.revision >= installed.shadow.revision &&
                    installed.shadow != snapshot
                ) {
                    synchronized.advance(accountId, snapshot)
                } else {
                    installed
                }
                dao.complete(accountId, sourceId, snapshot.document.id)
                dao.clearContent(accountId, sourceId)
                state
            }
        }

    private suspend fun read(accountId: String, sourceId: String): Publication? {
        val row = dao.find(accountId, sourceId) ?: return null
        val document = if (row.remoteId == null) {
            val chunks = dao.content(accountId, sourceId)
            check(chunks.isNotEmpty())
            codec.decode(chunks.joinToString("")).also { check(it.id == sourceId) }
        } else {
            null
        }
        return Publication(accountId, sourceId, row.name, row.operationId, document, row.remoteId)
    }
}
