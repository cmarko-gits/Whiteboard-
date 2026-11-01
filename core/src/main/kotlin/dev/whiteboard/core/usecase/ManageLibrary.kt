package dev.whiteboard.core.usecase

import dev.whiteboard.core.concurrency.AppDispatchers
import dev.whiteboard.core.concurrency.DefaultAppDispatchers
import dev.whiteboard.core.model.*
import dev.whiteboard.core.repository.BoardRepository
import dev.whiteboard.core.repository.DocumentStorage
import dev.whiteboard.core.repository.FolderRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*

data class LibrarySnapshot(
    val boards: List<BoardSummary>,
    val folders: List<Folder>,
    val query: String,
    val matches: Set<String>,
    val error: DocumentError? = null
)

/** Library policy and joins live in the domain; the ViewModel only translates results into UI state. */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class ManageLibrary(
    private val boards: BoardRepository,
    private val folders: FolderRepository,
    private val storage: DocumentStorage,
    private val dispatchers: AppDispatchers = DefaultAppDispatchers
) {
    fun observe(query: Flow<String>): Flow<LibrarySnapshot> = combine(
        boards.observeBoards(),
        folders.observeFolders(),
        query.map { it.trim().take(512) }.debounce(250).distinctUntilChanged()
    ) { list, tree, search -> Triple(list, tree, search) }.mapLatest { (list, tree, search) ->
        val paths = FolderTree(tree)
        val ordered = tree.sortedBy { paths.path(it.id) }
        try {
            val matches = if (search.isEmpty()) {
                emptySet()
            } else {
                boards.search(search) + list.filter {
                    normalizeForSearch(paths.path(it.folderId)).contains(normalizeForSearch(search))
                }.map { it.id }
            }
            LibrarySnapshot(list, ordered, search, matches)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: DocumentFailure) {
            LibrarySnapshot(list, ordered, search, emptySet(), failure.error)
        } catch (_: Exception) {
            LibrarySnapshot(list, ordered, search, emptySet(), DocumentError.STORAGE_UNAVAILABLE)
        }
    }.flowOn(dispatchers.computation)
    suspend fun prepare() = cleanup { storage.pruneUnused() }
    suspend fun preview(reference: String): ByteArray? = try {
        storage.readPreview(reference)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }
    suspend fun rename(id: String, name: String) = boards.rename(id, name.trim().take(120).also { require(it.isNotEmpty()) })
    suspend fun favorite(board: BoardSummary) = boards.setFavorite(board.id, !board.favorite)
    suspend fun trash(id: String) = boards.trash(id)
    suspend fun restore(id: String) = boards.restore(id)
    suspend fun permanentlyDelete(id: String) {
        boards.deletePermanently(id)
        // A committed deletion is not reported as failed just because optional private-file cleanup failed.
        cleanup {
            storage.deletePreview(id)
            storage.pruneUnused()
        }
    }
    suspend fun createFolder(name: String, parent: String?) = folders.create(name, parent)
    suspend fun renameFolder(id: String, name: String) = folders.rename(id, name)
    suspend fun moveFolder(id: String, parent: String?) = folders.move(id, parent)
    suspend fun deleteFolder(id: String) = folders.deleteKeepingContents(id)
    suspend fun assignBoard(board: String, folder: String?) = folders.assignBoard(board, folder)
    private suspend fun cleanup(action: suspend () -> Unit) {
        try {
            action()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Preserve files when reference discovery or cleanup is unavailable; retry on a later library load.
        }
    }
}
