package dev.whiteboard.app

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.whiteboard.core.model.*
import dev.whiteboard.data.DocumentChunk
import dev.whiteboard.data.LocalBoardRepository
import dev.whiteboard.data.WhiteboardDatabase
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MindMapPersistenceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = Room.inMemoryDatabaseBuilder(context, WhiteboardDatabase::class.java).build()
    private val repository = LocalBoardRepository(database)
    private val root = Note(id = "root", bounds = Bounds(0f, 0f, 100f, 80f), text = "Tema")
    private val child = root.copy(id = "child", bounds = Bounds(200f, 100f, 300f, 180f), text = "Dete Ћирилица")
    private val external = root.copy(id = "external", bounds = Bounds(400f, 0f, 500f, 80f), text = "Outside")
    private val managed =
        Connector(
            id = "managed",
            start = ConnectorEnd.Bound(root.id, ConnectionPort.RIGHT),
            end = ConnectorEnd.Bound(child.id, ConnectionPort.LEFT)
        )
    private val link = managed.copy(
        id = "external-edge",
        start = ConnectorEnd.Bound(child.id, ConnectionPort.RIGHT),
        end = ConnectorEnd.Bound(external.id, ConnectionPort.LEFT)
    )
    private val tree = MindMapTree("map", listOf(MindMapNode(root.id, collapsed = true), MindMapNode(child.id, root.id, managed.id)))
    private val page = Page(elements = listOf(root, managed, child, external, link), mindMaps = listOf(tree))
    private val board = Board(name = "Persist map", pages = listOf(page))
    private val draft = board.withPage(
        page.copy(
            elements = page.elements.map { if (it.id == child.id) child.copy(text = "Independent recovery") else it },
            mindMaps = listOf(tree.withCollapsed(root.id, false))
        )
    )

    @After fun close() = database.close()

    @Test fun savedAndDraftStatesKeepIndependentTopologyTextAndVisibilityAfterReopening(): Unit = runBlocking {
        repository.save(board)
        repository.saveDraft(draft)
        val loaded = repository.load(board.id)
        assertEquals(board, loaded)
        assertEquals(listOf(root, external), loaded.activePage.mindMapIndex?.visibleObjects)
        assertEquals(draft, repository.draft(board.id))
        assertEquals(5, repository.draft(board.id)?.activePage?.mindMapIndex?.visibleObjects?.size)
        val stored = JSONObject(database.boards().document(board.id).joinToString(""))
        assertEquals(6, stored.getInt("version"))
        val nodes = stored.getJSONArray("pages").getJSONObject(0).getJSONArray("mindMaps").getJSONObject(0).getJSONArray("nodes")
        assertEquals(2, nodes.length())
        assertTrue(nodes.getJSONObject(0).getBoolean("collapsed"))
    }

    @Test fun importingAnEditableMapCreatesAnIndependentBoardWithoutDiscardingHiddenContent(): Unit = runBlocking {
        repository.save(board)
        repository.importBoards(listOf(board))
        val imported = repository.savedBoards().single { it.id != board.id }
        assertEquals(board, repository.load(board.id))
        assertEquals(page.elements, imported.activePage.elements)
        assertEquals(page.mindMaps, imported.activePage.mindMaps)
        assertEquals(listOf(root, external), imported.activePage.mindMapIndex?.visibleObjects)
        assertNotEquals(board.id, imported.id)
        assertTrue(imported.name.endsWith(" (imported)"))
    }

    @Test fun malformedStoredTopologyCannotEraseMetadataOrAnIndependentValidRecoveryRevision(): Unit = runBlocking {
        repository.save(board)
        repository.saveDraft(draft)
        val stored = database.boards().document(board.id).joinToString("")
        val source = JSONObject(stored)
        source.getJSONArray("pages").getJSONObject(0).getJSONArray("mindMaps").getJSONObject(0)
            .getJSONArray("nodes").getJSONObject(1).put("parentId", "foreign")
        val malformed = listOf(source.toString(), stored.replace("\"version\":6", "\"version\":6.000000000000000000001"))
        for (corrupted in malformed) {
            database.withTransaction {
                database.boards().deleteDocument(board.id)
                database.boards().documentChunks(listOf(DocumentChunk(board.id, 0, corrupted)))
            }
            try {
                repository.load(board.id)
                fail("Malformed stored document must be rejected")
            } catch (failure: DocumentFailure) {
                assertEquals(DocumentError.INVALID_DOCUMENT, failure.error)
            }
            assertEquals(board.name, database.boards().board(board.id)?.name)
            assertEquals(corrupted, database.boards().document(board.id).joinToString(""))
            assertEquals(draft, repository.draft(board.id))
        }
        repository.save(board)
        database.withTransaction {
            database.boards().deleteDraft(board.id)
            database.boards().draftChunks(listOf(dev.whiteboard.data.DraftChunk(board.id, 0, malformed.first())))
        }
        try {
            repository.draft(board.id)
            fail("Malformed recovery must be rejected")
        } catch (failure: DocumentFailure) {
            assertEquals(DocumentError.INVALID_DOCUMENT, failure.error)
        }
        assertEquals(board, repository.load(board.id))
        try {
            repository.retainedAssetIds()
            fail("Malformed recovery must prevent asset pruning")
        } catch (failure: DocumentFailure) {
            assertEquals(DocumentError.INVALID_DOCUMENT, failure.error)
        }
    }

    @Test fun aFutureStoredVersionKeepsItsSpecificErrorAndDoesNotRewriteSavedOrDraftContent(): Unit = runBlocking {
        repository.save(board)
        repository.saveDraft(draft)
        val saved = database.boards().document(board.id).joinToString("")
        val recovery = database.boards().draft(board.id).joinToString("")
        val futureSaved = JSONObject(saved).put("version", 7).toString()
        val futureDraft = JSONObject(recovery).put("version", 7).toString()
        database.withTransaction {
            database.boards().deleteDocument(board.id)
            database.boards().documentChunks(listOf(DocumentChunk(board.id, 0, futureSaved)))
            database.boards().deleteDraft(board.id)
            database.boards().draftChunks(listOf(dev.whiteboard.data.DraftChunk(board.id, 0, futureDraft)))
        }
        for (read in listOf<suspend () -> Any?>({ repository.load(board.id) }, { repository.draft(board.id) })) {
            try {
                read()
                fail("A future document needs a specific upgrade error")
            } catch (future: UnsupportedDocumentVersion) {
                assertEquals(7, future.version)
            }
        }
        assertEquals(futureSaved, database.boards().document(board.id).joinToString(""))
        assertEquals(futureDraft, database.boards().draft(board.id).joinToString(""))
        assertEquals(board.name, database.boards().board(board.id)?.name)
    }
}
