package dev.whiteboard.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.whiteboard.core.model.*
import dev.whiteboard.data.LocalBoardRepository
import dev.whiteboard.data.WhiteboardDatabase
import dev.whiteboard.format.DocumentCodec
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JsonBudgetPersistenceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = Room.inMemoryDatabaseBuilder(context, WhiteboardDatabase::class.java).build()
    private val repository = LocalBoardRepository(database, DocumentCodec(allocationBudget = 16_384))
    private val note = Note(bounds = Bounds(0f, 0f, 200f, 200f), text = "Saved text")
    private val original = Board(name = "Keep saved revision", pages = listOf(Page(elements = listOf(note))))
    private val draft = original.withPage(original.activePage.copy(elements = listOf(note.copy(text = "Independent recovery text"))))
    private val oversized = original.copy(name = "Must not replace metadata").withPage(
        original.activePage.copy(elements = listOf(note.copy(text = "x".repeat(16_000))))
    )

    @After fun close() = database.close()

    private suspend fun limited(action: suspend () -> Unit) {
        try {
            action()
            fail("Expected a document resource limit before a committed write")
        } catch (failure: DocumentFailure) {
            assertEquals(DocumentError.DOCUMENT_LIMIT, failure.error)
        }
    }

    @Test fun failedSaveAndDraftReplacementKeepBothIndependentRevisions(): Unit = runBlocking {
        repository.save(original)
        repository.saveDraft(draft)
        limited { repository.save(oversized) }
        assertEquals(original, repository.load(original.id))
        assertEquals(draft, repository.draft(original.id))
        limited { repository.saveDraft(oversized) }
        assertEquals(original, repository.load(original.id))
        assertEquals(draft, repository.draft(original.id))
        assertEquals(listOf(original), repository.savedBoards())
    }

    @Test fun aRejectedNewDocumentCannotLeaveMetadataOrDraftChunksBehind(): Unit = runBlocking {
        repository.save(original)
        val rejected = oversized.copy(id = newId())
        limited { repository.save(rejected) }
        limited { repository.saveDraft(rejected) }
        assertNull(database.boards().board(rejected.id))
        assertNull(repository.draft(rejected.id))
        assertEquals(listOf(original), repository.savedBoards())
    }

    @Test fun productionBudgetPersistsTheLargeGraphWithRealisticUuidLengthIdentities(): Unit = runBlocking {
        val nodes = List(10_000) { index ->
            val x = (index % 100) * 160f
            val y = (index / 100) * 140f
            Shape(
                id = "00000000-0000-0000-0000-${index.toString().padStart(12, '0')}",
                bounds = Bounds(x, y, x + 100f, y + 80f),
                kind = Tool.RECTANGLE,
                style = InkStyle()
            )
        }
        val edges = List(nodes.size - 1) { index ->
            Connector(
                id = "10000000-0000-0000-0000-${index.toString().padStart(12, '0')}",
                start = ConnectorEnd.Bound(nodes[index].id, ConnectionPort.RIGHT),
                end = ConnectorEnd.Bound(nodes[index + 1].id, ConnectionPort.LEFT)
            )
        }
        val board = Board(name = "UUID graph", pages = listOf(Page(elements = nodes + edges)))
        val production = LocalBoardRepository(database)
        production.save(board)
        val loaded = production.load(board.id)
        assertEquals(board, loaded)
        assertEquals(board.activePage.geometry.resolve(edges.last()), loaded.activePage.geometry.resolve(edges.last()))
    }
}
