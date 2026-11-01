package dev.whiteboard.server.boards

import dev.whiteboard.core.cloud.DeltaConflict
import dev.whiteboard.core.cloud.DeltaEngine
import dev.whiteboard.core.cloud.DocumentDelta
import dev.whiteboard.format.DocumentDeltaCodec
import dev.whiteboard.format.ElementDigest
import dev.whiteboard.server.ServiceFailure
import dev.whiteboard.server.auth.AuthService
import dev.whiteboard.server.database.query
import dev.whiteboard.server.invalidRequest
import java.time.Clock

data class CommittedOperation(val operationId: String, val revision: Long, val actorId: String?, val delta: String, val createdAt: Long)
data class OperationPage(val operations: List<CommittedOperation>, val nextRevision: Long, val currentRevision: Long)

class BoardOperations(private val auth: AuthService, private val clock: Clock = Clock.systemUTC()) {
    private val engine = DeltaEngine(ElementDigest())
    private val codec = DocumentDeltaCodec()

    fun submit(token: String, boardId: String, operationId: String, baseRevision: Long, delta: DocumentDelta): Long {
        CloudDocuments.operationId(operationId)
        if (baseRevision < 0 || delta.empty) invalidRequest()
        val encoded = codec.encode(delta)
        if (CloudDocuments.bytes(encoded) > CloudDocuments.MAX_DOCUMENT_BYTES) throw ServiceFailure(413, "operation_too_large")
        val hash = CloudDocuments.fingerprint("delta", baseRevision, encoded)
        return auth.withAccount(token) { db, account ->
            BoardAccess.lockWritable(db, boardId, account.id)
            BoardChangeStore.receipt(db, boardId, operationId, account.id, hash)?.let { return@withAccount it }
            val current = BoardChangeStore.read(db, boardId)
            if (baseRevision > current.revision) throw ServiceFailure(409, "invalid_base_revision")
            val tombstones = BoardChangeStore.tombstones(db, boardId)
            val result = try {
                engine.apply(current.document, delta, tombstones)
            } catch (
                error: DeltaConflict
            ) {
                throw ServiceFailure(409, "conflict_${error.reason.name.lowercase()}")
            }
            BoardChangeStore.persist(db, current, tombstones, result, account.id, operationId, hash, encoded, clock.millis())
        }
    }

    /** A bounded, contiguous durable cursor. New members cannot fetch document versions predating their grant. */
    fun changes(token: String, boardId: String, after: Long): OperationPage = auth.withAccount(token) { db, account ->
        if (after < 0) invalidRequest()
        val role = BoardAccess.role(db, boardId, account.id)
        val current = db.query("SELECT revision FROM boards WHERE id = ?", boardId) {
            it.next()
            it.getLong(1)
        }
        if (after > current) throw ServiceFailure(409, "invalid_base_revision")
        if (role != BoardRole.OWNER) {
            val grant = db.query("SELECT granted_revision FROM board_members WHERE board_id = ? AND account_id = ?", boardId, account.id) {
                if (it.next()) it.getLong(1) else throw ServiceFailure(404, "not_found")
            }
            if (after < grant) throw ServiceFailure(409, "snapshot_required")
        }
        var next = after
        var bytes = 0L
        val operations = db.query(
            """SELECT operation_id, revision, account_id, payload, created_at FROM board_commits
               WHERE board_id = ? AND revision > ? AND revision <= ? ORDER BY revision LIMIT 100""",
            boardId,
            after,
            current,
            fetchSize = 1
        ) { rows ->
            buildList {
                while (rows.next()) {
                    if (rows.getLong(2) != next + 1) throw ServiceFailure(409, "snapshot_required")
                    val source = rows.getString(4) ?: throw ServiceFailure(409, "snapshot_required")
                    val length = CloudDocuments.bytes(source)
                    if (isNotEmpty() && bytes + length > CloudDocuments.MAX_DOCUMENT_BYTES) break
                    bytes += length
                    next = rows.getLong(2)
                    add(CommittedOperation(rows.getString(1), next, rows.getString(3), source, rows.getLong(5)))
                }
            }
        }
        if (operations.isEmpty() && after < current) throw ServiceFailure(409, "snapshot_required")
        OperationPage(operations, next, current)
    }
}
