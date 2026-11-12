package com.mediagrab.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediagrab.app.data.local.entity.DownloadEntity
import com.mediagrab.app.data.local.entity.MediaItemType
import com.mediagrab.app.data.repository.DownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class HistoryFilterType { ALL, VIDEO, AUDIO }
enum class HistorySortOrder { NEWEST, SIZE_DESC, NAME_ASC }

data class HistoryUiState(
    val items: List<DownloadEntity> = emptyList(),
    val searchQuery: String = "",
    val filterType: HistoryFilterType = HistoryFilterType.ALL,
    val selectedPlatform: String? = null,
    val availablePlatforms: List<String> = emptyList(),
    val sortOrder: HistorySortOrder = HistorySortOrder.NEWEST,
    val selectedIds: Set<Long> = emptySet()
) {
    val isSelectionMode: Boolean get() = selectedIds.isNotEmpty()
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val downloadRepository: DownloadRepository
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val filterType = MutableStateFlow(HistoryFilterType.ALL)
    private val selectedPlatform = MutableStateFlow<String?>(null)
    private val sortOrder = MutableStateFlow(HistorySortOrder.NEWEST)
    private val selectedIds = MutableStateFlow<Set<Long>>(emptySet())

    val uiState: StateFlow<HistoryUiState> = combine(
        downloadRepository.observeDownloads(),
        searchQuery,
        filterType,
        selectedPlatform,
        sortOrder,
        selectedIds
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val allItems = values[0] as List<DownloadEntity>
        val query = values[1] as String
        val typeFilter = values[2] as HistoryFilterType
        val platformFilter = values[3] as String?
        val sort = values[4] as HistorySortOrder
        @Suppress("UNCHECKED_CAST")
        val selected = values[5] as Set<Long>

        val platforms = allItems.map { it.platform }.distinct().sorted()

        val filtered = allItems
            .filter { item ->
                val matchesQuery = query.isBlank() ||
                    item.title.contains(query, ignoreCase = true) ||
                    item.platform.contains(query, ignoreCase = true)
                val matchesType = when (typeFilter) {
                    HistoryFilterType.ALL -> true
                    HistoryFilterType.VIDEO -> item.mediaType == MediaItemType.VIDEO
                    HistoryFilterType.AUDIO -> item.mediaType != MediaItemType.VIDEO
                }
                val matchesPlatform = platformFilter == null || item.platform == platformFilter
                matchesQuery && matchesType && matchesPlatform
            }
            .let { list ->
                when (sort) {
                    HistorySortOrder.NEWEST -> list.sortedByDescending { it.createdAt }
                    HistorySortOrder.SIZE_DESC -> list.sortedByDescending { it.fileSizeBytes }
                    HistorySortOrder.NAME_ASC -> list.sortedBy { it.title.lowercase() }
                }
            }

        HistoryUiState(
            items = filtered,
            searchQuery = query,
            filterType = typeFilter,
            selectedPlatform = platformFilter,
            availablePlatforms = platforms,
            sortOrder = sort,
            selectedIds = selected
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HistoryUiState()
    )

    fun onSearchQueryChanged(query: String) {
        searchQuery.value = query
    }

    fun onFilterTypeChanged(type: HistoryFilterType) {
        filterType.value = type
    }

    fun onPlatformSelected(platform: String?) {
        selectedPlatform.value = platform
    }

    fun onSortOrderChanged(order: HistorySortOrder) {
        sortOrder.value = order
    }

    fun toggleSelection(id: Long) {
        selectedIds.update { current ->
            if (id in current) current - id else current + id
        }
    }

    fun clearSelection() {
        selectedIds.value = emptySet()
    }

    fun pauseDownload(id: Long) = viewModelScope.launch {
        downloadRepository.pauseDownload(id)
    }

    fun resumeOrRetry(id: Long) = viewModelScope.launch {
        downloadRepository.resumeOrRetryDownload(id)
    }

    fun cancelDownload(id: Long) = viewModelScope.launch {
        downloadRepository.cancelDownload(id)
    }

    fun deleteSingle(id: Long, deleteFileFromDevice: Boolean) = viewModelScope.launch {
        downloadRepository.deleteDownloads(listOf(id), deleteFileFromDevice)
        selectedIds.update { it - id }
    }

    fun deleteSelected(deleteFilesFromDevice: Boolean) = viewModelScope.launch {
        val ids = selectedIds.value.toList()
        if (ids.isNotEmpty()) {
            downloadRepository.deleteDownloads(ids, deleteFilesFromDevice)
            selectedIds.value = emptySet()
        }
    }

    fun clearHistory(deleteFilesFromDevice: Boolean) = viewModelScope.launch {
        downloadRepository.clearHistory(deleteFilesFromDevice)
        selectedIds.value = emptySet()
    }
}
