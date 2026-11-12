package com.mediagrab.app.domain.model

enum class SupportedPlatform(val displayName: String) {
    YOUTUBE("YouTube"),
    FACEBOOK("Facebook"),
    INSTAGRAM("Instagram"),
    TIKTOK("TikTok"),
    X_TWITTER("X"),
    SNAPCHAT("Snapchat"),
    PINTEREST("Pinterest"),
    REDDIT("Reddit"),
    VIMEO("Vimeo"),
    SOUNDCLOUD("SoundCloud"),
    OTHER("Web");

    companion object {
        fun fromUrlOrExtractor(url: String, extractorKey: String? = null): SupportedPlatform {
            val key = (extractorKey ?: "").lowercase()
            val host = url.lowercase()
            return when {
                "youtube" in key || "youtu.be" in host || "youtube.com" in host -> YOUTUBE
                "facebook" in key || "fb.watch" in host || "facebook.com" in host -> FACEBOOK
                "instagram" in key || "instagram.com" in host -> INSTAGRAM
                "tiktok" in key || "tiktok.com" in host -> TIKTOK
                "twitter" in key || "x.com" in host || "twitter.com" in host -> X_TWITTER
                "snapchat" in key || "snapchat.com" in host -> SNAPCHAT
                "pinterest" in key || "pinterest." in host || "pin.it" in host -> PINTEREST
                "reddit" in key || "reddit.com" in host || "redd.it" in host -> REDDIT
                "vimeo" in key || "vimeo.com" in host -> VIMEO
                "soundcloud" in key || "soundcloud.com" in host -> SOUNDCLOUD
                else -> OTHER
            }
        }
    }
}

enum class DownloadFormatType {
    VIDEO_MP4,
    AUDIO_MP3,
    AUDIO_M4A
}

enum class VideoQuality(val height: Int, val label: String) {
    P144(144, "144p"),
    P360(360, "360p"),
    P480(480, "480p"),
    P720(720, "720p HD"),
    P1080(1080, "1080p FHD"),
    P1440(1440, "1440p 2K"),
    P2160(2160, "2160p 4K")
}

enum class AudioBitrate(val kbps: Int, val label: String) {
    KBPS_128(128, "128 kbps"),
    KBPS_192(192, "192 kbps"),
    KBPS_320(320, "320 kbps")
}

data class PlaylistEntry(
    val id: String,
    val title: String,
    val url: String,
    val thumbnailUrl: String?,
    val durationSeconds: Long,
    val isSelected: Boolean = true
)

data class MediaInfo(
    val id: String,
    val url: String,
    val title: String,
    val thumbnailUrl: String?,
    val durationSeconds: Long,
    val platform: SupportedPlatform,
    val availableQualities: List<VideoQuality>,
    val isPlaylist: Boolean = false,
    val playlistEntries: List<PlaylistEntry> = emptyList()
)

data class DownloadRequest(
    val url: String,
    val title: String,
    val thumbnailUrl: String?,
    val platform: SupportedPlatform,
    val formatType: DownloadFormatType,
    val videoQuality: VideoQuality,
    val audioBitrate: AudioBitrate
)

sealed interface DownloadError {
    data object InvalidUrl : DownloadError
    data object NoInternet : DownloadError
    data object PrivateOrUnavailable : DownloadError
    data object InsufficientStorage : DownloadError
    data class ExtractionFailed(val detail: String) : DownloadError
}
