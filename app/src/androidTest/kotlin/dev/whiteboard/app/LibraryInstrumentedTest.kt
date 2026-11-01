package dev.whiteboard.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.whiteboard.core.model.*
import dev.whiteboard.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryInstrumentedTest {
    private val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WhiteboardDatabase::class.java).build()
    private val boards = LocalBoardRepository(database)
    private val folders = LocalFolderRepository(database)

    @After fun close() = database.close()

    @Test fun deletingFolderKeepsBoardsAndReparentsChildren() = runBlocking {
        folders.create("Work", null)
        val parent = folders.observeFolders().first().single()
        folders.create("Nested", parent.id)
        val child = folders.observeFolders().first().first { it.parentId == parent.id }
        val board = Board(name = "Keep", folderId = parent.id)
        boards.save(board)
        boards.saveDraft(board)
        folders.deleteKeepingContents(parent.id)
        assertNull(boards.load(board.id).folderId)
        assertNull(boards.draft(board.id)?.folderId)
        assertNull(folders.observeFolders().first().single { it.id == child.id }.parentId)
    }

    @Test fun folderMovesRejectCycles() = runBlocking {
        folders.create("Parent", null)
        val parent = folders.observeFolders().first().single()
        folders.create("Child", parent.id)
        val child = folders.observeFolders().first().first { it.parentId == parent.id }
        try {
            folders.move(parent.id, child.id)
            fail("A cycle should be rejected")
        } catch (_: IllegalArgumentException) { }
        assertNull(folders.observeFolders().first().first { it.id == parent.id }.parentId)
    }

    @Test fun contentSearchHandlesUnicodeDiacriticsAndLiteralWildcards() = runBlocking {
        val board = Board(
            name = "Notes",
            pages = listOf(
                Page(
                    name = "Sastanak",
                    elements = listOf(
                        Note(bounds = Bounds(0f, 0f, 200f, 200f), text = "Čas za đake. 50% plan_1. ЋИРИЛИЦА")
                    )
                )
            )
        )
        boards.save(board)
        assertEquals(setOf(board.id), boards.search("cas za djake"))
        assertEquals(setOf(board.id), boards.search("ћирилица"))
        assertEquals(setOf(board.id), boards.search("50%"))
        assertEquals(emptySet<String>(), boards.search("100%"))
        assertEquals(setOf(board.id), boards.search("plan_1"))
        assertEquals(setOf(board.id), boards.search("sastanak"))
    }
}
