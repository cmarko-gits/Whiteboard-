package dev.whiteboard.data

import androidx.room.*
import dev.whiteboard.data.cloud.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "boards")
data class BoardEntity(
    @PrimaryKey val id: String,
    val name: String,
    val updatedAt: Long,
    val lastOpenedAt: Long,
    val favorite: Boolean,
    val pageCount: Int,
    val objectCount: Int,
    val thumbnail: String?,
    val trashedAt: Long?,
    val folderId: String?,
    val payload: String
)

@Dao
interface BoardDao {
    @Query("SELECT * FROM boards ORDER BY lastOpenedAt DESC, updatedAt DESC")
    fun observeBoards(): Flow<List<BoardEntity>>

    @Query("SELECT * FROM boards WHERE id = :id")
    suspend fun board(id: String): BoardEntity?

    @Query("SELECT * FROM boards WHERE trashedAt IS NULL ORDER BY updatedAt DESC")
    suspend fun savedBoards(): List<BoardEntity>

    @Upsert suspend fun upsert(entity: BoardEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun documentChunks(chunks: List<DocumentChunk>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun draftChunks(chunks: List<DraftChunk>)

    @Upsert suspend fun index(row: BoardSearch)

    @Query("SELECT content FROM document_chunks WHERE boardId = :id ORDER BY part")
    suspend fun document(id: String): List<String>

    @Query("SELECT content FROM draft_chunks WHERE boardId = :id ORDER BY part")
    suspend fun draft(id: String): List<String>

    @Query("DELETE FROM document_chunks WHERE boardId = :id")
    suspend fun deleteDocument(id: String)

    @Query("SELECT id FROM boards")
    suspend fun ids(): List<String>

    @Query("SELECT boardId FROM board_search WHERE text LIKE :pattern ESCAPE '\\'")
    suspend fun search(pattern: String): List<String>

    @Query("DELETE FROM draft_chunks WHERE boardId = :id")
    suspend fun deleteDraft(id: String)

    @Query("UPDATE boards SET lastOpenedAt = :time WHERE id = :id")
    suspend fun markOpened(id: String, time: Long)

    @Query("UPDATE boards SET thumbnail = :path WHERE id = :id")
    suspend fun thumbnail(id: String, path: String)

    @Query("DELETE FROM boards WHERE id = :id")
    suspend fun delete(id: String)
}

@Database(
    entities = [
        BoardEntity::class, DocumentChunk::class, DraftChunk::class, BoardSearch::class, FolderEntity::class,
        SyncBoardEntity::class, SyncDocumentChunk::class, SyncOperationEntity::class, SyncOperationChunk::class,
        PublicationEntity::class, PublicationChunk::class
    ],
    version = 4,
    exportSchema = true
)
abstract class WhiteboardDatabase : RoomDatabase() {
    abstract fun boards(): BoardDao
    abstract fun folders(): FolderDao
    abstract fun sync(): SyncDao
    abstract fun publications(): PublicationDao
}
