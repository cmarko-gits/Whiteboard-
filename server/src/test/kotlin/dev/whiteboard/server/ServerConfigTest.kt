package dev.whiteboard.server

import dev.whiteboard.server.database.update
import dev.whiteboard.server.http.RateLimiter
import org.junit.Assert.*
import org.junit.Test

class ServerConfigTest {
    @Test fun modifiedOrFutureMigrationHistoryIsRejected() {
        ServerFixture().use { f ->
            f.database.transaction { db ->
                db.update("UPDATE schema_migrations SET checksum = ? WHERE version = 1", "0".repeat(64))
            }
            assertTrue(runCatching { f.database.migrate() }.exceptionOrNull() is IllegalStateException)
        }
        ServerFixture().use { f ->
            f.database.transaction { db ->
                val next = db.createStatement().use { statement ->
                    statement.executeQuery("SELECT MAX(version) FROM schema_migrations").use { rows ->
                        rows.next()
                        rows.getInt(1) + 1
                    }
                }
                db.update("INSERT INTO schema_migrations (version, checksum) VALUES (?, ?)", next, "0".repeat(64))
            }
            assertTrue(runCatching { f.database.migrate() }.exceptionOrNull() is IllegalStateException)
        }
    }

    @Test fun developmentCannotAccidentallyExposeItsOutboxOrDatabaseOnAllInterfaces() {
        val development = ServerConfig.from(emptyMap())
        assertEquals("127.0.0.1", development.host)
        assertTrue(development.jdbcUrl.startsWith("jdbc:h2:"))
        assertNull(development.smtp)
        invalid { ServerConfig.from(mapOf("WHITEBOARD_HOST" to "0.0.0.0")) }
        invalid { ServerConfig.from(mapOf("WHITEBOARD_ENV" to "prodution")) }
        invalid { ServerConfig.from(mapOf("WHITEBOARD_ENV" to "production")) }
    }

    @Test fun productionRequiresPostgresVerifiedTlsHttpsAndSmtp() {
        val valid = mapOf(
            "WHITEBOARD_ENV" to "production",
            "WHITEBOARD_JDBC_URL" to "jdbc:postgresql://database.example.com/whiteboard?sslmode=verify-full",
            "WHITEBOARD_DB_USER" to "whiteboard",
            "WHITEBOARD_DB_PASSWORD" to "provided by deployment secret store",
            "WHITEBOARD_PUBLIC_ORIGIN" to "https://api.example.com",
            "WHITEBOARD_SMTP_HOST" to "smtp.example.com",
            "WHITEBOARD_SMTP_USER" to "whiteboard",
            "WHITEBOARD_SMTP_PASSWORD" to "provided by deployment secret store",
            "WHITEBOARD_MAIL_FROM" to "no-reply@example.com"
        )
        assertTrue(ServerConfig.from(valid).production)
        for (option in listOf("sslfactory", "%73slfactory", "sslFactory", "sslhostnameverifier", "socketfactory")) {
            invalid {
                ServerConfig.from(
                    valid + (
                        "WHITEBOARD_JDBC_URL" to
                            "jdbc:postgresql://database.example.com/whiteboard?sslmode=verify-full&$option=unsafe.custom.Factory"
                        )
                )
            }
        }
        invalid { ServerConfig.from(valid - "WHITEBOARD_SMTP_PASSWORD") }
        invalid { ServerConfig.from(valid + ("WHITEBOARD_JDBC_URL" to "jdbc:h2:mem:forbidden")) }
        invalid { ServerConfig.from(valid + ("WHITEBOARD_JDBC_URL" to "jdbc:postgresql://database.example.com/whiteboard")) }
        invalid {
            ServerConfig.from(
                valid + ("WHITEBOARD_JDBC_URL" to "jdbc:postgresql://database.example.com/whiteboard?sslmode=verify-full&sslmode=disable")
            )
        }
        invalid {
            ServerConfig.from(
                valid + ("WHITEBOARD_JDBC_URL" to "jdbc:postgresql://database.example.com/whiteboard?sslmode=verify-full&%73slmode=disable")
            )
        }
        invalid {
            ServerConfig.from(
                valid + ("WHITEBOARD_JDBC_URL" to "jdbc:postgresql://database.example.com/whiteboard?sslmode=verify-full&%75ser=hidden")
            )
        }
        invalid { ServerConfig.from(valid + ("WHITEBOARD_PUBLIC_ORIGIN" to "http://api.example.com")) }
        invalid { ServerConfig.from(valid + ("WHITEBOARD_PUBLIC_ORIGIN" to "https://api.example.com/?token=secret")) }
    }

    @Test fun rateLimitResetsAndCannotGrowPastBound() {
        val clock = TestClock()
        val limiter = RateLimiter(clock)
        repeat(3) { limiter.check("login", 3) }
        failure(429) { limiter.check("login", 3) }
        clock.advance(61)
        limiter.check("login", 3)
        repeat(4095) { limiter.check("address-$it", 1) }
        failure(429) { limiter.check("overflow", 1) }
        clock.advance(61)
        limiter.check("new-address", 1)
    }

    @Test fun migrationsAreRepeatableAndRollbackLeavesNoPartialWrite() {
        ServerFixture().use { f ->
            f.database.migrate()
            runCatching {
                f.database.transaction { db ->
                    db.createStatement().use {
                        it.executeUpdate(
                            "INSERT INTO accounts (id, email, password_hash, created_at) VALUES ('rollback', 'x@example.com', 'test', 1)"
                        )
                    }
                    error("Simulated interrupted write")
                }
            }.onSuccess { fail("Expected rollback") }
            f.database.transaction { db ->
                db.createStatement().use { statement ->
                    statement.executeQuery("SELECT COUNT(*) FROM accounts WHERE id = 'rollback'").use { rows ->
                        rows.next()
                        assertEquals(0, rows.getInt(1))
                    }
                }
            }
        }
    }

    private fun invalid(action: () -> Unit) {
        try {
            action()
            fail("Expected invalid configuration")
        } catch (_: IllegalArgumentException) { }
    }
}
