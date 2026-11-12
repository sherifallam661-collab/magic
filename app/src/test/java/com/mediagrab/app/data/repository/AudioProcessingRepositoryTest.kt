package com.mediagrab.app.data.repository

import android.content.Context
import com.mediagrab.app.data.preferences.SettingsPreferences
import com.mediagrab.app.domain.model.AudioOutputFormat
import com.mediagrab.app.domain.model.AudioTimeFormatter
import com.mediagrab.app.domain.model.MergeAudioClip
import com.mediagrab.app.domain.model.MergeTransitionMode
import com.mediagrab.app.util.MediaStoreHelper
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioProcessingRepositoryTest {

    private val repo = AudioProcessingRepository(
        context = mockk<Context>(relaxed = true),
        mediaStoreHelper = mockk<MediaStoreHelper>(relaxed = true),
        downloadRepository = mockk<DownloadRepository>(relaxed = true),
        settingsPreferences = mockk<SettingsPreferences>(relaxed = true)
    )

    @Test
    fun `buildTrimCommand includes fade in and fade out filters`() {
        val cmd = repo.buildTrimCommand(
            inputPath = "/tmp/in.mp3",
            outputPath = "/tmp/out.mp3",
            startMs = 5_000L,
            endMs = 25_000L,
            fadeInSec = 2.0f,
            fadeOutSec = 3.0f,
            outputFormat = AudioOutputFormat.MP3
        )

        assertTrue(cmd.contains("-ss 5.000"))
        assertTrue(cmd.contains("-t 20.000"))
        assertTrue(cmd.contains("afade=t=in:st=0:d=2.00"))
        assertTrue(cmd.contains("afade=t=out:st=17.00:d=3.00"))
        assertTrue(cmd.contains("-c:a libmp3lame"))
    }

    @Test
    fun `buildMergeCommand with crossfade trims each clip and applies acrossfade`() {
        val clips = listOf(
            MergeAudioClip("1", "content://1", "Clip1", 20_000L, 1_000L, 10_000L),
            MergeAudioClip("2", "content://2", "Clip2", 15_000L, 0L, 8_000L)
        )
        val cmd = repo.buildMergeCommand(
            inputPaths = listOf("/tmp/a.mp3", "/tmp/b.mp3"),
            clips = clips,
            outputPath = "/tmp/merged.m4a",
            transitionMode = MergeTransitionMode.CROSSFADE,
            transitionSeconds = 1.5f,
            outputFormat = AudioOutputFormat.M4A
        )

        assertTrue(cmd.contains("[0:a]atrim=start=1.000:end=10.000"))
        assertTrue(cmd.contains("[1:a]atrim=start=0.000:end=8.000"))
        assertTrue(cmd.contains("acrossfade=d=1.50:c1=tri:c2=tri[aout]"))
        assertTrue(cmd.contains("-c:a aac"))
    }

    @Test
    fun `AudioTimeFormatter formats and parses mm_ss_SSS accurately`() {
        val formatted = AudioTimeFormatter.formatMillis(75_320L)
        assertEquals("01:15.320", formatted)
        assertEquals(75_320L, AudioTimeFormatter.parseMillis("01:15.320"))
        assertNull(AudioTimeFormatter.parseMillis("invalid"))
    }
}
