package dev.whiteboard.server.boards

import dev.whiteboard.core.cloud.DeltaResult
import dev.whiteboard.core.cloud.DocumentTombstones
import dev.whiteboard.core.model.Board
import dev.whiteboard.server.ServiceFailure
import dev.whiteboard.server.database.AccountQuota
import dev.whiteboard.server.database.query
import dev.whiteboard.server.database.update
import dev.whiteboard.server.notFound
import java.sql.Connection

internal data class StoredBoard(
    val document: Board,
    val revision: Long,
    val ownerId: String,
    val payloadBytes: Long,
    val historyBytes: Long
)

/** Called only while the owner quota and board locks are held; writes document, receipt and deletion state atomically. */
internal object BoardChangeStore {
    fun read(db: Connection, boardId: String): StoredBoard = db.query(
        "SELECT payload, revision, owner_id, payload_bytes, history_bytes FROM boards WHERE id = ?",
        boardId
    ) {
        if (!it.next()) notFound()
        StoredBoard(CloudDocuments.codec.decode(it.getString(1)), it.getLong(2), it.getString(3), it.getLong(4), it.getLong(5))
    }

    fun receipt(db: Connection, boardId: String, operationId: String, accountId: String, hash: String): Long? = db.query(
        "SELECT account_id, request_hash, revision FROM board_commits WHERE board_id = ? AND operation_id = ?",
        boardId,
        operationId
    ) {
        if (!it.next()) {
            null
        } else {
            if (it.getString(1) != accountId || it.getString(2) != hash) throw ServiceFailure(409, "operation_id_reused")
            it.getLong(3)
        }
    }

    fun tombstones(db: Connection, boardId: String): DocumentTombstones = db.query(
        "SELECT kind, page_id, object_id FROM board_tombstones WHERE board_id = ?",
        boardId,
        fetchSize = 128
    ) { rows ->
        val pages = mutableSetOf<String>()
        val objects = mutableMapOf<String, MutableSet<String>>()
        var count = 0
        while (rows.next()) {
            check(++count <= MAX_TOMBSTONES) { "Tombstone bound exceeded" }
            if (rows.getString(1) == "PAGE") {
                pages += rows.getString(2)
            } else {
                objects.getOrPut(rows.getString(2)) { mutableSetOf() } += rows.getString(3)
            }
        }
        DocumentTombstones(pages, objects)
    }

    fun persist(
        db: Connection,
        current: StoredBoard,
        before: DocumentTombstones,
        result: DeltaResult,
        accountId: String,
        operationId: String,
        hash: String,
        delta: String?,
        now: Long
    ): Long {
        val boardId = current.document.id
        val document = CloudDocuments.shared(result.board, boardId).copy(createdAt = current.document.createdAt, updatedAt = now)
        val payload = CloudDocuments.payload(document)
        val bytes = CloudDocuments.bytes(payload)
        val historyBytes = delta?.let(CloudDocuments::bytes) ?: 0L
        val tombstoneCount = result.tombstones.pages.size + result.tombstones.objects.values.sumOf { it.size }
        if (tombstoneCount > MAX_TOMBSTONES) throw ServiceFailure(409, "tombstone_quota_reached")
        val count = db.query(
            "SELECT COUNT(*) FROM board_commits c JOIN boards b ON b.id = c.board_id WHERE b.owner_id = ?",
            current.ownerId
        ) {
            it.next()
            it.getInt(1)
        }
        if (count >= 100_000) throw ServiceFailure(409, "operation_quota_reached")
        val retired = db.query(
            "SELECT COUNT(*) FROM board_tombstones t JOIN boards b ON b.id = t.board_id WHERE b.owner_id = ?",
            current.ownerId
        ) {
            it.next()
            it.getInt(1)
        }
        val previousCount = before.pages.size + before.objects.values.sumOf { it.size }
        if (retired + tombstoneCount - previousCount > MAX_TOMBSTONES) throw ServiceFailure(409, "tombstone_quota_reached")
        AccountQuota.checkAdditional(db, current.ownerId, bytes - current.payloadBytes + historyBytes)
        val revision = Math.addExact(current.revision, 1)
        db.update(
            "UPDATE boards SET revision = ?, name = ?, payload = ?, payload_bytes = ?, history_bytes = ?, updated_at = ? WHERE id = ?",
            revision,
            document.name,
            payload,
            bytes,
            current.historyBytes + historyBytes,
            now,
            boardId
        )
        db.update(
            """INSERT INTO board_commits (board_id, operation_id, account_id, request_hash, revision, payload, created_at)
               VALUES (?, ?, ?, ?, ?, ?, ?)""",
            boardId,
            operationId,
            accountId,
            hash,
            revision,
            delta,
            now
        )
        val newlyDeletedPages = result.tombstones.pages - before.pages
        newlyDeletedPages.forEach {
            db.update("DELETE FROM board_tombstones WHERE board_id = ? AND kind = 'OBJECT' AND page_id = ?", boardId, it)
        }
        db.prepareStatement(
            "INSERT INTO board_tombstones (board_id, kind, page_id, object_id, revision, account_id, operation_id) VALUES (?, ?, ?, ?, ?, ?, ?)"
        ).use { statement ->
            var pending = 0
            fun add(kind: String, pageId: String, objectId: String) {
                statement.setString(1, boardId)
                statement.setString(2, kind)
                statement.setString(3, pageId)
                statement.setString(4, objectId)
                statement.setLong(5, revision)
                statement.setString(6, accountId)
                statement.setString(7, operationId)
                statement.addBatch()
                if (++pending == 256) {
                    statement.executeBatch()
                    statement.clearBatch()
                    pending = 0
                }
            }
            newlyDeletedPages.forEach { add("PAGE", it, "") }
            result.tombstones.objects.forEach { (page, ids) -> (ids - before.objects[page].orEmpty()).forEach { add("OBJECT", page, it) } }
            if (pending > 0) statement.executeBatch()
        }
        return revision
    }

    const val MAX_TOMBSTONES = 50_000
}
