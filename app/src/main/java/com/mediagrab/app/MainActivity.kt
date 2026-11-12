package com.mediagrab.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mediagrab.app.data.preferences.AppSettings
import com.mediagrab.app.data.preferences.SettingsPreferences
import com.mediagrab.app.data.preferences.ThemeMode
import com.mediagrab.app.data.preferences.WelcomePreferences
import com.mediagrab.app.ui.audiotools.AudioToolsScreen
import com.mediagrab.app.ui.download.DownloadScreen
import com.mediagrab.app.ui.download.DownloadViewModel
import com.mediagrab.app.ui.history.HistoryScreen
import com.mediagrab.app.ui.settings.SettingsScreen
import com.mediagrab.app.ui.theme.MediaGrabTheme
import com.mediagrab.app.ui.welcome.WelcomeScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

private enum class MainTab {
    DOWNLOAD,
    AUDIO_TOOLS,
    HISTORY
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var welcomePreferences: WelcomePreferences

    @Inject
    lateinit var settingsPreferences: SettingsPreferences

    private val downloadViewModel: DownloadViewModel by viewModels()

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestPostNotificationsIfNeeded()
        handleShareIntent(intent)

        setContent {
            val settings by settingsPreferences.settings
                .collectAsStateWithLifecycle(initialValue = AppSettings())
            val darkTheme = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            MediaGrabTheme(darkTheme = darkTheme) {
                val accepted by welcomePreferences.isDisclaimerAccepted
                    .collectAsStateWithLifecycle(initialValue = true)
                val scope = rememberCoroutineScope()

                if (!accepted) {
                    WelcomeScreen(
                        onAccept = {
                            scope.launch { welcomePreferences.acceptDisclaimer() }
                        }
                    )
                } else {
                    MainAppShell(downloadViewModel = downloadViewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
            downloadViewModel.handleSharedUrl(sharedText)
        }
    }

    private fun requestPostNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainAppShell(
    downloadViewModel: DownloadViewModel
) {
    var currentTab by rememberSaveable { mutableStateOf(MainTab.DOWNLOAD) }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (showSettings) stringResource(R.string.settings)
                        else stringResource(R.string.app_name)
                    )
                },
                navigationIcon = {
                    if (showSettings) {
                        IconButton(onClick = { showSettings = false }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.close)
                            )
                        }
                    }
                },
                actions = {
                    if (!showSettings) {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = stringResource(R.string.settings)
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (!showSettings) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentTab == MainTab.DOWNLOAD,
                        onClick = { currentTab = MainTab.DOWNLOAD },
                        icon = { Icon(Icons.Rounded.Download, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_download)) }
                    )
                    NavigationBarItem(
                        selected = currentTab == MainTab.AUDIO_TOOLS,
                        onClick = { currentTab = MainTab.AUDIO_TOOLS },
                        icon = { Icon(Icons.Rounded.AudioFile, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_audio_tools)) }
                    )
                    NavigationBarItem(
                        selected = currentTab == MainTab.HISTORY,
                        onClick = { currentTab = MainTab.HISTORY },
                        icon = { Icon(Icons.Rounded.History, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_history)) }
                    )
                }
            }
        }
    ) { innerPadding ->
        if (showSettings) {
            SettingsScreen(modifier = Modifier.padding(innerPadding))
        } else {
            when (currentTab) {
                MainTab.DOWNLOAD -> DownloadScreen(
                    modifier = Modifier.padding(innerPadding),
                    viewModel = downloadViewModel
                )
                MainTab.AUDIO_TOOLS -> AudioToolsScreen(
                    modifier = Modifier.padding(innerPadding)
                )
                MainTab.HISTORY -> HistoryScreen(
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}
