package dev.whiteboard.data.cloud

import androidx.room.*
import dev.whiteboard.core.cloud.PendingPublication
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "publications",
    primaryKeys = ["accountId", "sourceId"],
    indices = [Index(value = ["accountId", "operationId"], unique = true)]
)
data class PublicationEntity(
    val accountId: String,
    val sourceId: String,
    val name: String,
    val operationId: String,
    val remoteId: String?,
    val payloadBytes: Long
)

@Entity(
    tableName = "publication_chunks",
    primaryKeys = ["accountId", "sourceId", "part"],
    foreignKeys = [
        ForeignKey(
            entity = PublicationEntity::class,
            parentColumns = ["accountId", "sourceId"],
            childColumns = ["accountId", "sourceId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PublicationChunk(val accountId: String, val sourceId: String, val part: Int, val content: String)

@Dao
interface PublicationDao {
    @Query("SELECT sourceId, name FROM publications WHERE accountId = :accountId AND remoteId IS NULL ORDER BY name, sourceId")
    fun observePending(accountId: String): Flow<List<PendingPublication>>

    @Query("SELECT * FROM publications WHERE accountId = :accountId AND sourceId = :sourceId")
    suspend fun find(accountId: String, sourceId: String): PublicationEntity?

    @Query("SELECT COUNT(*) FROM publications WHERE accountId = :accountId AND remoteId IS NULL")
    suspend fun pendingCount(accountId: String): Int

    @Query("SELECT COUNT(*) FROM publications WHERE accountId = :accountId")
    suspend fun count(accountId: String): Int

    @Query("SELECT COALESCE(SUM(payloadBytes), 0) FROM publications WHERE accountId = :accountId")
    suspend fun payloadBytes(accountId: String): Long

    @Query("SELECT content FROM publication_chunks WHERE accountId = :accountId AND sourceId = :sourceId ORDER BY part")
    suspend fun content(accountId: String, sourceId: String): List<String>

    @Insert suspend fun insert(request: PublicationEntity)

    @Insert suspend fun chunks(chunks: List<PublicationChunk>)

    @Query("UPDATE publications SET remoteId = :remoteId, payloadBytes = 0 WHERE accountId = :accountId AND sourceId = :sourceId")
    suspend fun complete(accountId: String, sourceId: String, remoteId: String)

    @Query("DELETE FROM publication_chunks WHERE accountId = :accountId AND sourceId = :sourceId")
    suspend fun clearContent(accountId: String, sourceId: String)
}
