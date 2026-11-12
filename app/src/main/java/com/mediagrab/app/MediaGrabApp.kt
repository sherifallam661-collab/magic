package com.mediagrab.app

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class MediaGrabApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Initialize native yt-dlp, FFmpeg, and aria2c off the Main Thread
        appScope.launch {
            runCatching {
                YoutubeDL.getInstance().init(this@MediaGrabApp)
                FFmpeg.getInstance().init(this@MediaGrabApp)
                Aria2c.getInstance().init(this@MediaGrabApp)
            }.onFailure { error ->
                Log.e("MediaGrabApp", "Failed to initialize media engines", error)
            }
        }
    }
}
