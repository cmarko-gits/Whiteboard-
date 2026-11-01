package dev.whiteboard.server.auth

import dev.whiteboard.server.ServiceFailure
import dev.whiteboard.server.database.AccountQuota
import dev.whiteboard.server.database.Database
import dev.whiteboard.server.database.query
import dev.whiteboard.server.database.update
import dev.whiteboard.server.invalidRequest
import dev.whiteboard.server.security.Passwords
import dev.whiteboard.server.security.Tokens
import dev.whiteboard.server.security.validatePassword
import dev.whiteboard.server.unauthenticated
import java.sql.Connection
import java.sql.SQLException
import java.time.Clock
import java.util.Locale
import java.util.UUID

data class Account(val id: String, val email: String)
data class SessionTokens(val accessToken: String, val refreshToken: String, val accessExpiresAt: Long)

class AuthService(
    private val database: Database,
    private val passwords: Passwords,
    private val mail: MailDelivery,
    private val clock: Clock = Clock.systemUTC()
) {
    private val dummyHash = passwords.hash(Tokens.create())

    fun register(rawEmail: String, password: String) {
        val email = normalizeEmail(rawEmail)
        validatePassword(password)
        val hash = passwords.hash(password)
        val id = UUID.randomUUID().toString()
        val token = Tokens.create()
        val created = try {
            database.transaction { db ->
                db.update("INSERT INTO accounts (id, email, password_hash, created_at) VALUES (?, ?, ?, ?)", id, email, hash, now())
                db.update("INSERT INTO account_quotas (account_id) VALUES (?)", id)
                insertAction(db, id, "verify", token, DAY)
            }
            true
        } catch (error: SQLException) {
            if (error.sqlState == "23505") false else throw error
        }
        if (created) mail.send(email, "verify", token)
    }

    fun verify(token: String) = database.transaction { db ->
        lockActionAccount(db, token)
        val id = consumeAction(db, token, "verify")
        db.update("UPDATE accounts SET verified = TRUE WHERE id = ?", id)
        db.update("DELETE FROM account_actions WHERE account_id = ? AND purpose = 'verify'", id)
        Unit
    }

    /** Generic response whether the address exists. A failed delivery can be retried through this endpoint. */
    fun requestAction(rawEmail: String, purpose: String) {
        require(purpose == "verify" || purpose == "reset")
        val email = normalizeEmail(rawEmail)
        val token = Tokens.create()
        val created = database.transaction { db ->
            val account = db.query("SELECT id, verified FROM accounts WHERE email = ? FOR UPDATE", email) {
                if (it.next()) it.getString(1) to it.getBoolean(2) else null
            } ?: return@transaction false
            if ((purpose == "verify") == account.second) return@transaction false
            db.update("DELETE FROM account_actions WHERE account_id = ? AND purpose = ?", account.first, purpose)
            insertAction(db, account.first, purpose, token, if (purpose == "reset") 30 * MINUTE else DAY)
            true
        }
        if (created) mail.send(email, purpose, token)
    }

    fun reset(token: String, password: String) {
        validatePassword(password)
        val hash = passwords.hash(password)
        database.transaction { db ->
            lockActionAccount(db, token)
            val id = consumeAction(db, token, "reset")
            db.update("UPDATE accounts SET password_hash = ? WHERE id = ?", hash, id)
            db.update("DELETE FROM account_actions WHERE account_id = ?", id)
            db.update("UPDATE sessions SET revoked = TRUE WHERE account_id = ?", id)
        }
    }

    fun login(rawEmail: String, password: String): SessionTokens {
        val email = normalizeEmail(rawEmail)
        val candidate = database.transaction { db ->
            db.query("SELECT id, password_hash FROM accounts WHERE email = ?", email) {
                if (it.next()) it.getString(1) to it.getString(2) else null
            }
        }
        val matches = passwords.verify(password, candidate?.second ?: dummyHash)
        if (!matches || candidate == null) unauthenticated()
        return database.transaction { db ->
            val valid = db.query("SELECT password_hash, verified FROM accounts WHERE id = ? FOR UPDATE", candidate.first) {
                it.next() && it.getBoolean(2) && it.getString(1) == candidate.second
            }
            if (!valid) unauthenticated()
            issue(db, candidate.first, UUID.randomUUID().toString(), now() + 30 * DAY)
        }
    }

    fun authenticate(token: String): Account = database.transaction { authenticate(it, token) }

    fun <T> withAccount(token: String, block: (Connection, Account) -> T): T = database.transaction { db ->
        val candidate = authenticate(db, token)
        lockAccount(db, candidate.id)
        block(db, authenticate(db, token))
    }

    fun authenticate(db: Connection, token: String): Account = db.query(
        """SELECT a.id, a.email FROM sessions s JOIN accounts a ON a.id = s.account_id
           WHERE s.access_hash = ? AND s.revoked = FALSE AND s.consumed = FALSE AND s.access_expires > ? AND a.verified = TRUE""",
        Tokens.hash(token),
        now()
    ) {
        if (it.next()) Account(it.getString(1), it.getString(2)) else unauthenticated()
    }

    fun refresh(token: String): SessionTokens {
        // Commit family revocation before returning an error for replay of an already consumed token.
        val result = database.transaction { db ->
            val accountId = db.query("SELECT account_id FROM sessions WHERE refresh_hash = ?", Tokens.hash(token)) {
                if (it.next()) it.getString(1) else null
            } ?: return@transaction null
            lockAccount(db, accountId)
            val session = db.query(
                "SELECT id, account_id, family_id, consumed, revoked, refresh_expires FROM sessions WHERE refresh_hash = ? FOR UPDATE",
                Tokens.hash(token)
            ) {
                if (it.next()) {
                    RefreshRow(it.getString(1), it.getString(2), it.getString(3), it.getBoolean(4), it.getBoolean(5), it.getLong(6))
                } else {
                    null
                }
            } ?: return@transaction null
            if (session.consumed) {
                db.update("UPDATE sessions SET revoked = TRUE WHERE family_id = ?", session.family)
                return@transaction null
            }
            if (session.revoked || session.expires <= now()) return@transaction null
            db.update("UPDATE sessions SET consumed = TRUE WHERE id = ?", session.id)
            issue(db, session.account, session.family, session.expires)
        }
        return result ?: unauthenticated()
    }

    fun logout(token: String) = withAccount(token) { db, account ->
        db.update(
            "UPDATE sessions SET revoked = TRUE WHERE family_id = (SELECT family_id FROM sessions WHERE access_hash = ?) AND account_id = ?",
            Tokens.hash(token),
            account.id
        )
        Unit
    }

    fun deleteAccount(token: String, password: String) {
        val account = authenticate(token)
        val hash = database.transaction { db ->
            db.query("SELECT password_hash FROM accounts WHERE id = ?", account.id) {
                if (it.next()) it.getString(1) else unauthenticated()
            }
        }
        if (!passwords.verify(password, hash)) unauthenticated()
        withAccount(token) { db, _ ->
            AccountQuota.lock(db, account.id)
            val unchanged = db.query("SELECT password_hash FROM accounts WHERE id = ? FOR UPDATE", account.id) {
                it.next() && it.getString(1) == hash
            }
            if (!unchanged) unauthenticated()
            db.update("DELETE FROM accounts WHERE id = ?", account.id)
        }
    }

    private fun issue(db: Connection, id: String, family: String, refreshExpiry: Long): SessionTokens {
        val access = Tokens.create()
        val refresh = Tokens.create()
        val expires = minOf(now() + 15 * MINUTE, refreshExpiry)
        db.update(
            """INSERT INTO sessions (id, account_id, family_id, access_hash, refresh_hash, access_expires, refresh_expires)
               VALUES (?, ?, ?, ?, ?, ?, ?)""",
            UUID.randomUUID().toString(),
            id,
            family,
            Tokens.hash(access),
            Tokens.hash(refresh),
            expires,
            refreshExpiry
        )
        return SessionTokens(access, refresh, expires)
    }

    private fun insertAction(db: Connection, id: String, purpose: String, token: String, ttl: Long) {
        db.update(
            "INSERT INTO account_actions (token_hash, account_id, purpose, expires_at) VALUES (?, ?, ?, ?)",
            Tokens.hash(token),
            id,
            purpose,
            now() + ttl
        )
    }

    private fun consumeAction(db: Connection, token: String, purpose: String): String {
        val hash = Tokens.hash(token)
        val id = db.query(
            "SELECT account_id FROM account_actions WHERE token_hash = ? AND purpose = ? AND expires_at > ? FOR UPDATE",
            hash,
            purpose,
            now()
        ) { if (it.next()) it.getString(1) else throw ServiceFailure(400, "invalid_or_expired_code") }
        db.update("DELETE FROM account_actions WHERE token_hash = ?", hash)
        return id
    }

    private fun lockActionAccount(db: Connection, token: String) {
        val id = db.query("SELECT account_id FROM account_actions WHERE token_hash = ?", Tokens.hash(token)) {
            if (it.next()) it.getString(1) else throw ServiceFailure(400, "invalid_or_expired_code")
        }
        lockAccount(db, id)
    }

    private fun lockAccount(db: Connection, id: String) {
        db.query("SELECT id FROM accounts WHERE id = ? FOR UPDATE", id) { if (!it.next()) unauthenticated() }
    }

    private fun now() = clock.millis()
    private data class RefreshRow(
        val id: String,
        val account: String,
        val family: String,
        val consumed: Boolean,
        val revoked: Boolean,
        val expires: Long
    )

    companion object {
        private const val MINUTE = 60_000L
        private const val DAY = 24 * 60 * MINUTE
    }
}

fun normalizeEmail(raw: String): String {
    val email = raw.trim().lowercase(Locale.ROOT)
    if (email.length > 254 || !email.matches(Regex("[a-z0-9.!#$%&'*+/=?^_`{|}~-]{1,64}@[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?\\.[a-z]{2,63}"))) {
        invalidRequest()
    }
    val local = email.substringBefore('@')
    val labels = email.substringAfter('@').split('.')
    if (local.startsWith('.') || local.endsWith('.') || local.contains("..") ||
        labels.any { !it.matches(Regex("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")) }
    ) {
        invalidRequest()
    }
    return email
}
