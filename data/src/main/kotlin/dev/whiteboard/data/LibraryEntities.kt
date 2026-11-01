package dev.whiteboard.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "folders")
data class FolderEntity(@PrimaryKey val id: String, val name: String, val parentId: String?)

@Entity(
    tableName = "document_chunks",
    primaryKeys = ["boardId", "part"],
    foreignKeys = [
        ForeignKey(
            entity = BoardEntity::class,
            parentColumns = ["id"],
            childColumns = ["boardId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class DocumentChunk(val boardId: String, val part: Int, val content: String)

@Entity(
    tableName = "draft_chunks",
    primaryKeys = ["boardId", "part"],
    foreignKeys = [
        ForeignKey(
            entity = BoardEntity::class,
            parentColumns = ["id"],
            childColumns = ["boardId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class DraftChunk(val boardId: String, val part: Int, val content: String)

@Entity(
    tableName = "board_search",
    foreignKeys = [
        ForeignKey(
            entity = BoardEntity::class,
            parentColumns = ["id"],
            childColumns = ["boardId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class BoardSearch(@PrimaryKey val boardId: String, val text: String)

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY name")
    fun observe(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders")
    suspend fun all(): List<FolderEntity>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun get(id: String): FolderEntity?

    @Upsert suspend fun put(folder: FolderEntity)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun delete(id: String)
}
