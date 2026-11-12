package com.mediagrab.app.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Environment
import android.os.StatFs
import com.mediagrab.app.domain.model.DownloadError
import com.mediagrab.app.domain.model.MediaInfo
import com.mediagrab.app.domain.model.PlaylistEntry
import com.mediagrab.app.domain.model.SupportedPlatform
import com.mediagrab.app.domain.model.VideoQuality
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YtDlpRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun fetchMediaInfo(url: String): Result<MediaInfo> = withContext(Dispatchers.IO) {
        if (!isNetworkAvailable()) {
            return@withContext Result.failure(MappedDownloadException(DownloadError.NoInternet))
        }
        if (!hasMinimumFreeStorage(MIN_REQUIRED_BYTES)) {
            return@withContext Result.failure(MappedDownloadException(DownloadError.InsufficientStorage))
        }

        try {
            val request = YoutubeDLRequest(url).apply {
                addOption("--dump-single-json")
                addOption("--flat-playlist")
                addOption("--no-warnings")
            }

            val response = YoutubeDL.getInstance().execute(request, null)
            val json = JSONObject(response.out)
            val isPlaylist = json.optString("_type") == "playlist" && json.has("entries")

            if (isPlaylist) {
                val entriesArray = json.optJSONArray("entries")
                val entries = buildList {
                    if (entriesArray != null) {
                        for (i in 0 until entriesArray.length()) {
                            val item = entriesArray.optJSONObject(i) ?: continue
                            val entryUrl = item.optString("webpage_url")
                                .ifBlank { item.optString("url") }
                            if (entryUrl.isBlank()) continue
                            add(
                                PlaylistEntry(
                                    id = item.optString("id", "item_$i"),
                                    title = item.optString("title", "Item ${i + 1}"),
                                    url = entryUrl,
                                    thumbnailUrl = item.optString("thumbnail").ifBlank { null },
                                    durationSeconds = item.optLong("duration", 0L),
                                    isSelected = true
                                )
                            )
                        }
                    }
                }
                val platform = SupportedPlatform.fromUrlOrExtractor(
                    url = url,
                    extractorKey = json.optString("extractor_key")
                )
                return@withContext Result.success(
                    MediaInfo(
                        id = json.optString("id", url.hashCode().toString()),
                        url = url,
                        title = json.optString("title", "Playlist"),
                        thumbnailUrl = entries.firstOrNull()?.thumbnailUrl
                            ?: json.optString("thumbnail").ifBlank { null },
                        durationSeconds = entries.sumOf { it.durationSeconds },
                        platform = platform,
                        availableQualities = VideoQuality.entries,
                        isPlaylist = true,
                        playlistEntries = entries
                    )
                )
            }

            // Single video / audio
            val platform = SupportedPlatform.fromUrlOrExtractor(
                url = url,
                extractorKey = json.optString("extractor_key")
            )
            val heights = mutableSetOf<Int>()
            val formatsArray = json.optJSONArray("formats")
            if (formatsArray != null) {
                for (i in 0 until formatsArray.length()) {
                    val f = formatsArray.optJSONObject(i) ?: continue
                    val h = f.optInt("height", 0)
                    if (h > 0) heights.add(h)
                }
            }
            val qualities = VideoQuality.entries.filter { q ->
                heights.isEmpty() || heights.any { it >= q.height }
            }.ifEmpty { listOf(VideoQuality.P360, VideoQuality.P720) }

            Result.success(
                MediaInfo(
                    id = json.optString("id", url.hashCode().toString()),
                    url = json.optString("webpage_url", url),
                    title = json.optString("title", "Untitled Media"),
                    thumbnailUrl = json.optString("thumbnail").ifBlank { null },
                    durationSeconds = json.optLong("duration", 0L),
                    platform = platform,
                    availableQualities = qualities,
                    isPlaylist = false
                )
            )
        } catch (t: Throwable) {
            Result.failure(MappedDownloadException(mapThrowableToError(t)))
        }
    }

    fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun hasMinimumFreeStorage(requiredBytes: Long): Boolean {
        val path = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        val stat = StatFs(path.absolutePath)
        return stat.availableBytes >= requiredBytes
    }

    private fun mapThrowableToError(t: Throwable): DownloadError {
        val msg = (t.message ?: "").lowercase()
        return when {
            "private" in msg || "sign in" in msg || "login" in msg ||
                "unavailable" in msg || "age-restricted" in msg -> DownloadError.PrivateOrUnavailable
            "unable to resolve host" in msg || "network is unreachable" in msg ||
                "timed out" in msg -> DownloadError.NoInternet
            "no space left" in msg -> DownloadError.InsufficientStorage
            else -> DownloadError.ExtractionFailed(t.message ?: "Unknown error")
        }
    }

    companion object {
        private const val MIN_REQUIRED_BYTES = 50L * 1024L * 1024L // 50 MB minimum check
    }
}

class MappedDownloadException(val error: DownloadError) : Exception()
