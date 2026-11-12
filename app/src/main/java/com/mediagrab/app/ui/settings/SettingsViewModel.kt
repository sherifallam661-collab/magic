package com.mediagrab.app.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediagrab.app.data.preferences.AppLanguage
import com.mediagrab.app.data.preferences.AppSettings
import com.mediagrab.app.data.preferences.SettingsPreferences
import com.mediagrab.app.data.preferences.ThemeMode
import com.mediagrab.app.domain.model.VideoQuality
import com.yausername.youtubedl_android.YoutubeDL
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsPreferences: SettingsPreferences
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsPreferences.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AppSettings()
        )

    private val _ytDlpUpdateStatus = MutableStateFlow<String?>(null)
    val ytDlpUpdateStatus: StateFlow<String?> = _ytDlpUpdateStatus.asStateFlow()

    private val _isUpdatingYtDlp = MutableStateFlow(false)
    val isUpdatingYtDlp: StateFlow<Boolean> = _isUpdatingYtDlp.asStateFlow()

    fun setDefaultFolder(folder: String) = viewModelScope.launch {
        settingsPreferences.setDefaultFolder(folder)
    }

    fun setDefaultQuality(quality: VideoQuality) = viewModelScope.launch {
        settingsPreferences.setDefaultQuality(quality)
    }

    fun setWifiOnly(enabled: Boolean) = viewModelScope.launch {
        settingsPreferences.setWifiOnly(enabled)
    }

    fun setMaxConcurrent(count: Int) = viewModelScope.launch {
        settingsPreferences.setMaxConcurrent(count)
    }

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch {
        settingsPreferences.setThemeMode(mode)
    }

    fun setLanguage(language: AppLanguage) = viewModelScope.launch {
        settingsPreferences.setLanguage(language)
    }

    fun updateYtDlp() = viewModelScope.launch {
        _isUpdatingYtDlp.value = true
        _ytDlpUpdateStatus.value = null
        val result = withContext(Dispatchers.IO) {
            runCatching {
                YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
            }
        }
        _isUpdatingYtDlp.value = false
        _ytDlpUpdateStatus.value = result.fold(
            onSuccess = { status -> status?.name ?: "ALREADY_UP_TO_DATE" },
            onFailure = { err -> err.message ?: "Update failed" }
        )
    }
}
