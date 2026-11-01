package dev.whiteboard.server.database

import dev.whiteboard.server.ServiceFailure
import dev.whiteboard.server.notFound
import java.sql.Connection

/** Writers acquire this row before a board row, keeping an owner's aggregate budget atomic across editors/boards. */
object AccountQuota {
    const val PAYLOAD_BYTES = 64L * 1024 * 1024
    fun lock(db: Connection, accountId: String) {
        db.query("SELECT account_id FROM account_quotas WHERE account_id = ? FOR UPDATE", accountId) { if (!it.next()) notFound() }
    }
    fun checkAdditional(db: Connection, accountId: String, bytes: Long) {
        val used = db.query("SELECT COALESCE(SUM(payload_bytes + history_bytes), 0) FROM boards WHERE owner_id = ?", accountId) {
            it.next()
            it.getLong(1)
        }
        if (used + bytes > PAYLOAD_BYTES) throw ServiceFailure(409, "storage_quota_reached")
    }
}
