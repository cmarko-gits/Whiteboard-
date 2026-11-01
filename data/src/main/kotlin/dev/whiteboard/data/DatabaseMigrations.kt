package dev.whiteboard.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {
    val FROM_3_TO_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE sync_boards ADD COLUMN name TEXT NOT NULL DEFAULT ''")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS publications (accountId TEXT NOT NULL, sourceId TEXT NOT NULL, name TEXT NOT NULL, operationId TEXT NOT NULL, remoteId TEXT, payloadBytes INTEGER NOT NULL, PRIMARY KEY(accountId, sourceId))"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_publications_accountId_operationId ON publications (accountId, operationId)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS publication_chunks (accountId TEXT NOT NULL, sourceId TEXT NOT NULL, part INTEGER NOT NULL, content TEXT NOT NULL, PRIMARY KEY(accountId, sourceId, part), FOREIGN KEY(accountId, sourceId) REFERENCES publications(accountId, sourceId) ON UPDATE NO ACTION ON DELETE CASCADE)"
            )
        }
    }

    val FROM_2_TO_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS sync_boards (accountId TEXT NOT NULL, boardId TEXT NOT NULL, revision INTEGER NOT NULL, role TEXT NOT NULL, localVersion INTEGER NOT NULL, conflictReason TEXT, conflictTarget TEXT, PRIMARY KEY(accountId, boardId))"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS sync_documents (accountId TEXT NOT NULL, boardId TEXT NOT NULL, kind TEXT NOT NULL, part INTEGER NOT NULL, content TEXT NOT NULL, PRIMARY KEY(accountId, boardId, kind, part), FOREIGN KEY(accountId, boardId) REFERENCES sync_boards(accountId, boardId) ON UPDATE NO ACTION ON DELETE CASCADE)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS sync_operations (accountId TEXT NOT NULL, operationId TEXT NOT NULL, boardId TEXT NOT NULL, ordinal INTEGER NOT NULL, baseRevision INTEGER NOT NULL, payloadBytes INTEGER NOT NULL, PRIMARY KEY(accountId, operationId), FOREIGN KEY(accountId, boardId) REFERENCES sync_boards(accountId, boardId) ON UPDATE NO ACTION ON DELETE CASCADE)"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_sync_operations_accountId_boardId_ordinal ON sync_operations (accountId, boardId, ordinal)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS sync_operation_chunks (accountId TEXT NOT NULL, operationId TEXT NOT NULL, part INTEGER NOT NULL, content TEXT NOT NULL, PRIMARY KEY(accountId, operationId, part), FOREIGN KEY(accountId, operationId) REFERENCES sync_operations(accountId, operationId) ON UPDATE NO ACTION ON DELETE CASCADE)"
            )
        }
    }

    val FROM_1_TO_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS folders (id TEXT NOT NULL, name TEXT NOT NULL, parentId TEXT, PRIMARY KEY(id))")
            for (table in listOf("document_chunks", "draft_chunks")) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS $table (boardId TEXT NOT NULL, part INTEGER NOT NULL, content TEXT NOT NULL, PRIMARY KEY(boardId, part), FOREIGN KEY(boardId) REFERENCES boards(id) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
            }
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS board_search (boardId TEXT NOT NULL, text TEXT NOT NULL, PRIMARY KEY(boardId), FOREIGN KEY(boardId) REFERENCES boards(id) ON UPDATE NO ACTION ON DELETE CASCADE)"
            )
            movePayloads(db, "boards", "id", "document_chunks")
            movePayloads(db, "drafts", "boardId", "draft_chunks")
            db.execSQL("UPDATE boards SET payload = ''")
            db.execSQL("DROP TABLE drafts")
            db.execSQL("INSERT INTO board_search(boardId, text) SELECT id, lower(name) FROM boards")
        }
    }
    private fun movePayloads(db: SupportSQLiteDatabase, source: String, idColumn: String, destination: String) {
        // SQL substr works on Unicode code points; payloads never cross a CursorWindow during migration.
        val lengths = mutableListOf<Pair<String, Int>>()
        db.query("SELECT $idColumn, length(payload) FROM $source").use { cursor ->
            while (cursor.moveToNext()) lengths.add(cursor.getString(0) to cursor.getInt(1))
        }
        lengths.forEach { (id, length) ->
            var offset = 0
            var part = 0
            while (offset < length) {
                db.execSQL(
                    "INSERT INTO $destination(boardId, part, content) SELECT $idColumn, ?, substr(payload, ?, ?) FROM $source WHERE $idColumn = ?",
                    arrayOf<Any>(part++, offset + 1, DOCUMENT_CHUNK_CHARACTERS, id)
                )
                offset += DOCUMENT_CHUNK_CHARACTERS
            }
        }
    }
}
