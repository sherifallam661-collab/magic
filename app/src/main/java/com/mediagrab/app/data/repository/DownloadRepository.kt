package com.mediagrab.app.data.repository

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.mediagrab.app.data.local.dao.DownloadDao
import com.mediagrab.app.data.local.entity.DownloadEntity
import com.mediagrab.app.data.local.entity.DownloadStatus
import com.mediagrab.app.data.local.entity.MediaItemType
import com.mediagrab.app.data.preferences.SettingsPreferences
import com.mediagrab.app.domain.model.DownloadFormatType
import com.mediagrab.app.domain.model.DownloadRequest
import com.mediagrab.app.util.MediaStoreHelper
import com.mediagrab.app.worker.DownloadWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadDao: DownloadDao,
    private val mediaStoreHelper: MediaStoreHelper,
    private val settingsPreferences: SettingsPreferences
) {
    private val workManager: WorkManager by lazy { WorkManager.getInstance(context) }

    fun observeDownloads(): Flow<List<DownloadEntity>> = downloadDao.observeAll()

    suspend fun enqueueDownloads(requests: List<DownloadRequest>): List<Long> {
        val settings = settingsPreferences.settings.first()
        val ids = mutableListOf<Long>()

        requests.forEach { req ->
            val mediaType = if (req.formatType == DownloadFormatType.VIDEO_MP4) {
                MediaItemType.VIDEO
            } else {
                MediaItemType.AUDIO
            }
            val ext = when (req.formatType) {
                DownloadFormatType.VIDEO_MP4 -> "mp4"
                DownloadFormatType.AUDIO_MP3 -> "mp3"
                DownloadFormatType.AUDIO_M4A -> "m4a"
            }
            val qualityLabel = if (mediaType == MediaItemType.VIDEO) {
                req.videoQuality.label
            } else {
                req.audioBitrate.label
            }

            val entity = DownloadEntity(
                url = req.url,
                title = req.title,
                thumbnailUrl = req.thumbnailUrl,
                platform = req.platform.displayName,
                mediaType = mediaType,
                formatExtension = ext,
                qualityLabel = qualityLabel,
                status = DownloadStatus.QUEUED
            )
            val id = downloadDao.insert(entity)
            ids.add(id)
            scheduleWorker(id, settings.wifiOnly)
        }
        return ids
    }

    suspend fun pauseDownload(id: Long) {
        val entity = downloadDao.getById(id) ?: return
        downloadDao.updateStatus(id, DownloadStatus.PAUSED)
        entity.workRequestId?.let { workId ->
            runCatching { workManager.cancelWorkById(UUID.fromString(workId)) }
        }
    }

    suspend fun resumeOrRetryDownload(id: Long) {
        val entity = downloadDao.getById(id) ?: return
        val settings = settingsPreferences.settings.first()
        downloadDao.updateStatus(id, DownloadStatus.QUEUED, null)
        scheduleWorker(entity.id, settings.wifiOnly)
    }

    suspend fun cancelDownload(id: Long) {
        val entity = downloadDao.getById(id) ?: return
        downloadDao.updateStatus(id, DownloadStatus.CANCELLED)
        entity.workRequestId?.let { workId ->
            runCatching { workManager.cancelWorkById(UUID.fromString(workId)) }
        }
    }

    suspend fun deleteDownloads(ids: List<Long>, deleteFilesFromDevice: Boolean) {
        val items = downloadDao.getByIds(ids)
        items.forEach { item ->
            item.workRequestId?.let { workId ->
                runCatching { workManager.cancelWorkById(UUID.fromString(workId)) }
            }
            if (deleteFilesFromDevice) {
                mediaStoreHelper.deleteFromDevice(item.localUri, item.filePath)
            }
        }
        downloadDao.deleteByIds(ids)
    }

    suspend fun clearHistory(deleteFilesFromDevice: Boolean) {
        if (deleteFilesFromDevice) {
            val all = downloadDao.observeAll().first()
            all.forEach { item ->
                mediaStoreHelper.deleteFromDevice(item.localUri, item.filePath)
            }
        }
        workManager.cancelAllWorkByTag(TAG_MEDIAGRAB_DOWNLOAD)
        downloadDao.clearAll()
    }

    suspend fun insertProcessedAudio(
        title: String,
        mediaType: MediaItemType,
        formatExtension: String,
        sizeBytes: Long,
        localUri: String?,
        filePath: String?
    ): Long {
        return downloadDao.insert(
            DownloadEntity(
                url = "local://processed",
                title = title,
                platform = "MediaGrab",
                mediaType = mediaType,
                formatExtension = formatExtension,
                qualityLabel = formatExtension.uppercase(),
                status = DownloadStatus.COMPLETED,
                progress = 100,
                fileSizeBytes = sizeBytes,
                localUri = localUri,
                filePath = filePath
            )
        )
    }

    private suspend fun scheduleWorker(downloadId: Long, wifiOnly: Boolean) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(
                if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
            )
            .setRequiresStorageNotLow(true)
            .build()

        val input = Data.Builder()
            .putLong(DownloadWorker.KEY_DOWNLOAD_ID, downloadId)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(constraints)
            .setInputData(input)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .addTag(TAG_MEDIAGRAB_DOWNLOAD)
            .build()

        downloadDao.updateWorkRequestId(downloadId, workRequest.id.toString())
        workManager.enqueueUniqueWork(
            "download_$downloadId",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    companion object {
        private const val TAG_MEDIAGRAB_DOWNLOAD = "mediagrab_download"
    }
}
