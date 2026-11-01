package dev.whiteboard.core.repository

import dev.whiteboard.core.model.Folder
import kotlinx.coroutines.flow.Flow

interface FolderRepository {
    fun observeFolders(): Flow<List<Folder>>
    suspend fun create(name: String, parentId: String?)
    suspend fun rename(id: String, name: String)
    suspend fun move(id: String, parentId: String?)
    suspend fun deleteKeepingContents(id: String)
    suspend fun assignBoard(boardId: String, folderId: String?)
}
