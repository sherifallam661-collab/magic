package com.mediagrab.app.ui.audiotools

import com.mediagrab.app.data.repository.AudioProcessingRepository
import com.mediagrab.app.data.repository.DownloadRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
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
class AudioToolsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var audioRepo: AudioProcessingRepository
    private lateinit var downloadRepo: DownloadRepository
    private lateinit var viewModel: AudioToolsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        audioRepo = mockk(relaxed = true)
        downloadRepo = mockk(relaxed = true)
        every { downloadRepo.observeDownloads() } returns flowOf(emptyList())
        viewModel = AudioToolsViewModel(audioRepo, downloadRepo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `selectTrimSource loads duration and waveform`() = runTest {
        coEvery { audioRepo.getAudioDurationMs("content://audio/1") } returns 30_000L
        coEvery { audioRepo.extractWaveform("content://audio/1") } returns listOf(0.5f, 0.8f)

        viewModel.selectTrimSource("content://audio/1", "Track 1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(30_000L, state.trimEndMs)
        assertEquals("00:30.000", state.endTimeText)
        assertEquals(2, state.waveform.size)
    }

    @Test
    fun `moveMergeClip reorders clips accurately`() = runTest {
        coEvery { audioRepo.getAudioDurationMs(any()) } returns 10_000L

        viewModel.addMergeClip("content://1", "First")
        viewModel.addMergeClip("content://2", "Second")
        advanceUntilIdle()

        viewModel.moveMergeClip(0, 1)

        assertEquals("Second", viewModel.uiState.value.mergeClips[0].title)
        assertEquals("First", viewModel.uiState.value.mergeClips[1].title)
    }
}
