package dev.whiteboard.data

import androidx.room.withTransaction
import dev.whiteboard.core.model.*
import dev.whiteboard.core.repository.BoardRepository
import dev.whiteboard.format.DocumentCodec
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LocalBoardRepository(
    private val database: WhiteboardDatabase,
    private val codec: DocumentCodec = DocumentCodec(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : BoardRepository {
    private val dao = database.boards()
    private val mutations = Mutex()
    private var indexReady = false

    override fun observeBoards(): Flow<List<BoardSummary>> = dao.observeBoards().map { rows ->
        rows.map {
            BoardSummary(
                it.id, it.name, it.updatedAt, it.lastOpenedAt, it.favorite,
                it.pageCount, it.objectCount, it.thumbnail, it.trashedAt, it.folderId
            )
        }
    }.flowOn(dispatcher)

    override suspend fun load(id: String): Board = storageCall(dispatcher) {
        database.withTransaction { read(id) }
    }
    private suspend fun read(id: String): Board {
        requireNotNull(dao.board(id)) { "Board not found" }
        val chunks = dao.document(id)
        require(chunks.isNotEmpty()) { "Document data is unavailable" }
        return codec.decode(chunks.joinToString(""))
    }
    override suspend fun save(board: Board) = storageCall(dispatcher) {
        mutations.withLock { database.withTransaction { put(board) } }
    }

    /** Caller owns a database transaction. Metadata and content always move to the same revision. */
    private suspend fun put(source: Board) {
        val folder = source.folderId?.takeIf { database.folders().get(it) != null }
        val board = if (folder == source.folderId) source else source.copy(folderId = folder)
        val chunks = chunkDocument(codec.encode(board))
        val existing = dao.board(board.id)
        dao.upsert(
            BoardEntity(
                board.id, board.name, board.updatedAt, existing?.lastOpenedAt ?: System.currentTimeMillis(),
                board.favorite, board.pages.size, board.pages.sumOf { it.elements.size }, existing?.thumbnail,
                board.trashedAt, board.folderId, ""
            )
        )
        dao.deleteDocument(board.id)
        dao.documentChunks(chunks.mapIndexed { index, content -> DocumentChunk(board.id, index, content) })
        dao.index(BoardSearch(board.id, searchKey(board.searchableText())))
    }
    override suspend fun saveDraft(board: Board) = storageCall(dispatcher) {
        val chunks = chunkDocument(codec.encode(board))
        mutations.withLock {
            database.withTransaction {
                dao.deleteDraft(board.id)
                dao.draftChunks(chunks.mapIndexed { index, content -> DraftChunk(board.id, index, content) })
            }
        }
    }
    override suspend fun draft(id: String): Board? = storageCall(dispatcher) {
        database.withTransaction { dao.draft(id).takeIf { it.isNotEmpty() }?.joinToString("")?.let(codec::decode) }
    }
    override suspend fun discardDraft(id: String) {
        mutations.withLock { dao.deleteDraft(id) }
    }
    override suspend fun markOpened(id: String) {
        dao.markOpened(id, System.currentTimeMillis())
    }
    override suspend fun rename(id: String, name: String) =
        mutate(id) { it.copy(name = name.trim(), updatedAt = System.currentTimeMillis()) }
    override suspend fun setFavorite(id: String, favorite: Boolean) = mutate(id) { it.copy(favorite = favorite) }
    override suspend fun trash(id: String) = mutate(id) { it.copy(trashedAt = System.currentTimeMillis()) }
    override suspend fun restore(id: String) = mutate(id) { it.copy(trashedAt = null) }
    private suspend fun mutate(id: String, change: (Board) -> Board) = storageCall(dispatcher) {
        mutations.withLock { database.withTransaction { put(change(read(id))) } }
    }
    override suspend fun deletePermanently(id: String) {
        mutations.withLock { dao.delete(id) }
    }
    override suspend fun saveThumbnail(id: String, path: String) {
        dao.thumbnail(id, path)
    }
    override suspend fun savedBoards(): List<Board> = storageCall(dispatcher) {
        database.withTransaction { dao.savedBoards().map { read(it.id) } }
    }
    override suspend fun retainedAssetIds(): Set<String> = storageCall(dispatcher) {
        database.withTransaction {
            dao.ids().flatMap { id ->
                val saved = read(id).referencedAssets()
                val draft = dao.draft(id).takeIf { it.isNotEmpty() }?.joinToString("")?.let(codec::decode)
                saved + (draft?.referencedAssets() ?: emptySet())
            }.toSet()
        }
    }
    override suspend fun search(query: String): Set<String> = storageCall(dispatcher) {
        mutations.withLock {
            if (!indexReady) {
                database.withTransaction { dao.ids().forEach { id -> dao.index(BoardSearch(id, searchKey(read(id).searchableText()))) } }
                indexReady = true
            }
            val escaped = searchKey(query.take(512)).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
            dao.search("%$escaped%").toSet()
        }
    }
    override suspend fun importBoards(boards: List<Board>) = storageCall(dispatcher) {
        mutations.withLock {
            database.withTransaction {
                boards.forEach { board ->
                    val suffix = " (imported)"
                    put(board.copy(id = newId(), name = board.name.take(120 - suffix.length) + suffix, folderId = null, trashedAt = null))
                }
            }
        }
    }
}
