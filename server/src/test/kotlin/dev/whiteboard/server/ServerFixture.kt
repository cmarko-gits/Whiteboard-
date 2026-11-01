package dev.whiteboard.server

import dev.whiteboard.server.auth.AuthService
import dev.whiteboard.server.auth.MailDelivery
import dev.whiteboard.server.auth.SessionTokens
import dev.whiteboard.server.boards.BoardOperations
import dev.whiteboard.server.boards.BoardService
import dev.whiteboard.server.database.Database
import dev.whiteboard.server.security.ArgonPasswords
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.fail

internal class TestClock : Clock() {
    var time = Instant.parse("2026-10-20T12:00:00Z")
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
    override fun instant(): Instant = time
    fun advance(seconds: Long) {
        time = time.plusSeconds(seconds)
    }
}

internal class CapturedMail : MailDelivery {
    data class Item(val email: String, val purpose: String, val token: String)
    val items = mutableListOf<Item>()
    override fun send(email: String, purpose: String, token: String) {
        items += Item(email, purpose, token)
    }
    fun code(email: String, purpose: String = "verify") = items.last { it.email == email && it.purpose == purpose }.token
}

internal class ServerFixture(migrate: Boolean = true) : AutoCloseable {
    val clock = TestClock()
    val mail = CapturedMail()
    private val postgresUrl = PostgresTestDatabase.urlIfRequested()
    val config = ServerConfig.from(emptyMap()).copy(
        jdbcUrl = postgresUrl ?: "jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        databaseUser = if (postgresUrl == null) "sa" else "postgres"
    )
    val database = Database(config).also { if (migrate) it.migrate() }
    val auth = AuthService(database, ArgonPasswords(), mail, clock)
    val boards = BoardService(auth, clock)
    val operations = BoardOperations(auth, clock)
    fun account(email: String, password: String = PASSWORD): SessionTokens {
        auth.register(email, password)
        auth.verify(mail.code(email))
        return auth.login(email, password)
    }
    override fun close() = database.close()
    companion object {
        const val PASSWORD = "correct horse battery staple"
    }
}

internal fun failure(status: Int, code: String? = null, action: () -> Unit) {
    try {
        action()
        fail("Expected service failure $status")
    } catch (error: ServiceFailure) {
        assertEquals(status, error.status)
        if (code != null) assertEquals(code, error.code)
    }
}
