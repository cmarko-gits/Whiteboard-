package dev.whiteboard.core.repository

import dev.whiteboard.core.model.Board
import dev.whiteboard.core.model.BoardSummary
import kotlinx.coroutines.flow.Flow

interface BoardRepository {
    fun observeBoards(): Flow<List<BoardSummary>>
    suspend fun load(id: String): Board
    suspend fun save(board: Board)
    suspend fun saveDraft(board: Board)
    suspend fun draft(id: String): Board?
    suspend fun discardDraft(id: String)
    suspend fun markOpened(id: String)
    suspend fun rename(id: String, name: String)
    suspend fun setFavorite(id: String, favorite: Boolean)
    suspend fun trash(id: String)
    suspend fun restore(id: String)
    suspend fun deletePermanently(id: String)
    suspend fun saveThumbnail(id: String, path: String)
    suspend fun importBoards(boards: List<Board>)
    suspend fun savedBoards(): List<Board>
    suspend fun retainedAssetIds(): Set<String>
    suspend fun search(query: String): Set<String>
}
