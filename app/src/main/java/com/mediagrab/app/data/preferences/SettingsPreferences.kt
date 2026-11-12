package com.mediagrab.app.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mediagrab.app.domain.model.VideoQuality
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "settings_prefs")

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class AppLanguage(val tag: String) { SYSTEM(""), ARABIC("ar"), ENGLISH("en") }

data class AppSettings(
    val defaultFolderName: String = "MagicDownloader",
    val defaultQuality: VideoQuality = VideoQuality.P1080,
    val wifiOnly: Boolean = false,
    val maxConcurrentDownloads: Int = 2,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.SYSTEM
)

@Singleton
class SettingsPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val folderKey = stringPreferencesKey("default_folder")
    private val qualityKey = stringPreferencesKey("default_quality")
    private val wifiOnlyKey = booleanPreferencesKey("wifi_only")
    private val maxConcurrentKey = intPreferencesKey("max_concurrent")
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val languageKey = stringPreferencesKey("app_language")

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            defaultFolderName = prefs[folderKey] ?: "MagicDownloader",
            defaultQuality = prefs[qualityKey]?.let {
                runCatching { VideoQuality.valueOf(it) }.getOrNull()
            } ?: VideoQuality.P1080,
            wifiOnly = prefs[wifiOnlyKey] ?: false,
            maxConcurrentDownloads = (prefs[maxConcurrentKey] ?: 2).coerceIn(1, 5),
            themeMode = prefs[themeModeKey]?.let {
                runCatching { ThemeMode.valueOf(it) }.getOrNull()
            } ?: ThemeMode.SYSTEM,
            language = prefs[languageKey]?.let {
                runCatching { AppLanguage.valueOf(it) }.getOrNull()
            } ?: AppLanguage.SYSTEM
        )
    }

    suspend fun setDefaultFolder(folder: String) {
        context.settingsDataStore.edit { it[folderKey] = folder.ifBlank { "MagicDownloader" } }
    }

    suspend fun setDefaultQuality(quality: VideoQuality) {
        context.settingsDataStore.edit { it[qualityKey] = quality.name }
    }

    suspend fun setWifiOnly(enabled: Boolean) {
        context.settingsDataStore.edit { it[wifiOnlyKey] = enabled }
    }

    suspend fun setMaxConcurrent(count: Int) {
        context.settingsDataStore.edit { it[maxConcurrentKey] = count.coerceIn(1, 5) }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[themeModeKey] = mode.name }
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.settingsDataStore.edit { it[languageKey] = language.name }
    }
}
