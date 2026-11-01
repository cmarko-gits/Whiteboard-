package dev.whiteboard.data

import androidx.room.withTransaction
import dev.whiteboard.core.model.*
import dev.whiteboard.core.repository.FolderRepository
import dev.whiteboard.format.DocumentCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class LocalFolderRepository(private val database: WhiteboardDatabase, private val codec: DocumentCodec = DocumentCodec()) :
    FolderRepository {
    private val folders = database.folders()
    private val boards = database.boards()
    override fun observeFolders() = folders.observe().map { rows -> rows.map { Folder(it.id, it.name, it.parentId) } }
    override suspend fun create(name: String, parentId: String?) = withContext(Dispatchers.IO) {
        val folder = Folder(name = name.trim(), parentId = parentId)
        database.withTransaction {
            require(parentId == null || folders.get(parentId) != null)
            folders.put(FolderEntity(folder.id, folder.name, folder.parentId))
        }
    }
    override suspend fun rename(id: String, name: String) = withContext(Dispatchers.IO) {
        require(name.isNotBlank() && name.length <= 120)
        database.withTransaction { folders.put(requireNotNull(folders.get(id)).copy(name = name.trim())) }
    }
    override suspend fun move(id: String, parentId: String?) = withContext(Dispatchers.IO) {
        database.withTransaction {
            val all = folders.all().map { Folder(it.id, it.name, it.parentId) }
            require(FolderTree(all).canMove(id, parentId)) { "Folder cannot be moved into its own subtree" }
            folders.put(requireNotNull(folders.get(id)).copy(parentId = parentId))
        }
    }
    override suspend fun deleteKeepingContents(id: String) = withContext(Dispatchers.IO) {
        database.withTransaction {
            val removed = requireNotNull(folders.get(id))
            folders.all().filter { it.parentId == id }.forEach { folders.put(it.copy(parentId = removed.parentId)) }
            boards.ids().forEach { boardId ->
                val board = requireNotNull(boards.board(boardId))
                if (board.folderId == id) assign(board, removed.parentId)
            }
            folders.delete(id)
        }
    }
    override suspend fun assignBoard(boardId: String, folderId: String?) = withContext(Dispatchers.IO) {
        database.withTransaction {
            require(folderId == null || folders.get(folderId) != null)
            assign(requireNotNull(boards.board(boardId)), folderId)
        }
    }
    private suspend fun assign(row: BoardEntity, folder: String?) {
        val saved = codec.decode(boards.document(row.id).joinToString("")).copy(folderId = folder)
        boards.upsert(row.copy(folderId = folder))
        boards.deleteDocument(row.id)
        boards.documentChunks(chunkDocument(codec.encode(saved)).mapIndexed { index, text -> DocumentChunk(row.id, index, text) })
        val draft = boards.draft(row.id)
        if (draft.isNotEmpty()) {
            val updated = codec.decode(draft.joinToString("")).copy(folderId = folder)
            boards.deleteDraft(row.id)
            boards.draftChunks(chunkDocument(codec.encode(updated)).mapIndexed { index, text -> DraftChunk(row.id, index, text) })
        }
    }
}
