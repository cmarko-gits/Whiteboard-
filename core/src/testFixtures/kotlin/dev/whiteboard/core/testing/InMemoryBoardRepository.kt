package dev.whiteboard.core.testing

import dev.whiteboard.core.model.*
import dev.whiteboard.core.repository.BoardRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Deterministic domain fixture. Device tests separately qualify Room transactions and migrations. */
class InMemoryBoardRepository : BoardRepository {
    val saved = linkedMapOf<String, Board>()
    val drafts = linkedMapOf<String, Board>()
    var beforeSave: suspend (Board) -> Unit = {}
    var beforeDraft: suspend (Board) -> Unit = {}
    private val changed = MutableStateFlow(0)
    override fun observeBoards() = changed.map {
        saved.values.map {
            BoardSummary(
                it.id, it.name, it.updatedAt, 0L, it.favorite, it.pages.size,
                it.pages.sumOf { page -> page.elements.size }, null, it.trashedAt, it.folderId
            )
        }
    }
    override suspend fun load(id: String) = checkNotNull(saved[id]) { "Missing fixture board" }
    override suspend fun save(board: Board) {
        beforeSave(board)
        saved[board.id] = board
        changed.value++
    }
    override suspend fun saveDraft(board: Board) {
        beforeDraft(board)
        drafts[board.id] = board
    }
    override suspend fun draft(id: String) = drafts[id]
    override suspend fun discardDraft(id: String) {
        drafts.remove(id)
    }
    override suspend fun markOpened(id: String) {
        check(saved.containsKey(id))
    }
    override suspend fun rename(id: String, name: String) {
        save(load(id).copy(name = name))
    }
    override suspend fun setFavorite(id: String, favorite: Boolean) {
        save(load(id).copy(favorite = favorite))
    }
    override suspend fun trash(id: String) {
        save(load(id).copy(trashedAt = 1L))
    }
    override suspend fun restore(id: String) {
        save(load(id).copy(trashedAt = null))
    }
    override suspend fun deletePermanently(id: String) {
        saved.remove(id)
        drafts.remove(id)
        changed.value++
    }
    override suspend fun saveThumbnail(id: String, path: String) {
        check(saved.containsKey(id))
    }
    override suspend fun importBoards(boards: List<Board>) {
        boards.forEach { save(it) }
    }
    override suspend fun savedBoards() = saved.values.filter { it.trashedAt == null }
    override suspend fun retainedAssetIds() = (saved.values + drafts.values).flatMap { it.pages }.flatMap { it.elements }.referencedAssets()
    override suspend fun search(query: String) = saved.values.filter { it.name.contains(query, ignoreCase = true) }.map { it.id }.toSet()
}
