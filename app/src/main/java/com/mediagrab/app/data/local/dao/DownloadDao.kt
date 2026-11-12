package com.mediagrab.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mediagrab.app.data.local.entity.DownloadEntity
import com.mediagrab.app.data.local.entity.DownloadStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getById(id: Long): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<DownloadEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DownloadEntity): Long

    @Update
    suspend fun update(entity: DownloadEntity)

    @Query("UPDATE downloads SET status = :status, progress = :progress WHERE id = :id")
    suspend fun updateProgress(id: Long, status: DownloadStatus, progress: Int)

    @Query("UPDATE downloads SET status = :status, errorMessage = :errorMessage WHERE id = :id")
    suspend fun updateStatus(id: Long, status: DownloadStatus, errorMessage: String? = null)

    @Query(
        "UPDATE downloads SET status = :status, progress = 100, fileSizeBytes = :sizeBytes, " +
            "localUri = :localUri, filePath = :filePath, errorMessage = NULL WHERE id = :id"
    )
    suspend fun markCompleted(
        id: Long,
        status: DownloadStatus = DownloadStatus.COMPLETED,
        sizeBytes: Long,
        localUri: String?,
        filePath: String?
    )

    @Query("UPDATE downloads SET workRequestId = :workId WHERE id = :id")
    suspend fun updateWorkRequestId(id: Long, workId: String)

    @Query("DELETE FROM downloads WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM downloads")
    suspend fun clearAll()
}
