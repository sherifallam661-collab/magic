package com.mediagrab.app.ui.history

import com.mediagrab.app.data.local.entity.DownloadEntity
import com.mediagrab.app.data.local.entity.DownloadStatus
import com.mediagrab.app.data.local.entity.MediaItemType
import com.mediagrab.app.data.repository.DownloadRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: DownloadRepository
    private lateinit var viewModel: HistoryViewModel
    private val itemsFlow = MutableStateFlow(
        listOf(
            DownloadEntity(
                id = 1,
                url = "https://youtu.be/1",
                title = "Alpha Video",
                platform = "YouTube",
                mediaType = MediaItemType.VIDEO,
                formatExtension = "mp4",
                qualityLabel = "1080p",
                status = DownloadStatus.COMPLETED,
                fileSizeBytes = 10_000,
                createdAt = 100L
            ),
            DownloadEntity(
                id = 2,
                url = "https://soundcloud.com/2",
                title = "Beta Podcast",
                platform = "SoundCloud",
                mediaType = MediaItemType.AUDIO,
                formatExtension = "mp3",
                qualityLabel = "320 kbps",
                status = DownloadStatus.COMPLETED,
                fileSizeBytes = 50_000,
                createdAt = 200L
            )
        )
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true)
        every { repository.observeDownloads() } returns itemsFlow
        viewModel = HistoryViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `filter by audio and search query filters items accurately`() = runTest {
        val job = launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.onFilterTypeChanged(HistoryFilterType.AUDIO)
        viewModel.onSearchQueryChanged("Beta")
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.items.size)
        assertEquals("Beta Podcast", viewModel.uiState.value.items.first().title)
        job.cancel()
    }

    @Test
    fun `sort by size descending orders largest file first`() = runTest {
        val job = launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.onSortOrderChanged(HistorySortOrder.SIZE_DESC)
        advanceUntilIdle()

        assertEquals(2L, viewModel.uiState.value.items.first().id)
        job.cancel()
    }

    @Test
    fun `deleteSelected invokes repository with selected ids and clears selection`() = runTest {
        val job = launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.toggleSelection(1L)
        viewModel.deleteSelected(deleteFilesFromDevice = true)
        advanceUntilIdle()

        coVerify { repository.deleteDownloads(listOf(1L), true) }
        assertEquals(emptySet<Long>(), viewModel.uiState.value.selectedIds)
        job.cancel()
    }
}
