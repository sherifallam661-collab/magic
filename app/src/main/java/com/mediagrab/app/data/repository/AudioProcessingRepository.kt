package com.mediagrab.app.data.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.ReturnCode
import com.mediagrab.app.data.local.entity.MediaItemType
import com.mediagrab.app.data.preferences.SettingsPreferences
import com.mediagrab.app.domain.model.AudioOutputFormat
import com.mediagrab.app.domain.model.AudioProcessingState
import com.mediagrab.app.domain.model.MergeAudioClip
import com.mediagrab.app.domain.model.MergeTransitionMode
import com.mediagrab.app.util.MediaStoreHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class AudioProcessingRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaStoreHelper: MediaStoreHelper,
    private val downloadRepository: DownloadRepository,
    private val settingsPreferences: SettingsPreferences
) {
    private var activeSession: FFmpegSession? = null

    suspend fun getAudioDurationMs(uriString: String): Long = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, Uri.parse(uriString))
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    /**
     * Generates normalized [0f..1f] waveform bars using FFmpegKit PCM decoding on IO dispatcher.
     */
    suspend fun extractWaveform(uriString: String, sampleCount: Int = 80): List<Float> =
        withContext(Dispatchers.IO) {
            val tempInput = copyUriToTempFile(uriString, "wave_in") ?: return@withContext emptyList()
            val tempPcm = File(context.cacheDir, "wave_${System.currentTimeMillis()}.pcm")
            try {
                val cmd = "-y -i \"${tempInput.absolutePath}\" -ac 1 -ar 8000 -f s16le \"${tempPcm.absolutePath}\""
                val session = FFmpegKit.execute(cmd)
                if (!ReturnCode.isSuccess(session.returnCode) || !tempPcm.exists()) {
                    return@withContext List(sampleCount) { 0.35f }
                }
                val bytes = tempPcm.readBytes()
                val totalShorts = bytes.size / 2
                if (totalShorts == 0) return@withContext List(sampleCount) { 0.35f }

                val shortBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                val step = (totalShorts / sampleCount).coerceAtLeast(1)
                val peaks = FloatArray(sampleCount)
                var maxPeak = 1f

                for (i in 0 until sampleCount) {
                    val start = i * step
                    val end = (start + step).coerceAtMost(totalShorts)
                    var peak = 0
                    for (j in start until end) {
                        val amp = abs(shortBuffer.get(j).toInt())
                        if (amp > peak) peak = amp
                    }
                    peaks[i] = peak.toFloat()
                    if (peaks[i] > maxPeak) maxPeak = peaks[i]
                }

                peaks.map { (it / maxPeak).coerceIn(0.08f, 1f) }
            } finally {
                tempInput.delete()
                tempPcm.delete()
            }
        }

    fun trimAudio(
        uriString: String,
        outputTitle: String,
        startMs: Long,
        endMs: Long,
        fadeInSec: Float,
        fadeOutSec: Float,
        outputFormat: AudioOutputFormat
    ): Flow<AudioProcessingState> = callbackFlow {
        val tempInput = withContext(Dispatchers.IO) { copyUriToTempFile(uriString, "trim_in") }
        if (tempInput == null) {
            trySend(AudioProcessingState.Error("Unable to read source audio"))
            close()
            return@callbackFlow
        }

        val durationMs = (endMs - startMs).coerceAtLeast(100L)
        val outFile = File(
            context.cacheDir,
            "${sanitizeFileName(outputTitle)}_${System.currentTimeMillis()}.${outputFormat.ext}"
        )

        val command = buildTrimCommand(
            inputPath = tempInput.absolutePath,
            outputPath = outFile.absolutePath,
            startMs = startMs,
            endMs = endMs,
            fadeInSec = fadeInSec,
            fadeOutSec = fadeOutSec,
            outputFormat = outputFormat
        )

        trySend(AudioProcessingState.Processing(0))

        activeSession = FFmpegKit.executeAsync(
            command,
            { session ->
                when {
                    ReturnCode.isSuccess(session.returnCode) -> {
                        kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                            val subFolder = settingsPreferences.settings.first().defaultFolderName
                            val published = mediaStoreHelper.publishToMediaStore(
                                sourceFile = outFile,
                                displayName = outFile.name,
                                mimeType = outputFormat.mimeType,
                                isVideo = false,
                                subFolder = subFolder
                            )
                            if (published != null) {
                                downloadRepository.insertProcessedAudio(
                                    title = outputTitle,
                                    mediaType = MediaItemType.TRIMMED,
                                    formatExtension = outputFormat.ext,
                                    sizeBytes = published.sizeBytes,
                                    localUri = published.uri,
                                    filePath = published.filePath
                                )
                                trySend(AudioProcessingState.Success(outputTitle))
                            } else {
                                trySend(AudioProcessingState.Error("Failed to save trimmed audio"))
                            }
                        }
                    }
                    ReturnCode.isCancel(session.returnCode) -> {
                        outFile.delete()
                        trySend(AudioProcessingState.Cancelled)
                    }
                    else -> {
                        outFile.delete()
                        trySend(AudioProcessingState.Error(session.failStackTrace ?: "FFmpeg trim failed"))
                    }
                }
                tempInput.delete()
                activeSession = null
                close()
            },
            { _ -> },
            { stats ->
                val pct = ((stats.time.toFloat() / durationMs.toFloat()) * 100f)
                    .toInt()
                    .coerceIn(0, 99)
                trySend(AudioProcessingState.Processing(pct))
            }
        )

        awaitClose {
            activeSession?.let { FFmpegKit.cancel(it.sessionId) }
        }
    }

    fun mergeAudio(
        clips: List<MergeAudioClip>,
        outputTitle: String,
        transitionMode: MergeTransitionMode,
        transitionSeconds: Float,
        outputFormat: AudioOutputFormat
    ): Flow<AudioProcessingState> = callbackFlow {
        if (clips.size < 2) {
            trySend(AudioProcessingState.Error("Select at least 2 audio files"))
            close()
            return@callbackFlow
        }

        val tempInputs = withContext(Dispatchers.IO) {
            clips.mapIndexedNotNull { idx, clip ->
                copyUriToTempFile(clip.uri, "merge_in_$idx")
            }
        }
        if (tempInputs.size != clips.size) {
            tempInputs.forEach { it.delete() }
            trySend(AudioProcessingState.Error("Unable to read one or more audio files"))
            close()
            return@callbackFlow
        }

        val totalEstimatedMs = clips.sumOf { (it.endMs - it.startMs).coerceAtLeast(100L) }
        val outFile = File(
            context.cacheDir,
            "${sanitizeFileName(outputTitle)}_${System.currentTimeMillis()}.${outputFormat.ext}"
        )

        val command = buildMergeCommand(
            inputPaths = tempInputs.map { it.absolutePath },
            clips = clips,
            outputPath = outFile.absolutePath,
            transitionMode = transitionMode,
            transitionSeconds = transitionSeconds,
            outputFormat = outputFormat
        )

        trySend(AudioProcessingState.Processing(0))

        activeSession = FFmpegKit.executeAsync(
            command,
            { session ->
                when {
                    ReturnCode.isSuccess(session.returnCode) -> {
                        kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                            val subFolder = settingsPreferences.settings.first().defaultFolderName
                            val published = mediaStoreHelper.publishToMediaStore(
                                sourceFile = outFile,
                                displayName = outFile.name,
                                mimeType = outputFormat.mimeType,
                                isVideo = false,
                                subFolder = subFolder
                            )
                            if (published != null) {
                                downloadRepository.insertProcessedAudio(
                                    title = outputTitle,
                                    mediaType = MediaItemType.MERGED,
                                    formatExtension = outputFormat.ext,
                                    sizeBytes = published.sizeBytes,
                                    localUri = published.uri,
                                    filePath = published.filePath
                                )
                                trySend(AudioProcessingState.Success(outputTitle))
                            } else {
                                trySend(AudioProcessingState.Error("Failed to save merged audio"))
                            }
                        }
                    }
                    ReturnCode.isCancel(session.returnCode) -> {
                        outFile.delete()
                        trySend(AudioProcessingState.Cancelled)
                    }
                    else -> {
                        outFile.delete()
                        trySend(AudioProcessingState.Error(session.failStackTrace ?: "FFmpeg merge failed"))
                    }
                }
                tempInputs.forEach { it.delete() }
                activeSession = null
                close()
            },
            { _ -> },
            { stats ->
                val pct = ((stats.time.toFloat() / totalEstimatedMs.toFloat()) * 100f)
                    .toInt()
                    .coerceIn(0, 99)
                trySend(AudioProcessingState.Processing(pct))
            }
        )

        awaitClose {
            activeSession?.let { FFmpegKit.cancel(it.sessionId) }
        }
    }

    fun cancelActiveProcessing() {
        activeSession?.let { FFmpegKit.cancel(it.sessionId) }
    }

    internal fun buildTrimCommand(
        inputPath: String,
        outputPath: String,
        startMs: Long,
        endMs: Long,
        fadeInSec: Float,
        fadeOutSec: Float,
        outputFormat: AudioOutputFormat
    ): String {
        val startSec = startMs / 1000.0
        val durationSec = ((endMs - startMs).coerceAtLeast(100L)) / 1000.0
        val filters = mutableListOf<String>()

        if (fadeInSec > 0f) {
            filters.add(String.format(Locale.US, "afade=t=in:st=0:d=%.2f", fadeInSec))
        }
        if (fadeOutSec > 0f && durationSec > fadeOutSec) {
            val fadeOutStart = (durationSec - fadeOutSec).coerceAtLeast(0.0)
            filters.add(String.format(Locale.US, "afade=t=out:st=%.2f:d=%.2f", fadeOutStart, fadeOutSec))
        }

        val filterArg = if (filters.isNotEmpty()) "-af \"${filters.joinToString(",")}\"" else ""
        return String.format(
            Locale.US,
            "-y -ss %.3f -t %.3f -i \"%s\" %s -c:a %s -b:a 192k \"%s\"",
            startSec,
            durationSec,
            inputPath,
            filterArg,
            outputFormat.ffmpegCodec,
            outputPath
        ).replace("  ", " ")
    }

    internal fun buildMergeCommand(
        inputPaths: List<String>,
        clips: List<MergeAudioClip>,
        outputPath: String,
        transitionMode: MergeTransitionMode,
        transitionSeconds: Float,
        outputFormat: AudioOutputFormat
    ): String {
        val inputsArg = inputPaths.joinToString(" ") { "-i \"$it\"" }
        val filterParts = mutableListOf<String>()

        // Trim each clip first
        clips.forEachIndexed { idx, clip ->
            val startSec = clip.startMs / 1000.0
            val endSec = clip.endMs / 1000.0
            filterParts.add(
                String.format(
                    Locale.US,
                    "[%d:a]atrim=start=%.3f:end=%.3f,asetpts=PTS-STARTPTS[a%d]",
                    idx,
                    startSec,
                    endSec,
                    idx
                )
            )
        }

        val finalLabel: String
        when (transitionMode) {
            MergeTransitionMode.CROSSFADE -> {
                var currentLabel = "[a0]"
                for (i in 1 until clips.size) {
                    val outLabel = if (i == clips.lastIndex) "[aout]" else "[cf$i]"
                    filterParts.add(
                        String.format(
                            Locale.US,
                            "%s[a%d]acrossfade=d=%.2f:c1=tri:c2=tri%s",
                            currentLabel,
                            i,
                            transitionSeconds.coerceAtLeast(0.2f),
                            outLabel
                        )
                    )
                    currentLabel = outLabel
                }
                finalLabel = "[aout]"
            }
            MergeTransitionMode.SILENCE_GAP -> {
                val gapSec = transitionSeconds.coerceAtLeast(0.2f)
                val concatInputs = buildString {
                    clips.indices.forEach { idx ->
                        append("[a$idx]")
                        if (idx < clips.lastIndex) {
                            filterParts.add(
                                String.format(
                                    Locale.US,
                                    "anullsrc=r=44100:cl=stereo,atrim=duration=%.2f[gap%d]",
                                    gapSec,
                                    idx
                                )
                            )
                            append("[gap$idx]")
                        }
                    }
                }
                val totalSegments = clips.size + (clips.size - 1)
                filterParts.add("${concatInputs}concat=n=$totalSegments:v=0:a=1[aout]")
                finalLabel = "[aout]"
            }
            MergeTransitionMode.NONE -> {
                val concatInputs = clips.indices.joinToString("") { "[a$it]" }
                filterParts.add("${concatInputs}concat=n=${clips.size}:v=0:a=1[aout]")
                finalLabel = "[aout]"
            }
        }

        val filterComplex = filterParts.joinToString(";")
        return "-y $inputsArg -filter_complex \"$filterComplex\" -map \"$finalLabel\" -c:a ${outputFormat.ffmpegCodec} -b:a 192k \"$outputPath\""
    }

    private fun copyUriToTempFile(uriString: String, prefix: String): File? {
        return runCatching {
            val uri = Uri.parse(uriString)
            val tempFile = File(context.cacheDir, "${prefix}_${System.nanoTime()}.tmp")
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { out -> input.copyTo(out) }
            } ?: return null
            tempFile
        }.getOrNull()
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9_\\-\\u0600-\\u06FF ]"), "_")
            .trim()
            .ifBlank { "mediagrab_audio" }
    }
}
