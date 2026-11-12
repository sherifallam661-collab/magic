package com.mediagrab.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mediagrab.app.R
import com.mediagrab.app.data.preferences.AppLanguage
import com.mediagrab.app.data.preferences.ThemeMode
import com.mediagrab.app.domain.model.VideoQuality
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val isUpdating by viewModel.isUpdatingYtDlp.collectAsStateWithLifecycle()
    val updateStatus by viewModel.ytDlpUpdateStatus.collectAsStateWithLifecycle()
    var folderDraft by remember(settings.defaultFolderName) {
        mutableStateOf(settings.defaultFolderName)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.settings),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        OutlinedTextField(
            value = folderDraft,
            onValueChange = {
                folderDraft = it
                viewModel.setDefaultFolder(it)
            },
            label = { Text(stringResource(R.string.setting_save_folder)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = stringResource(R.string.setting_default_quality),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VideoQuality.entries.forEach { quality ->
                FilterChip(
                    selected = settings.defaultQuality == quality,
                    onClick = { viewModel.setDefaultQuality(quality) },
                    label = { Text(quality.label) }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.setting_wifi_only),
                style = MaterialTheme.typography.bodyLarge
            )
            Switch(
                checked = settings.wifiOnly,
                onCheckedChange = viewModel::setWifiOnly
            )
        }

        Column {
            Text(
                text = stringResource(R.string.setting_max_concurrent, settings.maxConcurrentDownloads),
                style = MaterialTheme.typography.bodyLarge
            )
            Slider(
                value = settings.maxConcurrentDownloads.toFloat(),
                onValueChange = { viewModel.setMaxConcurrent(it.roundToInt()) },
                valueRange = 1f..5f,
                steps = 3
            )
        }

        HorizontalDivider()

        Text(
            text = stringResource(R.string.setting_theme),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = settings.themeMode == mode,
                    onClick = { viewModel.setThemeMode(mode) },
                    label = { Text(mode.name) }
                )
            }
        }

        Text(
            text = stringResource(R.string.setting_language),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppLanguage.entries.forEach { lang ->
                FilterChip(
                    selected = settings.language == lang,
                    onClick = { viewModel.setLanguage(lang) },
                    label = { Text(lang.name) }
                )
            }
        }

        HorizontalDivider()

        Button(
            onClick = viewModel::updateYtDlp,
            enabled = !isUpdating,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isUpdating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(stringResource(R.string.setting_update_ytdlp))
        }

        updateStatus?.let { status ->
            Text(
                text = status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
