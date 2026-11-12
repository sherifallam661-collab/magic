package com.mediagrab.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MediaItemType {
    VIDEO,
    AUDIO,
    TRIMMED,
    MERGED
}

enum class DownloadStatus {
    QUEUED,
    RUNNING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val url: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val platform: String,
    val mediaType: MediaItemType,
    val formatExtension: String,
    val qualityLabel: String,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val progress: Int = 0,
    val fileSizeBytes: Long = 0L,
    val localUri: String? = null,
    val filePath: String? = null,
    val errorMessage: String? = null,
    val workRequestId: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
