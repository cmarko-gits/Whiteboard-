package dev.whiteboard.data.cloud

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "sync_boards", primaryKeys = ["accountId", "boardId"])
data class SyncBoardEntity(
    val accountId: String,
    val boardId: String,
    val revision: Long,
    val role: String,
    val localVersion: Long,
    val conflictReason: String?,
    val conflictTarget: String?,
    @ColumnInfo(defaultValue = "''") val name: String = ""
)

@Entity(
    tableName = "sync_documents",
    primaryKeys = ["accountId", "boardId", "kind", "part"],
    foreignKeys = [
        ForeignKey(
            entity = SyncBoardEntity::class,
            parentColumns = ["accountId", "boardId"],
            childColumns = ["accountId", "boardId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class SyncDocumentChunk(val accountId: String, val boardId: String, val kind: String, val part: Int, val content: String)

@Entity(
    tableName = "sync_operations",
    primaryKeys = ["accountId", "operationId"],
    indices = [Index(value = ["accountId", "boardId", "ordinal"], unique = true)],
    foreignKeys = [
        ForeignKey(
            entity = SyncBoardEntity::class,
            parentColumns = ["accountId", "boardId"],
            childColumns = ["accountId", "boardId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class SyncOperationEntity(
    val accountId: String,
    val operationId: String,
    val boardId: String,
    val ordinal: Long,
    val baseRevision: Long,
    val payloadBytes: Long
)

@Entity(
    tableName = "sync_operation_chunks",
    primaryKeys = ["accountId", "operationId", "part"],
    foreignKeys = [
        ForeignKey(
            entity = SyncOperationEntity::class,
            parentColumns = ["accountId", "operationId"],
            childColumns = ["accountId", "operationId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class SyncOperationChunk(val accountId: String, val operationId: String, val part: Int, val content: String)

@Dao
interface SyncDao {
    @Query(
        "SELECT s.boardId, s.name, s.revision, s.role, s.conflictReason, COUNT(o.operationId) AS pending FROM sync_boards s LEFT JOIN sync_operations o ON o.accountId = s.accountId AND o.boardId = s.boardId WHERE s.accountId = :accountId GROUP BY s.accountId, s.boardId ORDER BY s.name, s.boardId"
    )
    fun observeBoards(accountId: String): Flow<List<SyncListing>>

    @Query("SELECT * FROM sync_boards WHERE accountId = :accountId AND boardId = :boardId")
    suspend fun board(accountId: String, boardId: String): SyncBoardEntity?

    @Insert suspend fun insert(board: SyncBoardEntity)

    @Update suspend fun update(board: SyncBoardEntity)

    @Query("SELECT content FROM sync_documents WHERE accountId = :accountId AND boardId = :boardId AND kind = :kind ORDER BY part")
    suspend fun document(accountId: String, boardId: String, kind: String): List<String>

    @Query("DELETE FROM sync_documents WHERE accountId = :accountId AND boardId = :boardId AND kind = :kind")
    suspend fun clearDocument(accountId: String, boardId: String, kind: String)

    @Insert suspend fun documents(chunks: List<SyncDocumentChunk>)

    @Query("SELECT * FROM sync_operations WHERE accountId = :accountId AND boardId = :boardId ORDER BY ordinal")
    suspend fun operations(accountId: String, boardId: String): List<SyncOperationEntity>

    @Query("SELECT COALESCE(SUM(payloadBytes), 0) FROM sync_operations WHERE accountId = :accountId")
    suspend fun queuedBytes(accountId: String): Long

    @Insert suspend fun operation(operation: SyncOperationEntity)

    @Insert suspend fun operationChunks(chunks: List<SyncOperationChunk>)

    @Query("SELECT content FROM sync_operation_chunks WHERE accountId = :accountId AND operationId = :operationId ORDER BY part")
    suspend fun operationBody(accountId: String, operationId: String): List<String>

    @Query("DELETE FROM sync_operations WHERE accountId = :accountId AND operationId = :operationId")
    suspend fun acknowledge(accountId: String, operationId: String)
}

data class SyncListing(
    val boardId: String,
    val name: String,
    val revision: Long,
    val role: String,
    val conflictReason: String?,
    val pending: Int
)
