package com.mediagrab.app.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.mediagrab.app.R
import com.mediagrab.app.data.local.dao.DownloadDao
import com.mediagrab.app.data.local.entity.DownloadStatus
import com.mediagrab.app.data.local.entity.MediaItemType
import com.mediagrab.app.data.preferences.SettingsPreferences
import com.mediagrab.app.util.MediaStoreHelper
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val downloadDao: DownloadDao,
    private val mediaStoreHelper: MediaStoreHelper,
    private val settingsPreferences: SettingsPreferences
) : CoroutineWorker(appContext, workerParams) {

    private val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val downloadId = inputData.getLong(KEY_DOWNLOAD_ID, -1L)
        if (downloadId <= 0L) return@withContext Result.failure()

        val entity = downloadDao.getById(downloadId) ?: return@withContext Result.failure()
        val settings = settingsPreferences.settings.first()

        ensureChannelExists()
        setForeground(createForegroundInfo(downloadId.toInt(), entity.title, 0))
        downloadDao.updateProgress(downloadId, DownloadStatus.RUNNING, 0)

        val workDir = File(appContext.getExternalFilesDir(null), "mediagrab_temp/$downloadId")
            .apply { mkdirs() }
        val processId = "dl_$downloadId"

        try {
            val request = YoutubeDLRequest(entity.url).apply {
                addOption("-o", "${workDir.absolutePath}/%(title)s.%(ext)s")
                addOption("--continue") // Resume partial downloads
                addOption("--retries", "5")
                addOption("--fragment-retries", "5")
                addOption("--no-mtime")

                if (entity.mediaType == MediaItemType.VIDEO) {
                    val maxHeight = entity.qualityLabel.filter { it.isDigit() }.toIntOrNull() ?: 1080
                    addOption("-f", "bestvideo[height<=${maxHeight}][ext=mp4]+bestaudio[ext=m4a]/best[height<=${maxHeight}][ext=mp4]/best")
                    addOption("--merge-output-format", "mp4")
                } else {
                    val audioFormat = entity.formatExtension.lowercase()
                    val bitrateKbps = entity.qualityLabel.filter { it.isDigit() }.ifBlank { "192" }
                    addOption("-x")
                    addOption("--audio-format", audioFormat)
                    addOption("--audio-quality", "${bitrateKbps}K")
                }
            }

            YoutubeDL.getInstance().execute(request, processId) { progress, _, _ ->
                val pct = progress.toInt().coerceIn(0, 100)
                setForegroundAsync(createForegroundInfo(downloadId.toInt(), entity.title, pct))
                // Persist progress asynchronously via Room
                kotlinx.coroutines.runBlocking {
                    downloadDao.updateProgress(downloadId, DownloadStatus.RUNNING, pct)
                }
            }

            val downloadedFile = workDir.listFiles()?.maxByOrNull { it.lastModified() }
            if (downloadedFile == null || !downloadedFile.exists()) {
                downloadDao.updateStatus(downloadId, DownloadStatus.FAILED, "Output file missing")
                return@withContext Result.failure()
            }

            val isVideo = entity.mediaType == MediaItemType.VIDEO
            val mimeType = when (downloadedFile.extension.lowercase()) {
                "mp4" -> "video/mp4"
                "mp3" -> "audio/mpeg"
                "m4a" -> "audio/mp4"
                else -> if (isVideo) "video/*" else "audio/*"
            }

            val published = mediaStoreHelper.publishToMediaStore(
                sourceFile = downloadedFile,
                displayName = downloadedFile.name,
                mimeType = mimeType,
                isVideo = isVideo,
                subFolder = settings.defaultFolderName
            )

            workDir.deleteRecursively()

            if (published != null) {
                downloadDao.markCompleted(
                    id = downloadId,
                    sizeBytes = published.sizeBytes,
                    localUri = published.uri,
                    filePath = published.filePath
                )
                Result.success()
            } else {
                downloadDao.updateStatus(downloadId, DownloadStatus.FAILED, "MediaStore publish failed")
                Result.failure()
            }
        } catch (ce: CancellationException) {
            YoutubeDL.getInstance().destroyProcessById(processId)
            val current = downloadDao.getById(downloadId)
            if (current?.status != DownloadStatus.PAUSED && current?.status != DownloadStatus.CANCELLED) {
                downloadDao.updateStatus(downloadId, DownloadStatus.PAUSED)
            }
            throw ce
        } catch (t: Throwable) {
            val msg = t.message ?: "Download failed"
            if (runAttemptCount < 3 && isTransientNetworkError(msg)) {
                downloadDao.updateStatus(downloadId, DownloadStatus.QUEUED, msg)
                Result.retry()
            } else {
                downloadDao.updateStatus(downloadId, DownloadStatus.FAILED, msg)
                Result.failure()
            }
        }
    }

    private fun isTransientNetworkError(message: String): Boolean {
        val lower = message.lowercase()
        return "timed out" in lower || "connection reset" in lower ||
            "unable to resolve host" in lower || "network" in lower
    }

    private fun createForegroundInfo(notificationId: Int, title: String, progress: Int): ForegroundInfo {
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText("$progress%")
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private fun ensureChannelExists() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                appContext.getString(R.string.download_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val KEY_DOWNLOAD_ID = "download_id"
        private const val CHANNEL_ID = "mediagrab_downloads"
    }
}
