package dev.whiteboard.server.boards

import dev.whiteboard.server.ServiceFailure
import dev.whiteboard.server.database.AccountQuota
import dev.whiteboard.server.database.query
import dev.whiteboard.server.notFound
import java.sql.Connection

internal object BoardAccess {
    fun role(db: Connection, boardId: String, accountId: String): BoardRole = db.query(
        """SELECT b.owner_id, m.role FROM boards b LEFT JOIN board_members m ON m.board_id = b.id AND m.account_id = ? WHERE b.id = ?""",
        accountId,
        boardId
    ) {
        if (!it.next()) notFound()
        if (it.getString(1) == accountId) BoardRole.OWNER else it.getString(2)?.let(BoardRole::valueOf) ?: notFound()
    }
    fun owner(db: Connection, boardId: String): String = db.query("SELECT owner_id FROM boards WHERE id = ?", boardId) {
        if (it.next()) it.getString(1) else notFound()
    }
    fun requireOwner(db: Connection, boardId: String, accountId: String) {
        if (role(db, boardId, accountId) != BoardRole.OWNER) throw ServiceFailure(403, "owner_required")
    }
    fun lock(db: Connection, id: String) {
        db.query("SELECT id FROM boards WHERE id = ? FOR UPDATE", id) { if (!it.next()) notFound() }
    }
    fun lockWritable(db: Connection, boardId: String, accountId: String) {
        requireWritable(db, boardId, accountId)
        AccountQuota.lock(db, owner(db, boardId))
        lock(db, boardId)
        requireWritable(db, boardId, accountId)
    }
    private fun requireWritable(db: Connection, boardId: String, accountId: String) {
        if (role(db, boardId, accountId) !in setOf(BoardRole.OWNER, BoardRole.EDITOR)) throw ServiceFailure(403, "write_forbidden")
    }
}
