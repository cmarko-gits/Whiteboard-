package dev.whiteboard.server.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import dev.whiteboard.server.ServerConfig
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.util.HexFormat

class Database(config: ServerConfig) : AutoCloseable {
    private val pool = HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = config.jdbcUrl
            username = config.databaseUser
            password = config.databasePassword
            maximumPoolSize = 8
            minimumIdle = 1
            connectionTimeout = 5_000
            validationTimeout = 3_000
            poolName = "whiteboard-db"
        }
    )

    fun <T> transaction(block: (Connection) -> T): T = pool.connection.use { connection ->
        connection.autoCommit = false
        try {
            val value = block(connection)
            connection.commit()
            value
        } catch (error: Throwable) {
            runCatching { connection.rollback() }
            throw error
        }
    }

    fun migrate() = transaction { connection ->
        val postgres = connection.metaData.databaseProductName == "PostgreSQL"
        if (postgres) connection.query("SELECT pg_advisory_xact_lock(8129042601)") { it.next() }
        connection.update("CREATE TABLE IF NOT EXISTS schema_migrations (version INTEGER PRIMARY KEY, checksum VARCHAR(64) NOT NULL)")
        val files = requireNotNull(javaClass.getResource("/db/migrations.txt")).readText().lineSequence()
            .map(String::trim).filter(String::isNotEmpty).toList()
        val installed = connection.query("SELECT version, checksum FROM schema_migrations ORDER BY version") { rows ->
            buildMap { while (rows.next()) put(rows.getInt(1), rows.getString(2)) }
        }
        check(installed.keys.toList() == (1..installed.size).toList()) { "Migration history is incomplete" }
        check(installed.size <= files.size) { "Database is newer than this server; refusing downgrade" }
        files.forEachIndexed { index, file ->
            val version = index + 1
            check(file.matches(Regex("[0-9]{3}_[a-z0-9_]+\\.sql")) && file.take(3).toInt() == version)
            val source = requireNotNull(javaClass.getResource("/db/$file")).readText()
            val checksum = HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(source.toByteArray(Charsets.UTF_8))
            )
            val previous = installed[version]
            if (previous == null) {
                // Migration resources currently contain plain DDL, without procedural blocks or quoted semicolons.
                source.split(';').map(String::trim).filter(String::isNotEmpty).forEach { connection.update(it) }
                connection.update("INSERT INTO schema_migrations (version, checksum) VALUES (?, ?)", version, checksum)
            } else {
                check(previous == checksum) { "Migration checksum mismatch; create a new migration instead" }
            }
        }
    }

    override fun close() = pool.close()
}

fun Connection.update(sql: String, vararg values: Any?): Int = prepareStatement(sql).use {
    it.bind(values)
    it.executeUpdate()
}

fun <T> Connection.query(sql: String, vararg values: Any?, fetchSize: Int = 0, read: (ResultSet) -> T): T = prepareStatement(sql).use {
    it.bind(values)
    if (fetchSize > 0) it.fetchSize = fetchSize
    it.executeQuery().use(read)
}

private fun PreparedStatement.bind(values: Array<out Any?>) {
    values.forEachIndexed { index, value -> setObject(index + 1, value) }
}
