package dev.whiteboard.app.workspace

import androidx.lifecycle.viewModelScope
import dev.whiteboard.core.concurrency.AppDispatchers
import dev.whiteboard.core.model.*
import dev.whiteboard.core.repository.*
import dev.whiteboard.core.testing.*
import dev.whiteboard.core.usecase.ManageLibrary
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val models = mutableListOf<LibraryViewModel>()
    private val folders = object : FolderRepository {
        override fun observeFolders() = flowOf(emptyList<Folder>())
        override suspend fun create(name: String, parentId: String?) = Unit
        override suspend fun rename(id: String, name: String) = Unit
        override suspend fun move(id: String, parentId: String?) = Unit
        override suspend fun deleteKeepingContents(id: String) = Unit
        override suspend fun assignBoard(boardId: String, folderId: String?) = Unit
    }
    private fun model(repository: BoardRepository): LibraryViewModel {
        val dispatchers = object : AppDispatchers {
            override val io = main.dispatcher
            override val computation = main.dispatcher
        }
        return LibraryViewModel(ManageLibrary(repository, folders, FakeDocumentStorage(), dispatchers)).also(models::add)
    }

    @After fun close() {
        models.forEach { it.viewModelScope.cancel() }
    }

    @Test fun retryReopensAFailedObservationInsteadOfLeavingTheLibraryPermanentlyStale() = runTest {
        val base = InMemoryBoardRepository()
        val board = Board(name = "Retry fixture")
        base.save(board)
        var subscriptions = 0
        val repository = object : BoardRepository by base {
            override fun observeBoards() = flow {
                if (subscriptions++ == 0) throw IOException("fixture")
                emitAll(base.observeBoards())
            }
        }
        val model = model(repository)
        runCurrent()
        assertFalse(model.state.value.loading)
        assertTrue(model.state.value.message!!.retry)
        model.retry()
        advanceTimeBy(250)
        runCurrent()
        assertFalse(model.state.value.loading)
        assertEquals(board.id, model.state.value.boards.single().id)
        assertNull(model.state.value.message)
        assertEquals(2, subscriptions)
    }

    @Test fun closingTheViewModelCancelsItsSearchWithoutPublishingAFailure() = runTest {
        var cancelled = false
        val repository = object : BoardRepository by InMemoryBoardRepository() {
            override suspend fun search(query: String): Set<String> {
                try {
                    awaitCancellation()
                } finally {
                    cancelled = true
                }
            }
        }
        val model = model(repository)
        model.search("waiting")
        advanceTimeBy(250)
        runCurrent()
        model.viewModelScope.cancel()
        runCurrent()
        assertTrue(cancelled)
        assertNull(model.state.value.message)
    }
}
