package com.mediagrab.app.ui.audiotools

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MergeType
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.mediagrab.app.R
import com.mediagrab.app.data.local.entity.DownloadEntity
import com.mediagrab.app.domain.model.AudioOutputFormat
import com.mediagrab.app.domain.model.AudioProcessingState
import com.mediagrab.app.domain.model.AudioTimeFormatter
import com.mediagrab.app.domain.model.MergeAudioClip
import com.mediagrab.app.domain.model.MergeTransitionMode

private enum class AudioToolMode { TRIM, MERGE }

@Composable
fun AudioToolsScreen(
    modifier: Modifier = Modifier,
    viewModel: AudioToolsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val downloadedAudio by viewModel.downloadedAudioItems.collectAsStateWithLifecycle()

    var mode by rememberSaveable { mutableStateOf(AudioToolMode.TRIM) }
    var showDownloadsPicker by remember { mutableStateOf(false) }

    val deviceAudioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val title = it.lastPathSegment?.substringAfterLast('/') ?: "Audio"
            if (mode == AudioToolMode.TRIM) {
                viewModel.selectTrimSource(it.toString(), title)
            } else {
                viewModel.addMergeClip(it.toString(), title)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = mode == AudioToolMode.TRIM,
                onClick = { mode = AudioToolMode.TRIM },
                shape = SegmentedButtonDefaults.itemShape(0, 2)
            ) {
                Text(stringResource(R.string.audio_trim_tab))
            }
            SegmentedButton(
                selected = mode == AudioToolMode.MERGE,
                onClick = { mode = AudioToolMode.MERGE },
                shape = SegmentedButtonDefaults.itemShape(1, 2)
            ) {
                Text(stringResource(R.string.audio_merge_tab))
            }
        }

        // Source pickers
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { showDownloadsPicker = true },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.AudioFile, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.pick_from_downloads), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(
                onClick = { deviceAudioPicker.launch(arrayOf("audio/*")) },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.pick_from_device), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        ProcessingStatusBanner(
            processingState = state.processingState,
            onCancel = viewModel::cancelProcessing,
            onDismiss = viewModel::resetProcessingState
        )

        if (mode == AudioToolMode.TRIM) {
            TrimAudioSection(state = state, viewModel = viewModel)
        } else {
            MergeAudioSection(state = state, viewModel = viewModel)
        }
    }

    if (showDownloadsPicker) {
        DownloadsAudioPickerDialog(
            items = downloadedAudio,
            onSelect = { entity ->
                entity.localUri?.let { uri ->
                    if (mode == AudioToolMode.TRIM) {
                        viewModel.selectTrimSource(uri, entity.title)
                    } else {
                        viewModel.addMergeClip(uri, entity.title)
                    }
                }
                showDownloadsPicker = false
            },
            onDismiss = { showDownloadsPicker = false }
        )
    }
}

@Composable
private fun TrimAudioSection(
    state: AudioToolsUiState,
    viewModel: AudioToolsViewModel
) {
    val source = state.trimSource
    if (source == null) {
        Text(
            text = stringResource(R.string.trim_select_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    val context = LocalContext.current
    val previewPlayer = remember(source.uri) { ExoPlayer.Builder(context).build() }
    var isPreviewPlaying by remember { mutableStateOf(false) }

    DisposableEffect(previewPlayer) {
        onDispose { previewPlayer.release() }
    }

    var outputTitle by rememberSaveable(source.uri) {
        mutableStateOf("${source.title}_trimmed")
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = source.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (state.isLoadingWaveform) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                WaveformSelector(
                    amplitudes = state.waveform,
                    durationMs = source.durationMs,
                    startMs = state.trimStartMs,
                    endMs = state.trimEndMs,
                    onRangeChanged = viewModel::updateTrimRange
                )
            }

            // Manual time entry (mm:ss.SSS)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = state.startTimeText,
                    onValueChange = viewModel::onManualStartTimeChanged,
                    label = { Text(stringResource(R.string.start_time_label)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = state.endTimeText,
                    onValueChange = viewModel::onManualEndTimeChanged,
                    label = { Text(stringResource(R.string.end_time_label)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            // Preview clipped segment
            OutlinedButton(
                onClick = {
                    if (isPreviewPlaying) {
                        previewPlayer.pause()
                        isPreviewPlaying = false
                    } else {
                        val clippedItem = MediaItem.Builder()
                            .setUri(Uri.parse(source.uri))
                            .setClippingConfiguration(
                                MediaItem.ClippingConfiguration.Builder()
                                    .setStartPositionMs(state.trimStartMs)
                                    .setEndPositionMs(state.trimEndMs)
                                    .build()
                            )
                            .build()
                        previewPlayer.setMediaItem(clippedItem)
                        previewPlayer.prepare()
                        previewPlayer.play()
                        isPreviewPlaying = true
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = if (isPreviewPlaying) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    if (isPreviewPlaying) stringResource(R.string.stop_preview)
                    else stringResource(R.string.preview_selection)
                )
            }

            // Fade In / Fade Out
            Column {
                Text(
                    text = stringResource(R.string.fade_in_sec, state.fadeInSec),
                    style = MaterialTheme.typography.bodySmall
                )
                Slider(
                    value = state.fadeInSec,
                    onValueChange = viewModel::setFadeIn,
                    valueRange = 0f..5f
                )
                Text(
                    text = stringResource(R.string.fade_out_sec, state.fadeOutSec),
                    style = MaterialTheme.typography.bodySmall
                )
                Slider(
                    value = state.fadeOutSec,
                    onValueChange = viewModel::setFadeOut,
                    valueRange = 0f..5f
                )
            }

            // Output format
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AudioOutputFormat.entries.forEach { format ->
                    FilterChip(
                        selected = state.trimOutputFormat == format,
                        onClick = { viewModel.setTrimFormat(format) },
                        label = { Text(format.name) }
                    )
                }
            }

            OutlinedTextField(
                value = outputTitle,
                onValueChange = { outputTitle = it },
                label = { Text(stringResource(R.string.output_file_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = { viewModel.executeTrim(outputTitle) },
                enabled = state.processingState !is AudioProcessingState.Processing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Icon(Icons.Rounded.ContentCut, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.trim_and_save))
            }
        }
    }
}

@Composable
private fun MergeAudioSection(
    state: AudioToolsUiState,
    viewModel: AudioToolsViewModel
) {
    var outputTitle by rememberSaveable { mutableStateOf("Merged_Audio") }
    var editingClip by remember { mutableStateOf<MergeAudioClip?>(null) }

    if (state.mergeClips.isEmpty()) {
        Text(
            text = stringResource(R.string.merge_select_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    state.mergeClips.forEachIndexed { index, clip ->
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${index + 1}. ${clip.title}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${AudioTimeFormatter.formatMillis(clip.startMs)} → ${AudioTimeFormatter.formatMillis(clip.endMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = { editingClip = clip }) {
                    Icon(Icons.Rounded.ContentCut, contentDescription = stringResource(R.string.trim_clip))
                }
                IconButton(
                    onClick = { viewModel.moveMergeClip(index, index - 1) },
                    enabled = index > 0
                ) {
                    Icon(Icons.Rounded.ArrowUpward, contentDescription = null)
                }
                IconButton(
                    onClick = { viewModel.moveMergeClip(index, index + 1) },
                    enabled = index < state.mergeClips.lastIndex
                ) {
                    Icon(Icons.Rounded.ArrowDownward, contentDescription = null)
                }
                IconButton(onClick = { viewModel.removeMergeClip(clip.id) }) {
                    Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.delete))
                }
            }
        }
    }

    Text(
        text = stringResource(R.string.transition_mode),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = state.transitionMode == MergeTransitionMode.NONE,
            onClick = { viewModel.setTransitionMode(MergeTransitionMode.NONE) },
            label = { Text(stringResource(R.string.transition_none)) }
        )
        FilterChip(
            selected = state.transitionMode == MergeTransitionMode.CROSSFADE,
            onClick = { viewModel.setTransitionMode(MergeTransitionMode.CROSSFADE) },
            label = { Text(stringResource(R.string.transition_crossfade)) }
        )
        FilterChip(
            selected = state.transitionMode == MergeTransitionMode.SILENCE_GAP,
            onClick = { viewModel.setTransitionMode(MergeTransitionMode.SILENCE_GAP) },
            label = { Text(stringResource(R.string.transition_gap)) }
        )
    }

    if (state.transitionMode != MergeTransitionMode.NONE) {
        Text(
            text = stringResource(R.string.transition_duration, state.transitionSeconds),
            style = MaterialTheme.typography.bodySmall
        )
        Slider(
            value = state.transitionSeconds,
            onValueChange = viewModel::setTransitionSeconds,
            valueRange = 0.5f..5f
        )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AudioOutputFormat.entries.forEach { format ->
            FilterChip(
                selected = state.mergeOutputFormat == format,
                onClick = { viewModel.setMergeFormat(format) },
                label = { Text(format.name) }
            )
        }
    }

    OutlinedTextField(
        value = outputTitle,
        onValueChange = { outputTitle = it },
        label = { Text(stringResource(R.string.output_file_name)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    Button(
        onClick = { viewModel.executeMerge(outputTitle) },
        enabled = state.mergeClips.size >= 2 && state.processingState !is AudioProcessingState.Processing,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
    ) {
        Icon(Icons.Rounded.MergeType, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(stringResource(R.string.merge_and_save))
    }

    editingClip?.let { clip ->
        ClipTrimDialog(
            clip = clip,
            onSave = { s, e ->
                viewModel.updateMergeClipRange(clip.id, s, e)
                editingClip = null
            },
            onDismiss = { editingClip = null }
        )
    }
}

@Composable
private fun ProcessingStatusBanner(
    processingState: AudioProcessingState,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    when (processingState) {
        AudioProcessingState.Idle -> Unit
        is AudioProcessingState.Processing -> {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.processing_audio, processingState.progressPercent))
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cancel))
                        }
                    }
                    LinearProgressIndicator(
                        progress = { processingState.progressPercent / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        is AudioProcessingState.Success -> {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.audio_saved_to_history, processingState.outputTitle),
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.close))
                    }
                }
            }
        }
        is AudioProcessingState.Error -> {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = processingState.message,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.close))
                    }
                }
            }
        }
        AudioProcessingState.Cancelled -> {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.processing_cancelled))
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.close))
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadsAudioPickerDialog(
    items: List<DownloadEntity>,
    onSelect: (DownloadEntity) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pick_from_downloads)) },
        text = {
            if (items.isEmpty()) {
                Text(stringResource(R.string.no_audio_downloads))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items.take(15).forEach { item ->
                        TextButton(
                            onClick = { onSelect(item) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = item.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

@Composable
private fun ClipTrimDialog(
    clip: MergeAudioClip,
    onSave: (Long, Long) -> Unit,
    onDismiss: () -> Unit
) {
    var startText by remember { mutableStateOf(AudioTimeFormatter.formatMillis(clip.startMs)) }
    var endText by remember { mutableStateOf(AudioTimeFormatter.formatMillis(clip.endMs)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(clip.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = startText,
                    onValueChange = { startText = it },
                    label = { Text(stringResource(R.string.start_time_label)) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = endText,
                    onValueChange = { endText = it },
                    label = { Text(stringResource(R.string.end_time_label)) },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val s = AudioTimeFormatter.parseMillis(startText) ?: clip.startMs
                    val e = AudioTimeFormatter.parseMillis(endText) ?: clip.endMs
                    onSave(s, e)
                }
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
