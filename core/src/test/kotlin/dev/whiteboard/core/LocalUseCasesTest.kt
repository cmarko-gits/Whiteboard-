package dev.whiteboard.core

import dev.whiteboard.core.concurrency.AppDispatchers
import dev.whiteboard.core.model.*
import dev.whiteboard.core.repository.*
import dev.whiteboard.core.testing.*
import dev.whiteboard.core.usecase.*
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocalUseCasesTest {
    private val folders = object : FolderRepository {
        override fun observeFolders() = flowOf(emptyList<Folder>())
        override suspend fun create(name: String, parentId: String?) = Unit
        override suspend fun rename(id: String, name: String) = Unit
        override suspend fun move(id: String, parentId: String?) = Unit
        override suspend fun deleteKeepingContents(id: String) = Unit
        override suspend fun assignBoard(boardId: String, folderId: String?) = Unit
    }
    private fun dispatchers(scope: TestScope) = object : AppDispatchers {
        override val io = StandardTestDispatcher(scope.testScheduler)
        override val computation = io
    }

    @Test fun transfersRejectRemoteAndArbitraryFilesystemUrisBeforeOpeningStorage() = runTest {
        val storage = FakeDocumentStorage()
        var opened = false
        storage.export = { opened = true }
        val transfers = DocumentTransfers(storage)
        for (uri in listOf(
            "https://example.invalid/document",
            "file:///private/document",
            "content:missing-authority",
            "",
            "content://a/" + "x".repeat(8192)
        )) {
            val failure = runCatching { transfers.exportPng(uri, Page()) }.exceptionOrNull()
            assertEquals(DocumentError.PERMISSION_DENIED, (failure as DocumentFailure).error)
        }
        assertFalse(opened)
        transfers.exportPng("content://fixture/document/1", Page())
        assertTrue(opened)
    }

    @Test fun transferCancellationPropagatesWithoutWrappingItAsAStorageFailure() = runTest {
        val storage = FakeDocumentStorage()
        val cancellation = CancellationException("fixture")
        storage.export = { throw cancellation }
        assertSame(cancellation, runCatching { DocumentTransfers(storage).backup("content://fixture/backup") }.exceptionOrNull())
    }

    @Test fun invalidBrushNameDoesNotWritePreferences() = runTest {
        val settings = InMemorySettingsRepository(UserSettings(brushes = emptyList()))
        val preferences = ManagePreferences(settings)
        assertTrue(runCatching { preferences.addBrush("  ", Tool.PEN, InkStyle()) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(settings.values.value.brushes.isEmpty())
        preferences.addBrush("  My pen  ", Tool.PEN, InkStyle())
        assertEquals("My pen", settings.values.value.brushes.single().name)
    }

    @Test fun aNewQueryCancelsTheOlderSearchAndNeverPublishesItsResult() = runTest {
        val base = InMemoryBoardRepository()
        val board = Board(name = "Fast result")
        base.save(board)
        var cancelled = false
        val repository = object : BoardRepository by base {
            override suspend fun search(query: String): Set<String> {
                if (query == "slow") {
                    try {
                        awaitCancellation()
                    } finally {
                        cancelled = true
                    }
                }
                return setOf(board.id)
            }
        }
        val query = MutableStateFlow("")
        val observed = mutableListOf<LibrarySnapshot>()
        val service = ManageLibrary(repository, folders, FakeDocumentStorage(), dispatchers(this))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { service.observe(query).collect(observed::add) }
        advanceTimeBy(250)
        runCurrent()
        query.value = "slow"
        advanceTimeBy(250)
        runCurrent()
        query.value = "fast"
        advanceTimeBy(250)
        runCurrent()
        assertTrue(cancelled)
        assertEquals("fast", observed.last().query)
        assertEquals(setOf(board.id), observed.last().matches)
        assertFalse(observed.any { it.query == "slow" })
    }

    @Test fun failedSearchDoesNotTerminateTheLibraryObservation() = runTest {
        val base = InMemoryBoardRepository()
        val repository = object : BoardRepository by base {
            override suspend fun search(query: String): Set<String> {
                if (query == "bad") throw IOException("fixture")
                return setOf("matching-board")
            }
        }
        val query = MutableStateFlow("bad")
        val observed = mutableListOf<LibrarySnapshot>()
        val service = ManageLibrary(repository, folders, FakeDocumentStorage(), dispatchers(this))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { service.observe(query).collect(observed::add) }
        advanceTimeBy(250)
        runCurrent()
        assertEquals(DocumentError.STORAGE_UNAVAILABLE, observed.last().error)
        query.value = "good"
        advanceTimeBy(250)
        runCurrent()
        assertNull(observed.last().error)
        assertEquals(setOf("matching-board"), observed.last().matches)
    }

    @Test fun completedDeletionIsNotMisreportedWhenOptionalCleanupFails() = runTest {
        val boards = InMemoryBoardRepository()
        val board = Board(name = "Delete fixture")
        boards.save(board)
        val storage = object : DocumentStorage by FakeDocumentStorage() {
            override suspend fun deletePreview(id: String): Unit = throw IOException("fixture")
        }
        ManageLibrary(boards, folders, storage).permanentlyDelete(board.id)
        assertFalse(boards.saved.containsKey(board.id))
    }

    @Test fun failedDeletionNeverStartsAttachmentCleanup() = runTest {
        val repository = object : BoardRepository by InMemoryBoardRepository() {
            override suspend fun deletePermanently(id: String): Unit = throw IOException("fixture")
        }
        var cleaned = false
        val storage = object : DocumentStorage by FakeDocumentStorage() {
            override suspend fun deletePreview(id: String) {
                cleaned = true
            }
        }
        assertTrue(runCatching { ManageLibrary(repository, folders, storage).permanentlyDelete("fixture") }.isFailure)
        assertFalse(cleaned)
    }
}
