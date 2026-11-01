package dev.whiteboard.data.cloud

import androidx.room.withTransaction
import dev.whiteboard.core.cloud.*
import dev.whiteboard.core.model.*
import dev.whiteboard.data.WhiteboardDatabase
import dev.whiteboard.data.chunkDocument
import dev.whiteboard.format.DocumentCodec
import dev.whiteboard.format.DocumentDeltaCodec
import dev.whiteboard.format.ElementDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Shared copies live separately from private boards. No network call runs inside a database transaction. */
class RoomSyncStore(private val database: WhiteboardDatabase) : SyncStore {
    private val dao = database.sync()
    private val documents = DocumentCodec()
    private val deltas = DocumentDeltaCodec()
    private val engine = DeltaEngine(ElementDigest())

    override fun observeBoards(accountId: String) = dao.observeBoards(accountId).map { rows ->
        rows.map {
            CachedCloudBoard(it.boardId, it.name, it.revision, CloudBoardRole.valueOf(it.role), it.pending, it.conflictReason != null)
        }
    }

    override suspend fun install(accountId: String, snapshot: CloudBoardSnapshot): SyncedDocument = withContext(Dispatchers.IO) {
        require(accountId.isNotBlank() && snapshot.revision >= 0)
        validateDocument(snapshot.document)
        database.withTransaction {
            read(accountId, snapshot.document.id) ?: run {
                val initial = SyncedDocument(accountId, snapshot, snapshot.document, 0, emptyList())
                dao.insert(entity(initial))
                putDocument(initial, "shadow", snapshot.document)
                putDocument(initial, "working", snapshot.document)
                initial
            }
        }
    }

    override suspend fun load(accountId: String, boardId: String): SyncedDocument? = withContext(Dispatchers.IO) {
        database.withTransaction { read(accountId, boardId) }
    }

    override suspend fun edit(accountId: String, board: Board, expectedLocalVersion: Long, operationId: String): SyncedDocument =
        withContext(Dispatchers.IO) {
            require(operationId.matches(Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")))
            validateDocument(board)
            database.withTransaction {
                val current = read(accountId, board.id) ?: failure(SyncError.MISSING)
                if (current.localVersion != expectedLocalVersion) failure(SyncError.STALE_EDIT)
                if (current.conflict != null) failure(SyncError.CONFLICT)
                if (current.shadow.role !in setOf(CloudBoardRole.OWNER, CloudBoardRole.EDITOR)) failure(SyncError.READ_ONLY)
                val delta = engine.between(current.working, board)
                val nextVersion = increment(current.localVersion)
                val operation = if (delta.empty) null else QueuedOperation(operationId, current.shadow.revision, delta)
                if (operation != null) {
                    val source = deltas.encode(delta)
                    val bytes = source.toByteArray(Charsets.UTF_8).size.toLong()
                    val requestBytes = CloudPayloads.bytes(CloudPayloads.operation(operationId, operation.baseRevision, source))
                    if (requestBytes > CloudPayloads.MAX_REQUEST_BYTES || current.pending.size >= 1000 ||
                        dao.queuedBytes(accountId) + bytes > MAX_QUEUE_BYTES
                    ) {
                        failure(SyncError.QUEUE_FULL)
                    }
                    dao.operation(SyncOperationEntity(accountId, operationId, board.id, nextVersion, operation.baseRevision, bytes))
                    dao.operationChunks(
                        chunkDocument(source).mapIndexed { part, content ->
                            SyncOperationChunk(accountId, operationId, part, content)
                        }
                    )
                }
                val next = current.copy(
                    working = board,
                    localVersion = nextVersion,
                    pending = current.pending + listOfNotNull(operation)
                )
                putDocument(next, "working", board)
                dao.update(entity(next))
                next
            }
        }

    override suspend fun advance(
        accountId: String,
        snapshot: CloudBoardSnapshot,
        acknowledgment: OperationAcknowledgment?
    ): SyncedDocument = withContext(Dispatchers.IO) {
        require(snapshot.revision >= 0)
        validateDocument(snapshot.document)
        database.withTransaction {
            val current = read(accountId, snapshot.document.id) ?: failure(SyncError.MISSING)
            if (snapshot.revision < current.shadow.revision) failure(SyncError.STALE_SNAPSHOT)
            if (snapshot.revision == current.shadow.revision && !engine.between(current.shadow.document, snapshot.document).empty) {
                failure(SyncError.STALE_SNAPSHOT)
            }
            val pending = current.pending.toMutableList()
            if (acknowledgment != null) {
                val head = pending.firstOrNull() ?: failure(SyncError.INVALID_ACKNOWLEDGMENT)
                if (head.id != acknowledgment.id || acknowledgment.revision <= head.baseRevision ||
                    acknowledgment.revision > snapshot.revision
                ) {
                    failure(SyncError.INVALID_ACKNOWLEDGMENT)
                }
                dao.acknowledge(accountId, head.id)
                pending.removeAt(0)
            }
            var rebased = snapshot.document
            val conflict = try {
                pending.forEach { rebased = engine.apply(rebased, it.delta).board }
                null
            } catch (error: DeltaConflict) {
                SyncConflict(error.reason, error.targetId)
            }
            // Keep every visible local edit on conflict. The authoritative shadow can still advance independently.
            val working = if (conflict != null) current.working else personalState(rebased, current.working)
            val next = current.copy(
                shadow = snapshot,
                working = working,
                localVersion = increment(current.localVersion),
                pending = pending,
                conflict = conflict
            )
            putDocument(next, "shadow", snapshot.document)
            putDocument(next, "working", working)
            dao.update(entity(next))
            next
        }
    }

    private suspend fun read(accountId: String, boardId: String): SyncedDocument? {
        val row = dao.board(accountId, boardId) ?: return null
        fun decode(chunks: List<String>): Board {
            check(chunks.isNotEmpty())
            return documents.decode(chunks.joinToString("")).also { check(it.id == boardId) }
        }
        val shadow = decode(dao.document(accountId, boardId, "shadow"))
        val working = decode(dao.document(accountId, boardId, "working"))
        val pending = dao.operations(accountId, boardId).map {
            val chunks = dao.operationBody(accountId, it.operationId)
            check(chunks.isNotEmpty())
            QueuedOperation(it.operationId, it.baseRevision, deltas.decode(chunks.joinToString("")))
        }
        return SyncedDocument(
            accountId,
            CloudBoardSnapshot(shadow, row.revision, CloudBoardRole.valueOf(row.role)),
            working,
            row.localVersion,
            pending,
            row.conflictReason?.let { SyncConflict(DeltaConflictReason.valueOf(it), checkNotNull(row.conflictTarget)) }
        )
    }

    private suspend fun putDocument(state: SyncedDocument, kind: String, board: Board) {
        val chunks = chunkDocument(documents.encode(board))
        dao.clearDocument(state.accountId, board.id, kind)
        dao.documents(chunks.mapIndexed { part, content -> SyncDocumentChunk(state.accountId, board.id, kind, part, content) })
    }
    private fun entity(state: SyncedDocument) = SyncBoardEntity(
        state.accountId,
        state.working.id,
        state.shadow.revision,
        state.shadow.role.name,
        state.localVersion,
        state.conflict?.reason?.name,
        state.conflict?.targetId,
        state.working.name
    )
    private fun personalState(shared: Board, previous: Board): Board {
        val viewports = previous.pages.associate { it.id to it.viewport }
        return shared.copy(
            activePageId = previous.activePageId.takeIf { id -> shared.pages.any { it.id == id } } ?: shared.activePageId,
            pages = shared.pages.map { it.copy(viewport = viewports[it.id] ?: Viewport()) },
            favorite = previous.favorite,
            folderId = previous.folderId,
            trashedAt = previous.trashedAt
        )
    }
    private fun validateDocument(board: Board) {
        // Asset upload/download is a separate dependency and must finish before a shared copy references media.
        require(board.referencedAssets().isEmpty())
        require(documents.encode(board).toByteArray(Charsets.UTF_8).size <= MAX_DOCUMENT_BYTES)
    }
    private fun increment(value: Long): Long {
        check(value < Long.MAX_VALUE)
        return value + 1
    }
    private fun failure(error: SyncError): Nothing = throw SyncFailure(error)

    companion object {
        private const val MAX_DOCUMENT_BYTES = 8 * 1024 * 1024
        private const val MAX_QUEUE_BYTES = 32L * 1024 * 1024
    }
}
