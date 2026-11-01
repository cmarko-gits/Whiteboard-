package dev.whiteboard.server

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import java.util.UUID

/** The real database is test-only, binds loopback and uses a fresh schema for every fixture. No system service is installed. */
internal object PostgresTestDatabase {
    private val postgres by lazy {
        EmbeddedPostgres.builder().setPort(0).setServerConfig("listen_addresses", "127.0.0.1").start().also { database ->
            Runtime.getRuntime().addShutdownHook(Thread { database.close() })
        }
    }

    fun urlIfRequested(): String? {
        if (System.getProperty("whiteboard.test.postgres") != "true") return null
        val schema = "whiteboard_test_" + UUID.randomUUID().toString().replace("-", "")
        postgres.postgresDatabase.connection.use { connection ->
            connection.createStatement().use { it.executeUpdate("CREATE SCHEMA $schema") }
        }
        val url = postgres.getJdbcUrl("postgres", "postgres")
        return url + (if ('?' in url) "&" else "?") + "currentSchema=$schema"
    }
}
