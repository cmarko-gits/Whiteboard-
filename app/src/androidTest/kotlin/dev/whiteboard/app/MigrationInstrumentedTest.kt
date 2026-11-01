package dev.whiteboard.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.whiteboard.core.model.*
import dev.whiteboard.data.*
import dev.whiteboard.format.DocumentCodec
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationInstrumentedTest {
    @Test fun legacyLargePayloadAndRecoveryMigrateWithoutCursorWindowOrUnicodeLoss() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-${newId()}.db"
        val path = context.getDatabasePath(name)
        path.parentFile?.mkdirs()
        val text = "🙂".repeat(550_000)
        val page = Page(elements = listOf(TextElement(bounds = Bounds(0f, 0f, 200f, 300f), text = text)))
        val board = Board(name = "Legacy large document", pages = listOf(page))
        val payload = DocumentCodec().encode(board).replace("\"version\":${DocumentCodec.VERSION}", "\"version\":1")
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            old.execSQL(
                "CREATE TABLE boards (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, updatedAt INTEGER NOT NULL, lastOpenedAt INTEGER NOT NULL, favorite INTEGER NOT NULL, pageCount INTEGER NOT NULL, objectCount INTEGER NOT NULL, thumbnail TEXT, trashedAt INTEGER, folderId TEXT, payload TEXT NOT NULL)"
            )
            old.execSQL(
                "CREATE TABLE drafts (boardId TEXT NOT NULL PRIMARY KEY, payload TEXT NOT NULL, FOREIGN KEY(boardId) REFERENCES boards(id) ON DELETE CASCADE)"
            )
            old.execSQL(
                "INSERT INTO boards VALUES (?, ?, ?, ?, 0, 1, 1, NULL, NULL, NULL, ?)",
                arrayOf<Any>(board.id, board.name, board.updatedAt, board.updatedAt, payload)
            )
            old.execSQL("INSERT INTO drafts VALUES (?, ?)", arrayOf<Any>(board.id, payload))
            old.version = 1
        }
        val migrated = Room.databaseBuilder(
            context,
            WhiteboardDatabase::class.java,
            name
        ).addMigrations(DatabaseMigrations.FROM_1_TO_2, DatabaseMigrations.FROM_2_TO_3, DatabaseMigrations.FROM_3_TO_4).build()
        try {
            val repository = LocalBoardRepository(migrated)
            assertEquals(board, repository.load(board.id))
            assertEquals(board, repository.draft(board.id))
            assertTrue(repository.search("legacy").contains(board.id))
            assertTrue(migrated.boards().document(board.id).size > 1)
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }
}
