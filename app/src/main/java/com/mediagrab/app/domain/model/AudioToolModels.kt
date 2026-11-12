package com.mediagrab.app.domain.model

import java.util.Locale

enum class AudioOutputFormat(val ext: String, val ffmpegCodec: String, val mimeType: String) {
    MP3("mp3", "libmp3lame", "audio/mpeg"),
    M4A("m4a", "aac", "audio/mp4")
}

enum class MergeTransitionMode {
    NONE,
    CROSSFADE,
    SILENCE_GAP
}

data class AudioSourceItem(
    val uri: String,
    val title: String,
    val durationMs: Long
)

data class MergeAudioClip(
    val id: String,
    val uri: String,
    val title: String,
    val totalDurationMs: Long,
    val startMs: Long = 0L,
    val endMs: Long = totalDurationMs
)

sealed interface AudioProcessingState {
    data object Idle : AudioProcessingState
    data class Processing(val progressPercent: Int) : AudioProcessingState
    data class Success(val outputTitle: String) : AudioProcessingState
    data class Error(val message: String) : AudioProcessingState
    data object Cancelled : AudioProcessingState
}

object AudioTimeFormatter {
    fun formatMillis(ms: Long): String {
        val clamped = ms.coerceAtLeast(0L)
        val minutes = clamped / 60_000L
        val seconds = (clamped % 60_000L) / 1_000L
        val millis = clamped % 1_000L
        return String.format(Locale.US, "%02d:%02d.%03d", minutes, seconds, millis)
    }

    /**
     * Parses "mm:ss.SSS" or "mm:ss" into milliseconds. Returns null if invalid.
     */
    fun parseMillis(input: String): Long? {
        val trimmed = input.trim()
        val parts = trimmed.split(":")
        if (parts.size != 2) return null
        val minutes = parts[0].toLongOrNull() ?: return null
        if (minutes < 0) return null

        val secParts = parts[1].split(".")
        val seconds = secParts[0].toLongOrNull() ?: return null
        if (seconds !in 0..59) return null

        val millis = when (secParts.size) {
            1 -> 0L
            2 -> {
                val raw = secParts[1].padEnd(3, '0').take(3)
                raw.toLongOrNull() ?: return null
            }
            else -> return null
        }
        return (minutes * 60_000L) + (seconds * 1_000L) + millis
    }
}
