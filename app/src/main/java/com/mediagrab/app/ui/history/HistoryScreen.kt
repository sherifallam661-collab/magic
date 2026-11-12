package com.mediagrab.app.ui.history

import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.mediagrab.app.R
import com.mediagrab.app.data.local.entity.DownloadEntity
import com.mediagrab.app.data.local.entity.DownloadStatus
import com.mediagrab.app.data.local.entity.MediaItemType
import com.mediagrab.app.ui.player.MediaPlayerDialog
import java.text.DateFormat
import java.util.Date

@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var playingItem by remember { mutableStateOf<DownloadEntity?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }
    var deleteSelectedDialog by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Multi-select action bar OR Search + Sort + Clear bar
        if (state.isSelectionMode) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = viewModel::clearSelection) {
                            Icon(Icons.Rounded.Close, contentDescription = null)
                        }
                        Text(
                            text = stringResource(R.string.selected_count, state.selectedIds.size),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    IconButton(onClick = { deleteSelectedDialog = true }) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = stringResource(R.string.delete)
                        )
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = viewModel::onSearchQueryChanged,
                    placeholder = { Text(stringResource(R.string.search_history)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )

                Box {
                    IconButton(onClick = { sortMenuExpanded = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Sort,
                            contentDescription = stringResource(R.string.sort)
                        )
                    }
                    DropdownMenu(
                        expanded = sortMenuExpanded,
                        onDismissRequest = { sortMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sort_newest)) },
                            onClick = {
                                viewModel.onSortOrderChanged(HistorySortOrder.NEWEST)
                                sortMenuExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sort_size)) },
                            onClick = {
                                viewModel.onSortOrderChanged(HistorySortOrder.SIZE_DESC)
                                sortMenuExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sort_name)) },
                            onClick = {
                                viewModel.onSortOrderChanged(HistorySortOrder.NAME_ASC)
                                sortMenuExpanded = false
                            }
                        )
                    }
                }

                if (state.items.isNotEmpty()) {
                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteSweep,
                            contentDescription = stringResource(R.string.clear_history)
                        )
                    }
                }
            }
        }

        // Filter Chips (All / Video / Audio / Platforms)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = state.filterType == HistoryFilterType.ALL && state.selectedPlatform == null,
                onClick = {
                    viewModel.onFilterTypeChanged(HistoryFilterType.ALL)
                    viewModel.onPlatformSelected(null)
                },
                label = { Text(stringResource(R.string.filter_all)) }
            )
            FilterChip(
                selected = state.filterType == HistoryFilterType.VIDEO,
                onClick = { viewModel.onFilterTypeChanged(HistoryFilterType.VIDEO) },
                label = { Text(stringResource(R.string.filter_video)) }
            )
            FilterChip(
                selected = state.filterType == HistoryFilterType.AUDIO,
                onClick = { viewModel.onFilterTypeChanged(HistoryFilterType.AUDIO) },
                label = { Text(stringResource(R.string.filter_audio)) }
            )
            state.availablePlatforms.forEach { platform ->
                FilterChip(
                    selected = state.selectedPlatform == platform,
                    onClick = {
                        viewModel.onPlatformSelected(
                            if (state.selectedPlatform == platform) null else platform
                        )
                    },
                    label = { Text(platform) }
                )
            }
        }

        if (state.items.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.history_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(state.items, key = { it.id }) { item ->
                    HistoryItemCard(
                        item = item,
                        isSelected = item.id in state.selectedIds,
                        isSelectionMode = state.isSelectionMode,
                        onToggleSelect = { viewModel.toggleSelection(item.id) },
                        onPlay = { playingItem = item },
                        onShare = {
                            item.localUri?.let { uriStr ->
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = if (item.mediaType == MediaItemType.VIDEO) "video/*" else "audio/*"
                                    putExtra(Intent.EXTRA_STREAM, Uri.parse(uriStr))
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(
                                    Intent.createChooser(shareIntent, item.title)
                                )
                            }
                        },
                        onOpenFolder = {
                            item.localUri?.let { uriStr ->
                                val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(
                                        Uri.parse(uriStr),
                                        if (item.mediaType == MediaItemType.VIDEO) "video/*" else "audio/*"
                                    )
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                runCatching { context.startActivity(viewIntent) }
                            }
                        },
                        onPause = { viewModel.pauseDownload(item.id) },
                        onResumeOrRetry = { viewModel.resumeOrRetry(item.id) },
                        onCancel = { viewModel.cancelDownload(item.id) },
                        onDelete = { viewModel.deleteSingle(item.id, deleteFileFromDevice = true) }
                    )
                }
            }
        }
    }

    playingItem?.let { item ->
        val uri = item.localUri
        if (!uri.isNullOrBlank()) {
            MediaPlayerDialog(
                title = item.title,
                mediaUri = uri,
                isVideo = item.mediaType == MediaItemType.VIDEO,
                onDismiss = { playingItem = null }
            )
        }
    }

    if (deleteSelectedDialog) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete_selected_title),
            message = stringResource(R.string.delete_selected_message, state.selectedIds.size),
            onConfirm = { deleteFromDevice ->
                viewModel.deleteSelected(deleteFromDevice)
                deleteSelectedDialog = false
            },
            onDismiss = { deleteSelectedDialog = false }
        )
    }

    if (showClearDialog) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.clear_history),
            message = stringResource(R.string.clear_history_confirm),
            onConfirm = { deleteFromDevice ->
                viewModel.clearHistory(deleteFromDevice)
                showClearDialog = false
            },
            onDismiss = { showClearDialog = false }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryItemCard(
    item: DownloadEntity,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onToggleSelect: () -> Unit,
    onPlay: () -> Unit,
    onShare: () -> Unit,
    onOpenFolder: () -> Unit,
    onPause: () -> Unit,
    onResumeOrRetry: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val formattedSize = if (item.fileSizeBytes > 0) {
        Formatter.formatShortFileSize(context, item.fileSizeBytes)
    } else {
        "--"
    }
    val formattedDate = remember(item.createdAt) {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date(item.createdAt))
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) onToggleSelect()
                    else if (item.status == DownloadStatus.COMPLETED) onPlay()
                },
                onLongClick = onToggleSelect
            )
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!item.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = item.thumbnailUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 84.dp, height = 56.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${item.platform} • ${item.mediaType.name} • $formattedSize • $formattedDate",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (isSelectionMode) {
                    Checkbox(checked = isSelected, onCheckedChange = { onToggleSelect() })
                }
            }

            if (item.status == DownloadStatus.RUNNING || item.status == DownloadStatus.QUEUED) {
                LinearProgressIndicator(
                    progress = { item.progress / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text("${item.status.name} (${item.formatExtension.uppercase()})") }
                )

                Row {
                    when (item.status) {
                        DownloadStatus.COMPLETED -> {
                            IconButton(onClick = onPlay) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.play))
                            }
                            IconButton(onClick = onShare) {
                                Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.share))
                            }
                            IconButton(onClick = onOpenFolder) {
                                Icon(Icons.Rounded.FolderOpen, contentDescription = stringResource(R.string.open_folder))
                            }
                            IconButton(onClick = onDelete) {
                                Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.delete))
                            }
                        }
                        DownloadStatus.RUNNING, DownloadStatus.QUEUED -> {
                            IconButton(onClick = onPause) {
                                Icon(Icons.Rounded.Pause, contentDescription = stringResource(R.string.pause))
                            }
                            IconButton(onClick = onCancel) {
                                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cancel))
                            }
                        }
                        DownloadStatus.PAUSED, DownloadStatus.FAILED, DownloadStatus.CANCELLED -> {
                            IconButton(onClick = onResumeOrRetry) {
                                Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.retry))
                            }
                            IconButton(onClick = onDelete) {
                                Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.delete))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfirmDeleteDialog(
    title: String,
    message: String,
    onConfirm: (deleteFilesFromDevice: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var deleteFromDevice by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(message)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = deleteFromDevice,
                        onCheckedChange = { deleteFromDevice = it }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.also_delete_files))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(deleteFromDevice) }) {
                Text(stringResource(R.string.delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
