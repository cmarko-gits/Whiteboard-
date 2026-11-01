package dev.whiteboard.data.cloud

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.whiteboard.core.cloud.*
import dev.whiteboard.core.model.*
import dev.whiteboard.data.DatabaseMigrations
import dev.whiteboard.data.WhiteboardDatabase
import dev.whiteboard.data.chunkDocument
import dev.whiteboard.format.DocumentCodec
import dev.whiteboard.format.DocumentDeltaCodec
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomPublicationStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "publication-${newId()}.db"
    private var database = open()
    private var store = RoomPublicationStore(database)
    private val extraDatabases = mutableListOf<String>()
    private fun open() = Room.databaseBuilder(context, WhiteboardDatabase::class.java, name).build()

    @After fun clean() {
        database.close()
        context.deleteDatabase(name)
        extraDatabases.forEach { context.deleteDatabase(it) }
    }

    @Test fun capturedRequestSurvivesReopenAndDoesNotChangeWhenPrivateOriginalChanges(): Unit = runBlocking {
        val original = Board(
            name = "Unicode publication",
            favorite = true,
            folderId = "private-folder",
            trashedAt = 123,
            pages = listOf(
                Page(
                    viewport = Viewport().pan(500f, 400f),
                    elements = listOf(
                        Note(bounds = Bounds(0f, 0f, 100f, 100f), text = "Ћ 🖊".repeat(100_000))
                    )
                )
            )
        )
        val staged = store.stage("alice", original)
        assertEquals(staged, store.stage("alice", original.copy(name = "Changed later")))
        val captured = checkNotNull(staged.document)
        assertFalse(captured.favorite)
        assertNull(captured.folderId)
        assertNull(captured.trashedAt)
        assertEquals(Viewport(), captured.activePage.viewport)
        assertEquals(original.activePage.elements, captured.activePage.elements)
        assertNotEquals(staged.operationId, store.stage("bob", original).operationId)
        assertTrue(database.publications().content("alice", original.id).size > 1)
        database.close()
        database = open()
        store = RoomPublicationStore(database)
        assertEquals(staged, store.find("alice", original.id))
        assertNull(store.find("unknown", original.id))
        assertEquals(listOf(PendingPublication(original.id, original.name)), store.observePending("alice").first())
    }

    @Test fun completionInstallsCacheAndRetainsMinimalDeduplicationReceipt(): Unit = runBlocking {
        val source = Board(name = "Source")
        val request = store.stage("alice", source)
        val remote = CloudBoardSnapshot(source.copy(id = newId()), 0, CloudBoardRole.OWNER)
        val result = store.complete("alice", source.id, request.operationId, remote)
        assertEquals(remote, result.shadow)
        val receipt = checkNotNull(store.find("alice", source.id))
        assertEquals(remote.document.id, receipt.remoteId)
        assertNull(receipt.document)
        assertTrue(database.publications().content("alice", source.id).isEmpty())
        assertTrue(store.observePending("alice").first().isEmpty())
        assertEquals(receipt, store.stage("alice", source.copy(name = "Later private change")))
        assertEquals(result, store.complete("alice", source.id, request.operationId, remote))
        val listing = RoomSyncStore(database).observeBoards("alice").first().single()
        assertEquals(remote.document.id, listing.id)
        assertEquals(source.name, listing.name)
        rejected(PublicationError.INVALID_RECEIPT) { store.complete("alice", source.id, newId(), remote) }
        rejected(PublicationError.INVALID_RECEIPT) {
            store.complete("alice", source.id, request.operationId, remote.copy(document = remote.document.copy(id = newId())))
        }
        val newer = remote.copy(document = remote.document.copy(name = "Newer cloud title"), revision = 1)
        val refreshed = store.complete("alice", source.id, request.operationId, newer)
        assertEquals(newer, refreshed.shadow)
        assertEquals(newer.document.name, refreshed.working.name)
    }

    @Test fun failedReceiptWriteRollsBackNewCacheAndKeepsCapturedRequest(): Unit = runBlocking {
        withTimeout(15_000) {
            val source = Board(name = "Atomic publication")
            val request = store.stage("alice", source)
            val remote = CloudBoardSnapshot(source.copy(id = newId()), 0, CloudBoardRole.OWNER)
            database.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER fail_publication BEFORE UPDATE ON publications BEGIN SELECT RAISE(ABORT, 'test fault'); END"
            )
            try {
                store.complete("alice", source.id, request.operationId, remote)
                fail("Expected injected write failure")
            } catch (_: SQLiteException) { }
            assertEquals(request, store.find("alice", source.id))
            assertNull(RoomSyncStore(database).load("alice", remote.document.id))
            database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_publication")
            assertEquals(remote, store.complete("alice", source.id, request.operationId, remote).shadow)
        }
    }

    @Test fun unsupportedMediaDoesNotStagePartialPublicationAndQuotaIsAccountScoped(): Unit = runBlocking {
        val media = MediaElement(bounds = Bounds(0f, 0f, 100f, 100f), assetId = "${newId()}.png")
        val source = Board(name = "With media", pages = listOf(Page(elements = listOf(media))))
        rejected(PublicationError.MEDIA_UNAVAILABLE) { store.stage("alice", source) }
        assertNull(store.find("alice", source.id))
        repeat(100) { store.stage("alice", Board(name = "Pending $it")) }
        rejected(PublicationError.QUEUE_FULL) { store.stage("alice", Board(name = "Over quota")) }
        assertNotNull(store.stage("bob", Board(name = "Another account")))
        assertEquals(100, store.observePending("alice").first().size)
    }

    private suspend fun rejected(expected: PublicationError, action: suspend () -> Unit) {
        try {
            action()
            fail("Expected $expected")
        } catch (error: PublicationFailure) {
            assertEquals(expected, error.error)
        }
    }

    @Test fun schemaThreeUpgradePreservesSavedShadowAndUnsentOperation(): Unit = runBlocking {
        val migrationName = "publication-upgrade-${newId()}.db"
        extraDatabases += migrationName
        val path = context.getDatabasePath(migrationName)
        path.parentFile?.mkdirs()
        val source = Board(name = "Before upgrade")
        val delta = DocumentDelta(name = ValueChange(source.name, "Unsent title"))
        val operationId = newId()
        val schema = InstrumentationRegistry.getInstrumentation().context.assets
            .open("dev.whiteboard.data.WhiteboardDatabase/3.json").bufferedReader().use {
                JSONObject(it.readText()).getJSONObject("database")
            }
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                val indices = entity.optJSONArray("indices") ?: continue
                for (at in 0 until indices.length()) {
                    old.execSQL(indices.getJSONObject(at).getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (index in 0 until setup.length()) old.execSQL(setup.getString(index))
            old.execSQL("INSERT INTO sync_boards VALUES ('alice', ?, 0, 'OWNER', 1, NULL, NULL)", arrayOf<Any>(source.id))
            for ((kind, document) in listOf("shadow" to source, "working" to source.copy(name = "Unsent title"))) {
                chunkDocument(DocumentCodec().encode(document)).forEachIndexed { part, content ->
                    old.execSQL("INSERT INTO sync_documents VALUES ('alice', ?, ?, ?, ?)", arrayOf<Any>(source.id, kind, part, content))
                }
            }
            val body = DocumentDeltaCodec().encode(delta)
            old.execSQL(
                "INSERT INTO sync_operations VALUES ('alice', ?, ?, 1, 0, ?)",
                arrayOf<Any>(operationId, source.id, body.toByteArray().size)
            )
            old.execSQL("INSERT INTO sync_operation_chunks VALUES ('alice', ?, 0, ?)", arrayOf<Any>(operationId, body))
            old.version = 3
        }
        val migrated = Room.databaseBuilder(context, WhiteboardDatabase::class.java, migrationName)
            .addMigrations(DatabaseMigrations.FROM_3_TO_4).build()
        try {
            val restored = checkNotNull(RoomSyncStore(migrated).load("alice", source.id))
            assertEquals(source, restored.shadow.document)
            assertEquals("Unsent title", restored.working.name)
            assertEquals(QueuedOperation(operationId, 0, delta), restored.pending.single())
            assertTrue(RoomPublicationStore(migrated).observePending("alice").first().isEmpty())
        } finally {
            migrated.close()
            context.deleteDatabase(migrationName)
        }
    }
}
