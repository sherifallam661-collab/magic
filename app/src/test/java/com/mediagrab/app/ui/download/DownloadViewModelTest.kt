package com.mediagrab.app.ui.download

import com.mediagrab.app.data.repository.MappedDownloadException
import com.mediagrab.app.data.repository.YtDlpRepository
import com.mediagrab.app.domain.model.DownloadError
import com.mediagrab.app.domain.model.DownloadFormatType
import com.mediagrab.app.domain.model.MediaInfo
import com.mediagrab.app.domain.model.PlaylistEntry
import com.mediagrab.app.domain.model.SupportedPlatform
import com.mediagrab.app.domain.model.VideoQuality
import com.mediagrab.app.util.ClipboardHelper
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var ytDlpRepository: YtDlpRepository
    private lateinit var clipboardHelper: ClipboardHelper
    private lateinit var viewModel: DownloadViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        ytDlpRepository = mockk()
        clipboardHelper = mockk(relaxed = true)
        viewModel = DownloadViewModel(ytDlpRepository, clipboardHelper)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `fetchMetadata with invalid url sets InvalidUrl error`() = runTest {
        every { clipboardHelper.isValidHttpUrl("not-a-url") } returns false

        viewModel.onUrlChanged("not-a-url")
        viewModel.fetchMetadata()

        assertEquals(DownloadError.InvalidUrl, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `fetchMetadata success populates mediaInfo and selects best available quality`() = runTest {
        val url = "https://youtu.be/dQw4w9WgXcQ"
        val sampleInfo = MediaInfo(
            id = "dQw4w9WgXcQ",
            url = url,
            title = "Sample Video",
            thumbnailUrl = "https://img.youtube.com/vi/dQw4w9WgXcQ/0.jpg",
            durationSeconds = 212,
            platform = SupportedPlatform.YOUTUBE,
            availableQualities = listOf(VideoQuality.P360, VideoQuality.P720, VideoQuality.P1080)
        )
        every { clipboardHelper.isValidHttpUrl(url) } returns true
        coEvery { ytDlpRepository.fetchMediaInfo(url) } returns Result.success(sampleInfo)

        viewModel.onUrlChanged(url)
        viewModel.fetchMetadata()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.mediaInfo)
        assertEquals("Sample Video", state.mediaInfo?.title)
        assertEquals(VideoQuality.P1080, state.selectedVideoQuality)
    }

    @Test
    fun `fetchMetadata failure maps NoInternet error`() = runTest {
        val url = "https://www.tiktok.com/@user/video/123"
        every { clipboardHelper.isValidHttpUrl(url) } returns true
        coEvery { ytDlpRepository.fetchMediaInfo(url) } returns Result.failure(
            MappedDownloadException(DownloadError.NoInternet)
        )

        viewModel.onUrlChanged(url)
        viewModel.fetchMetadata()
        advanceUntilIdle()

        assertEquals(DownloadError.NoInternet, viewModel.uiState.value.error)
    }

    @Test
    fun `playlist selection toggles and queues only selected items`() = runTest {
        val url = "https://youtube.com/playlist?list=PL123"
        val playlistInfo = MediaInfo(
            id = "PL123",
            url = url,
            title = "My Playlist",
            thumbnailUrl = null,
            durationSeconds = 300,
            platform = SupportedPlatform.YOUTUBE,
            availableQualities = listOf(VideoQuality.P720),
            isPlaylist = true,
            playlistEntries = listOf(
                PlaylistEntry("1", "Track 1", "https://youtu.be/1", null, 150, true),
                PlaylistEntry("2", "Track 2", "https://youtu.be/2", null, 150, true)
            )
        )
        every { clipboardHelper.isValidHttpUrl(url) } returns true
        coEvery { ytDlpRepository.fetchMediaInfo(url) } returns Result.success(playlistInfo)

        viewModel.onUrlChanged(url)
        viewModel.fetchMetadata()
        advanceUntilIdle()

        viewModel.togglePlaylistEntry("2")
        viewModel.selectFormat(DownloadFormatType.AUDIO_MP3)
        viewModel.startDownload()

        assertEquals(1, viewModel.pendingRequests.value.size)
        assertEquals("Track 1", viewModel.pendingRequests.value.first().title)
        assertTrue(viewModel.uiState.value.queuedMessageVisible)
    }
}
