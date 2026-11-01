package dev.whiteboard.server

import dev.whiteboard.core.cloud.DeltaEngine
import dev.whiteboard.core.model.*
import dev.whiteboard.format.DocumentCodec
import dev.whiteboard.format.ElementDigest
import dev.whiteboard.server.database.query
import dev.whiteboard.server.database.update
import dev.whiteboard.server.security.ArgonPasswords
import java.security.MessageDigest
import java.util.HexFormat
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class DatabaseMigrationTest {
    @Test fun schemaOneUpgradePreservesUnicodePayloadOwnershipAndOriginalReceipt() {
        ServerFixture(migrate = false).use { f ->
            val initialSql = requireNotNull(javaClass.getResource("/db/001_initial.sql")).readText()
            val checksum = digest(initialSql)
            val accountId = UUID.randomUUID().toString()
            val operationId = UUID.randomUUID().toString()
            val note = Note(bounds = Bounds(0f, 0f, 100f, 100f), text = "Stari zapis Ћирилица 🖊")
            val source = Board(name = "Legacy updated Ž", pages = listOf(Page(elements = listOf(note))))
            val codec = DocumentCodec()
            val encoded = codec.encode(source)
            val originalReceipt = digest("0\n$encoded")
            val password = ArgonPasswords().hash(ServerFixture.PASSWORD)
            f.database.transaction { db ->
                db.update("CREATE TABLE schema_migrations (version INTEGER PRIMARY KEY, checksum VARCHAR(64) NOT NULL)")
                initialSql.split(';').map(String::trim).filter(String::isNotEmpty).forEach { db.update(it) }
                db.update("INSERT INTO schema_migrations (version, checksum) VALUES (1, ?)", checksum)
                db.update(
                    "INSERT INTO accounts (id, email, password_hash, verified, created_at) VALUES (?, ?, ?, TRUE, ?)",
                    accountId,
                    "alice@example.com",
                    password,
                    f.clock.millis()
                )
                db.update(
                    "INSERT INTO boards (id, owner_id, revision, name, payload, updated_at) VALUES (?, ?, 1, ?, ?, ?)",
                    source.id,
                    accountId,
                    source.name,
                    encoded,
                    f.clock.millis()
                )
                db.update(
                    "INSERT INTO board_commits (board_id, operation_id, account_id, request_hash, revision) VALUES (?, ?, ?, ?, 1)",
                    source.id,
                    operationId,
                    accountId,
                    originalReceipt
                )
            }
            f.database.migrate()
            f.database.migrate()
            val session = f.auth.login("alice@example.com", ServerFixture.PASSWORD)
            assertEquals(source, f.boards.get(session.accessToken, source.id).document)
            assertEquals(1L, f.boards.commit(session.accessToken, source.id, operationId, 0, source))
            f.database.transaction { db ->
                db.query("SELECT payload_bytes FROM boards WHERE id = ?", source.id) {
                    it.next()
                    assertEquals(encoded.toByteArray(Charsets.UTF_8).size.toLong(), it.getLong(1))
                }
            }
            failure(409, "snapshot_required") { f.operations.changes(session.accessToken, source.id, 0) }
            val updated = source.copy(name = "After migration")
            assertEquals(
                2L,
                f.operations.submit(
                    session.accessToken,
                    source.id,
                    UUID.randomUUID().toString(),
                    1,
                    DeltaEngine(ElementDigest()).between(source, updated)
                )
            )
            assertEquals(listOf(2L), f.operations.changes(session.accessToken, source.id, 1).operations.map { it.revision })
        }
    }
    private fun digest(value: String) =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)))
}
