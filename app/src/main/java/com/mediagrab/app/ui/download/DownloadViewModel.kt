package com.mediagrab.app.ui.download

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediagrab.app.data.preferences.SettingsPreferences
import com.mediagrab.app.data.repository.DownloadRepository
import com.mediagrab.app.data.repository.MappedDownloadException
import com.mediagrab.app.data.repository.YtDlpRepository
import com.mediagrab.app.domain.model.AudioBitrate
import com.mediagrab.app.domain.model.DownloadError
import com.mediagrab.app.domain.model.DownloadFormatType
import com.mediagrab.app.domain.model.DownloadRequest
import com.mediagrab.app.domain.model.VideoQuality
import com.mediagrab.app.util.ClipboardHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val ytDlpRepository: YtDlpRepository,
    private val downloadRepository: DownloadRepository,
    private val settingsPreferences: SettingsPreferences,
    private val clipboardHelper: ClipboardHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadUiState())
    val uiState: StateFlow<DownloadUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val defaultQuality = settingsPreferences.settings.first().defaultQuality
            _uiState.update { it.copy(selectedVideoQuality = defaultQuality) }
        }
    }

    fun checkClipboard() {
        val detected = clipboardHelper.getValidUrlFromClipboard()
        _uiState.update { state ->
            if (detected != null && detected != state.urlInput) {
                state.copy(clipboardUrl = detected)
            } else {
                state.copy(clipboardUrl = null)
            }
        }
    }

    fun onUrlChanged(newUrl: String) {
        _uiState.update {
            it.copy(
                urlInput = newUrl,
                error = null,
                queuedMessageVisible = false
            )
        }
    }

    fun applyClipboardUrl() {
        val clip = _uiState.value.clipboardUrl ?: return
        _uiState.update {
            it.copy(
                urlInput = clip,
                clipboardUrl = null,
                error = null
            )
        }
        fetchMetadata()
    }

    fun handleSharedUrl(rawText: String) {
        val extracted = clipboardHelper.extractFirstUrl(rawText) ?: rawText.trim()
        if (extracted.isNotBlank()) {
            _uiState.update {
                it.copy(
                    urlInput = extracted,
                    clipboardUrl = null,
                    error = null
                )
            }
            fetchMetadata()
        }
    }

    fun clearInput() {
        _uiState.update {
            DownloadUiState(clipboardUrl = it.clipboardUrl)
        }
    }

    fun fetchMetadata() {
        val url = _uiState.value.urlInput.trim()
        if (!clipboardHelper.isValidHttpUrl(url)) {
            _uiState.update { it.copy(error = DownloadError.InvalidUrl, isLoading = false) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, mediaInfo = null) }
            val result = ytDlpRepository.fetchMediaInfo(url)
            result.fold(
                onSuccess = { info ->
                    val defaultQuality = if (info.availableQualities.contains(_uiState.value.selectedVideoQuality)) {
                        _uiState.value.selectedVideoQuality
                    } else {
                        info.availableQualities.lastOrNull() ?: VideoQuality.P720
                    }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            mediaInfo = info,
                            selectedVideoQuality = defaultQuality,
                            error = null
                        )
                    }
                },
                onFailure = { throwable ->
                    val mapped = (throwable as? MappedDownloadException)?.error
                        ?: DownloadError.ExtractionFailed(throwable.message ?: "Unknown error")
                    _uiState.update {
                        it.copy(isLoading = false, error = mapped)
                    }
                }
            )
        }
    }

    fun selectFormat(format: DownloadFormatType) {
        _uiState.update { it.copy(selectedFormat = format) }
    }

    fun selectVideoQuality(quality: VideoQuality) {
        _uiState.update { it.copy(selectedVideoQuality = quality) }
    }

    fun selectAudioBitrate(bitrate: AudioBitrate) {
        _uiState.update { it.copy(selectedAudioBitrate = bitrate) }
    }

    fun togglePlaylistEntry(entryId: String) {
        _uiState.update { state ->
            val info = state.mediaInfo ?: return@update state
            val updatedEntries = info.playlistEntries.map { entry ->
                if (entry.id == entryId) entry.copy(isSelected = !entry.isSelected) else entry
            }
            state.copy(mediaInfo = info.copy(playlistEntries = updatedEntries))
        }
    }

    fun setAllPlaylistSelected(selected: Boolean) {
        _uiState.update { state ->
            val info = state.mediaInfo ?: return@update state
            val updatedEntries = info.playlistEntries.map { it.copy(isSelected = selected) }
            state.copy(mediaInfo = info.copy(playlistEntries = updatedEntries))
        }
    }

    fun startDownload() {
        val state = _uiState.value
        val info = state.mediaInfo ?: return

        val requests = if (info.isPlaylist) {
            info.playlistEntries
                .filter { it.isSelected }
                .map { entry ->
                    DownloadRequest(
                        url = entry.url,
                        title = entry.title,
                        thumbnailUrl = entry.thumbnailUrl,
                        platform = info.platform,
                        formatType = state.selectedFormat,
                        videoQuality = state.selectedVideoQuality,
                        audioBitrate = state.selectedAudioBitrate
                    )
                }
        } else {
            listOf(
                DownloadRequest(
                    url = info.url,
                    title = info.title,
                    thumbnailUrl = info.thumbnailUrl,
                    platform = info.platform,
                    formatType = state.selectedFormat,
                    videoQuality = state.selectedVideoQuality,
                    audioBitrate = state.selectedAudioBitrate
                )
            )
        }

        if (requests.isEmpty()) return

        viewModelScope.launch {
            downloadRepository.enqueueDownloads(requests)
            _uiState.update { it.copy(queuedMessageVisible = true) }
        }
    }
}
