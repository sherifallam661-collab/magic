package com.mediagrab.app.ui.download

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.mediagrab.app.R
import com.mediagrab.app.domain.model.AudioBitrate
import com.mediagrab.app.domain.model.DownloadError
import com.mediagrab.app.domain.model.DownloadFormatType
import com.mediagrab.app.domain.model.MediaInfo
import com.mediagrab.app.domain.model.PlaylistEntry
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DownloadScreen(
    modifier: Modifier = Modifier,
    viewModel: DownloadViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.checkClipboard()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Clipboard detection banner
        AnimatedVisibility(visible = state.clipboardUrl != null) {
            AssistChip(
                onClick = viewModel::applyClipboardUrl,
                label = { Text(stringResource(R.string.clipboard_detected)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.ContentPaste,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // URL Input Field
        OutlinedTextField(
            value = state.urlInput,
            onValueChange = viewModel::onUrlChanged,
            label = { Text(stringResource(R.string.url_hint)) },
            leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null) },
            trailingIcon = {
                Row {
                    if (state.urlInput.isNotEmpty()) {
                        IconButton(onClick = viewModel::clearInput) {
                            Icon(
                                imageVector = Icons.Rounded.Clear,
                                contentDescription = stringResource(R.string.clear)
                            )
                        }
                    } else {
                        IconButton(onClick = {
                            viewModel.checkClipboard()
                            viewModel.applyClipboardUrl()
                        }) {
                            Icon(
                                imageVector = Icons.Rounded.ContentPaste,
                                contentDescription = stringResource(R.string.paste)
                            )
                        }
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Search
            ),
            keyboardActions = KeyboardActions(
                onSearch = { viewModel.fetchMetadata() }
            ),
            modifier = Modifier.fillMaxWidth()
        )

        // Fetch Info Button
        Button(
            onClick = viewModel::fetchMetadata,
            enabled = !state.isLoading && state.urlInput.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
            } else {
                Icon(Icons.Rounded.Search, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(stringResource(R.string.fetch_info))
        }

        // Error Card
        state.error?.let { error ->
            ErrorBanner(
                error = error,
                onRetry = viewModel::fetchMetadata
            )
        }

        // Queued confirmation
        AnimatedVisibility(visible = state.queuedMessageVisible) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.download_queued),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }

        // Media Preview + Options
        state.mediaInfo?.let { info ->
            MediaPreviewCard(info = info)

            if (info.isPlaylist && info.playlistEntries.isNotEmpty()) {
                PlaylistSelectionCard(
                    entries = info.playlistEntries,
                    onToggleEntry = viewModel::togglePlaylistEntry,
                    onSelectAll = { viewModel.setAllPlaylistSelected(true) },
                    onDeselectAll = { viewModel.setAllPlaylistSelected(false) }
                )
            }

            // Format Selector (MP4 / MP3 / M4A)
            Text(
                text = stringResource(R.string.format_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val formats = listOf(
                    DownloadFormatType.VIDEO_MP4 to stringResource(R.string.format_video_mp4),
                    DownloadFormatType.AUDIO_MP3 to stringResource(R.string.format_audio_mp3),
                    DownloadFormatType.AUDIO_M4A to stringResource(R.string.format_audio_m4a)
                )
                formats.forEachIndexed { index, (format, label) ->
                    SegmentedButton(
                        selected = state.selectedFormat == format,
                        onClick = { viewModel.selectFormat(format) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = formats.size
                        )
                    ) {
                        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            // Quality / Bitrate Chips
            if (state.selectedFormat == DownloadFormatType.VIDEO_MP4) {
                Text(
                    text = stringResource(R.string.quality_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    info.availableQualities.forEach { quality ->
                        FilterChip(
                            selected = state.selectedVideoQuality == quality,
                            onClick = { viewModel.selectVideoQuality(quality) },
                            label = { Text(quality.label) }
                        )
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.bitrate_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AudioBitrate.entries.forEach { bitrate ->
                        FilterChip(
                            selected = state.selectedAudioBitrate == bitrate,
                            onClick = { viewModel.selectAudioBitrate(bitrate) },
                            label = { Text(bitrate.label) }
                        )
                    }
                }
            }

            val selectedCount = if (info.isPlaylist) {
                info.playlistEntries.count { it.isSelected }
            } else {
                1
            }

            Button(
                onClick = viewModel::startDownload,
                enabled = selectedCount > 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(Icons.Rounded.Download, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (info.isPlaylist) {
                        "${stringResource(R.string.start_download)} ($selectedCount)"
                    } else {
                        stringResource(R.string.start_download)
                    }
                )
            }
        }
    }
}

@Composable
private fun MediaPreviewCard(info: MediaInfo) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!info.thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = info.thumbnailUrl,
                    contentDescription = info.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(12.dp))
                )
            }
            Text(
                text = info.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SuggestionChip(
                    onClick = {},
                    label = { Text(info.platform.displayName) }
                )
                if (info.durationSeconds > 0) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text(formatDuration(info.durationSeconds)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistSelectionCard(
    entries: List<PlaylistEntry>,
    onToggleEntry: (String) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.playlist_items, entries.size),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Row {
                    TextButton(onClick = onSelectAll) {
                        Text(stringResource(R.string.select_all))
                    }
                    TextButton(onClick = onDeselectAll) {
                        Text(stringResource(R.string.deselect_all))
                    }
                }
            }
            HorizontalDivider()
            entries.forEach { entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = entry.isSelected,
                        onCheckedChange = { onToggleEntry(entry.id) }
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.title,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (entry.durationSeconds > 0) {
                            Text(
                                text = formatDuration(entry.durationSeconds),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorBanner(
    error: DownloadError,
    onRetry: () -> Unit
) {
    val message = when (error) {
        DownloadError.InvalidUrl -> stringResource(R.string.error_invalid_url)
        DownloadError.NoInternet -> stringResource(R.string.error_no_internet)
        DownloadError.PrivateOrUnavailable -> stringResource(R.string.error_private_content)
        DownloadError.InsufficientStorage -> stringResource(R.string.error_insufficient_storage)
        is DownloadError.ExtractionFailed -> stringResource(R.string.error_extraction_failed, error.detail)
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.retry))
            }
        }
    }
}

private fun formatDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
