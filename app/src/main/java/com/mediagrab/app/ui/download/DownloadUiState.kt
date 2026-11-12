package com.mediagrab.app.ui.download

import com.mediagrab.app.domain.model.AudioBitrate
import com.mediagrab.app.domain.model.DownloadError
import com.mediagrab.app.domain.model.DownloadFormatType
import com.mediagrab.app.domain.model.MediaInfo
import com.mediagrab.app.domain.model.VideoQuality

data class DownloadUiState(
    val urlInput: String = "",
    val clipboardUrl: String? = null,
    val isLoading: Boolean = false,
    val mediaInfo: MediaInfo? = null,
    val selectedFormat: DownloadFormatType = DownloadFormatType.VIDEO_MP4,
    val selectedVideoQuality: VideoQuality = VideoQuality.P1080,
    val selectedAudioBitrate: AudioBitrate = AudioBitrate.KBPS_192,
    val error: DownloadError? = null,
    val queuedMessageVisible: Boolean = false
)
