package com.mediagrab.app.ui.audiotools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediagrab.app.data.local.entity.DownloadEntity
import com.mediagrab.app.data.local.entity.DownloadStatus
import com.mediagrab.app.data.local.entity.MediaItemType
import com.mediagrab.app.data.repository.AudioProcessingRepository
import com.mediagrab.app.data.repository.DownloadRepository
import com.mediagrab.app.domain.model.AudioOutputFormat
import com.mediagrab.app.domain.model.AudioProcessingState
import com.mediagrab.app.domain.model.AudioSourceItem
import com.mediagrab.app.domain.model.AudioTimeFormatter
import com.mediagrab.app.domain.model.MergeAudioClip
import com.mediagrab.app.domain.model.MergeTransitionMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class AudioToolsUiState(
    // Trim state
    val trimSource: AudioSourceItem? = null,
    val waveform: List<Float> = emptyList(),
    val isLoadingWaveform: Boolean = false,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val startTimeText: String = "00:00.000",
    val endTimeText: String = "00:00.000",
    val fadeInSec: Float = 0f,
    val fadeOutSec: Float = 0f,
    val trimOutputFormat: AudioOutputFormat = AudioOutputFormat.MP3,

    // Merge state
    val mergeClips: List<MergeAudioClip> = emptyList(),
    val transitionMode: MergeTransitionMode = MergeTransitionMode.CROSSFADE,
    val transitionSeconds: Float = 1.5f,
    val mergeOutputFormat: AudioOutputFormat = AudioOutputFormat.MP3,

    // Shared processing state
    val processingState: AudioProcessingState = AudioProcessingState.Idle
)

@HiltViewModel
class AudioToolsViewModel @Inject constructor(
    private val audioRepository: AudioProcessingRepository,
    downloadRepository: DownloadRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AudioToolsUiState())
    val uiState: StateFlow<AudioToolsUiState> = _uiState.asStateFlow()

    val downloadedAudioItems: StateFlow<List<DownloadEntity>> =
        downloadRepository.observeDownloads()
            .map { list ->
                list.filter {
                    it.status == DownloadStatus.COMPLETED &&
                        it.mediaType != MediaItemType.VIDEO &&
                        !it.localUri.isNullOrBlank()
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    private var processingJob: Job? = null

    fun selectTrimSource(uri: String, title: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingWaveform = true, processingState = AudioProcessingState.Idle) }
            val durationMs = audioRepository.getAudioDurationMs(uri).coerceAtLeast(1_000L)
            val waveform = audioRepository.extractWaveform(uri)
            _uiState.update {
                it.copy(
                    trimSource = AudioSourceItem(uri = uri, title = title, durationMs = durationMs),
                    waveform = waveform,
                    isLoadingWaveform = false,
                    trimStartMs = 0L,
                    trimEndMs = durationMs,
                    startTimeText = AudioTimeFormatter.formatMillis(0L),
                    endTimeText = AudioTimeFormatter.formatMillis(durationMs)
                )
            }
        }
    }

    fun updateTrimRange(newStartMs: Long, newEndMs: Long) {
        val total = _uiState.value.trimSource?.durationMs ?: return
        val clampedStart = newStartMs.coerceIn(0L, (total - 100L).coerceAtLeast(0L))
        val clampedEnd = newEndMs.coerceIn((clampedStart + 100L).coerceAtMost(total), total)
        _uiState.update {
            it.copy(
                trimStartMs = clampedStart,
                trimEndMs = clampedEnd,
                startTimeText = AudioTimeFormatter.formatMillis(clampedStart),
                endTimeText = AudioTimeFormatter.formatMillis(clampedEnd)
            )
        }
    }

    fun onManualStartTimeChanged(text: String) {
        _uiState.update { it.copy(startTimeText = text) }
        val parsed = AudioTimeFormatter.parseMillis(text) ?: return
        val end = _uiState.value.trimEndMs
        if (parsed in 0 until end) {
            _uiState.update { it.copy(trimStartMs = parsed) }
        }
    }

    fun onManualEndTimeChanged(text: String) {
        _uiState.update { it.copy(endTimeText = text) }
        val parsed = AudioTimeFormatter.parseMillis(text) ?: return
        val total = _uiState.value.trimSource?.durationMs ?: return
        val start = _uiState.value.trimStartMs
        if (parsed in (start + 100L)..total) {
            _uiState.update { it.copy(trimEndMs = parsed) }
        }
    }

    fun setFadeIn(seconds: Float) {
        _uiState.update { it.copy(fadeInSec = seconds.coerceIn(0f, 5f)) }
    }

    fun setFadeOut(seconds: Float) {
        _uiState.update { it.copy(fadeOutSec = seconds.coerceIn(0f, 5f)) }
    }

    fun setTrimFormat(format: AudioOutputFormat) {
        _uiState.update { it.copy(trimOutputFormat = format) }
    }

    fun executeTrim(outputTitle: String) {
        val state = _uiState.value
        val source = state.trimSource ?: return
        processingJob?.cancel()
        processingJob = viewModelScope.launch {
            audioRepository.trimAudio(
                uriString = source.uri,
                outputTitle = outputTitle.ifBlank { "${source.title}_trimmed" },
                startMs = state.trimStartMs,
                endMs = state.trimEndMs,
                fadeInSec = state.fadeInSec,
                fadeOutSec = state.fadeOutSec,
                outputFormat = state.trimOutputFormat
            ).collect { progress ->
                _uiState.update { it.copy(processingState = progress) }
            }
        }
    }

    // --- Merge Operations ---

    fun addMergeClip(uri: String, title: String) {
        viewModelScope.launch {
            val durationMs = audioRepository.getAudioDurationMs(uri).coerceAtLeast(1_000L)
            val clip = MergeAudioClip(
                id = UUID.randomUUID().toString(),
                uri = uri,
                title = title,
                totalDurationMs = durationMs,
                startMs = 0L,
                endMs = durationMs
            )
            _uiState.update {
                it.copy(
                    mergeClips = it.mergeClips + clip,
                    processingState = AudioProcessingState.Idle
                )
            }
        }
    }

    fun moveMergeClip(fromIndex: Int, toIndex: Int) {
        _uiState.update { state ->
            val list = state.mergeClips.toMutableList()
            if (fromIndex !in list.indices || toIndex !in list.indices) return@update state
            val item = list.removeAt(fromIndex)
            list.add(toIndex, item)
            state.copy(mergeClips = list)
        }
    }

    fun removeMergeClip(id: String) {
        _uiState.update { state ->
            state.copy(mergeClips = state.mergeClips.filterNot { it.id == id })
        }
    }

    fun updateMergeClipRange(id: String, startMs: Long, endMs: Long) {
        _uiState.update { state ->
            val updated = state.mergeClips.map { clip ->
                if (clip.id == id) {
                    val s = startMs.coerceIn(0L, (clip.totalDurationMs - 100L).coerceAtLeast(0L))
                    val e = endMs.coerceIn((s + 100L).coerceAtMost(clip.totalDurationMs), clip.totalDurationMs)
                    clip.copy(startMs = s, endMs = e)
                } else {
                    clip
                }
            }
            state.copy(mergeClips = updated)
        }
    }

    fun setTransitionMode(mode: MergeTransitionMode) {
        _uiState.update { it.copy(transitionMode = mode) }
    }

    fun setTransitionSeconds(seconds: Float) {
        _uiState.update { it.copy(transitionSeconds = seconds.coerceIn(0.2f, 5f)) }
    }

    fun setMergeFormat(format: AudioOutputFormat) {
        _uiState.update { it.copy(mergeOutputFormat = format) }
    }

    fun executeMerge(outputTitle: String) {
        val state = _uiState.value
        if (state.mergeClips.size < 2) return
        processingJob?.cancel()
        processingJob = viewModelScope.launch {
            audioRepository.mergeAudio(
                clips = state.mergeClips,
                outputTitle = outputTitle.ifBlank { "Merged_Audio" },
                transitionMode = state.transitionMode,
                transitionSeconds = state.transitionSeconds,
                outputFormat = state.mergeOutputFormat
            ).collect { progress ->
                _uiState.update { it.copy(processingState = progress) }
            }
        }
    }

    fun cancelProcessing() {
        audioRepository.cancelActiveProcessing()
        processingJob?.cancel()
        _uiState.update { it.copy(processingState = AudioProcessingState.Cancelled) }
    }

    fun resetProcessingState() {
        _uiState.update { it.copy(processingState = AudioProcessingState.Idle) }
    }
}
