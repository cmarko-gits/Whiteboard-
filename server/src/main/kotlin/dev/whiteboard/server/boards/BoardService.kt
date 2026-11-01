package dev.whiteboard.server.boards

import dev.whiteboard.core.cloud.DeltaConflict
import dev.whiteboard.core.cloud.DeltaEngine
import dev.whiteboard.core.model.Board
import dev.whiteboard.format.ElementDigest
import dev.whiteboard.server.ServiceFailure
import dev.whiteboard.server.auth.AuthService
import dev.whiteboard.server.auth.normalizeEmail
import dev.whiteboard.server.boards.BoardAccess.lock as lockBoard
import dev.whiteboard.server.boards.BoardAccess.requireOwner
import dev.whiteboard.server.boards.BoardAccess.role
import dev.whiteboard.server.boards.CloudDocuments.payload
import dev.whiteboard.server.boards.CloudDocuments.shared
import dev.whiteboard.server.database.AccountQuota
import dev.whiteboard.server.database.query
import dev.whiteboard.server.database.update
import dev.whiteboard.server.invalidRequest
import dev.whiteboard.server.notFound
import dev.whiteboard.server.security.Tokens
import java.sql.Connection
import java.time.Clock
import java.util.UUID

enum class BoardRole { OWNER, EDITOR, COMMENTER, VIEWER }
data class CloudBoard(val document: Board, val revision: Long, val role: BoardRole)
data class CloudBoardSummary(val id: String, val name: String, val revision: Long, val role: BoardRole)

class BoardService(private val auth: AuthService, private val clock: Clock = Clock.systemUTC()) {
    private val codec = CloudDocuments.codec
    private val engine = DeltaEngine(ElementDigest())

    fun create(token: String, document: Board, operationId: String = UUID.randomUUID().toString()): CloudBoard {
        CloudDocuments.operationId(operationId)
        val hash = CloudDocuments.fingerprint("create", 0, payload(shared(document, document.id)))
        return auth.withAccount(token) { db, account ->
            AccountQuota.lock(db, account.id)
            val previous = db.query(
                "SELECT board_id, request_hash FROM board_creations WHERE account_id = ? AND operation_id = ?",
                account.id,
                operationId
            ) {
                if (!it.next()) {
                    null
                } else {
                    if (it.getString(2) != hash) throw ServiceFailure(409, "operation_id_reused")
                    it.getString(1) ?: throw ServiceFailure(409, "creation_was_deleted")
                }
            }
            if (previous != null) {
                val stored = BoardChangeStore.read(db, previous)
                return@withAccount CloudBoard(stored.document, stored.revision, BoardRole.OWNER)
            }
            val creations = db.query("SELECT COUNT(*) FROM board_creations WHERE account_id = ?", account.id) {
                it.next()
                it.getInt(1)
            }
            if (creations >= 10_000) throw ServiceFailure(409, "creation_quota_reached")
            val count = db.query("SELECT COUNT(*) FROM boards WHERE owner_id = ?", account.id) {
                it.next()
                it.getInt(1)
            }
            if (count >= 200) throw ServiceFailure(409, "board_quota_reached")
            val board = shared(document, UUID.randomUUID().toString()).copy(createdAt = clock.millis(), updatedAt = clock.millis())
            val payload = payload(board)
            val bytes = CloudDocuments.bytes(payload)
            AccountQuota.checkAdditional(db, account.id, bytes)
            db.update(
                "INSERT INTO boards (id, owner_id, revision, name, payload, payload_bytes, updated_at) VALUES (?, ?, 0, ?, ?, ?, ?)",
                board.id,
                account.id,
                board.name,
                payload,
                bytes,
                clock.millis()
            )
            db.update(
                "INSERT INTO board_creations (account_id, operation_id, board_id, request_hash) VALUES (?, ?, ?, ?)",
                account.id,
                operationId,
                board.id,
                hash
            )
            CloudBoard(board, 0, BoardRole.OWNER)
        }
    }

    fun list(token: String, after: String = ""): List<CloudBoardSummary> = auth.withAccount(token) { db, account ->
        if (after.length > 36) invalidRequest()
        db.query(
            """SELECT b.id, b.name, b.revision, b.owner_id, m.role FROM boards b
               LEFT JOIN board_members m ON m.board_id = b.id AND m.account_id = ?
               WHERE (b.owner_id = ? OR m.account_id = ?) AND b.id > ? ORDER BY b.id LIMIT 100""",
            account.id,
            account.id,
            account.id,
            after
        ) { rows ->
            buildList {
                while (rows.next()) {
                    add(
                        CloudBoardSummary(
                            rows.getString(1),
                            rows.getString(2),
                            rows.getLong(3),
                            if (rows.getString(4) == account.id) BoardRole.OWNER else BoardRole.valueOf(rows.getString(5))
                        )
                    )
                }
            }
        }
    }

    fun get(token: String, boardId: String): CloudBoard = auth.withAccount(token) { db, account ->
        val role = role(db, boardId, account.id)
        db.query("SELECT payload, revision FROM boards WHERE id = ?", boardId) {
            if (!it.next()) notFound()
            CloudBoard(codec.decode(it.getString(1)), it.getLong(2), role)
        }
    }

    /** A stale complete snapshot is rejected. Object-level rebasing is a separate sync protocol. */
    fun commit(token: String, boardId: String, operationId: String, expectedRevision: Long, document: Board): Long {
        CloudDocuments.operationId(operationId)
        if (expectedRevision < 0) invalidRequest()
        val board = shared(document, boardId)
        val encoded = payload(board)
        val fingerprint = CloudDocuments.fingerprint("snapshot", expectedRevision, encoded)
        return auth.withAccount(token) { db, account ->
            BoardAccess.lockWritable(db, boardId, account.id)
            val previous = BoardChangeStore.receipt(db, boardId, operationId, account.id, fingerprint)
            if (previous != null) return@withAccount previous
            val current = BoardChangeStore.read(db, boardId)
            if (current.revision != expectedRevision) throw ServiceFailure(409, "revision_conflict")
            val retired = BoardChangeStore.tombstones(db, boardId)
            val result = try {
                engine.apply(current.document, engine.between(current.document, board), retired)
            } catch (
                error: DeltaConflict
            ) {
                throw ServiceFailure(409, "conflict_${error.reason.name.lowercase()}")
            }
            BoardChangeStore.persist(db, current, retired, result, account.id, operationId, fingerprint, null, clock.millis())
        }
    }

    fun invite(token: String, boardId: String, email: String, role: BoardRole): String {
        if (role == BoardRole.OWNER) invalidRequest()
        val recipient = normalizeEmail(email)
        return auth.withAccount(token) { db, account ->
            lockBoard(db, boardId)
            requireOwner(db, boardId, account.id)
            val count = db.query("SELECT COUNT(*) FROM board_invites WHERE board_id = ? AND expires_at > ?", boardId, clock.millis()) {
                it.next()
                it.getInt(1)
            }
            if (count >= 100) throw ServiceFailure(409, "invite_quota_reached")
            val invitation = Tokens.create()
            db.update(
                "INSERT INTO board_invites (token_hash, board_id, email, role, expires_at) VALUES (?, ?, ?, ?, ?)",
                Tokens.hash(invitation),
                boardId,
                recipient,
                role.name,
                clock.millis() + 7 * 86_400_000L
            )
            invitation
        }
    }

    fun accept(token: String, invitation: String): String = auth.withAccount(token) { db, account ->
        val hash = Tokens.hash(invitation)
        val boardId = db.query("SELECT board_id FROM board_invites WHERE token_hash = ?", hash) {
            if (it.next()) it.getString(1) else notFound()
        }
        lockBoard(db, boardId)
        val role = db.query("SELECT email, role, expires_at FROM board_invites WHERE token_hash = ? FOR UPDATE", hash) {
            if (!it.next() || it.getString(1) != account.email || it.getLong(3) <= clock.millis()) notFound()
            BoardRole.valueOf(it.getString(2))
        }
        val owner = db.query("SELECT owner_id FROM boards WHERE id = ?", boardId) {
            it.next()
            it.getString(1)
        }
        if (owner == account.id) throw ServiceFailure(409, "already_owner")
        val count = db.query("SELECT COUNT(*) FROM board_members WHERE board_id = ?", boardId) {
            it.next()
            it.getInt(1)
        }
        if (count >= 50) throw ServiceFailure(409, "member_quota_reached")
        // A later invite never silently promotes an existing member. Role changes require an owner action.
        val exists = db.query("SELECT role FROM board_members WHERE board_id = ? AND account_id = ?", boardId, account.id) { it.next() }
        val granted = db.query("SELECT revision FROM boards WHERE id = ?", boardId) {
            it.next()
            it.getLong(1)
        }
        if (!exists) {
            db.update(
                "INSERT INTO board_members (board_id, account_id, role, granted_revision) VALUES (?, ?, ?, ?)",
                boardId,
                account.id,
                role.name,
                granted
            )
        }
        db.update("DELETE FROM board_invites WHERE token_hash = ?", hash)
        boardId
    }

    fun changeMember(token: String, boardId: String, memberId: String, role: BoardRole?) = auth.withAccount(token) { db, account ->
        lockBoard(db, boardId)
        requireOwner(db, boardId, account.id)
        if (role == BoardRole.OWNER || memberId == account.id) invalidRequest()
        if (role == null) {
            // Revoke pending invitations too, so an old invite cannot restore this user's access.
            db.update(
                "DELETE FROM board_invites WHERE board_id = ? AND email = (SELECT email FROM accounts WHERE id = ?)",
                boardId,
                memberId
            )
            db.update("DELETE FROM board_members WHERE board_id = ? AND account_id = ?", boardId, memberId)
        } else {
            val updated = db.update("UPDATE board_members SET role = ? WHERE board_id = ? AND account_id = ?", role.name, boardId, memberId)
            if (updated == 0) notFound()
        }
        Unit
    }

    fun revokeInvites(token: String, boardId: String) = auth.withAccount(token) { db, account ->
        lockBoard(db, boardId)
        requireOwner(db, boardId, account.id)
        db.update("DELETE FROM board_invites WHERE board_id = ?", boardId)
        Unit
    }

    fun delete(token: String, boardId: String) = auth.withAccount(token) { db, account ->
        requireOwner(db, boardId, account.id)
        AccountQuota.lock(db, account.id)
        lockBoard(db, boardId)
        requireOwner(db, boardId, account.id)
        db.update("DELETE FROM boards WHERE id = ?", boardId)
        Unit
    }
}
