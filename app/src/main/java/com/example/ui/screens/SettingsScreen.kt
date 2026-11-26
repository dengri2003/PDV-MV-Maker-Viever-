package com.example.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.data.prefs.AppLanguage
import com.example.data.prefs.AppThemeMode
import com.example.data.prefs.EyeCareFilter
import com.example.data.prefs.ReadingMode
import com.example.i18n.LocalStrings
import com.example.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.refreshCacheSize()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(strings.settingsTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SettingsSectionCard(title = strings.languageSection) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = uiState.preferences.language == AppLanguage.SYSTEM,
                            onClick = { viewModel.setLanguage(AppLanguage.SYSTEM) },
                            label = { Text(strings.langSystem) },
                            modifier = Modifier.testTag("lang_system_chip")
                        )
                        FilterChip(
                            selected = uiState.preferences.language == AppLanguage.ENGLISH,
                            onClick = { viewModel.setLanguage(AppLanguage.ENGLISH) },
                            label = { Text(strings.langEnglish) },
                            modifier = Modifier.testTag("lang_english_chip")
                        )
                        FilterChip(
                            selected = uiState.preferences.language == AppLanguage.RUSSIAN,
                            onClick = { viewModel.setLanguage(AppLanguage.RUSSIAN) },
                            label = { Text(strings.langRussian) },
                            modifier = Modifier.testTag("lang_russian_chip")
                        )
                    }
                }

                SettingsSectionCard(title = strings.themeSection) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = uiState.preferences.themeMode == AppThemeMode.SYSTEM,
                            onClick = { viewModel.setThemeMode(AppThemeMode.SYSTEM) },
                            label = { Text(strings.themeSystem) }
                        )
                        FilterChip(
                            selected = uiState.preferences.themeMode == AppThemeMode.LIGHT,
                            onClick = { viewModel.setThemeMode(AppThemeMode.LIGHT) },
                            label = { Text(strings.themeLight) }
                        )
                        FilterChip(
                            selected = uiState.preferences.themeMode == AppThemeMode.OLED_DARK,
                            onClick = { viewModel.setThemeMode(AppThemeMode.OLED_DARK) },
                            label = { Text(strings.themeOledDark) }
                        )
                    }
                }

                SettingsSectionCard(title = strings.defaultsSection) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = strings.readingMode,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = uiState.preferences.defaultReadingMode == ReadingMode.VERTICAL_CONTINUOUS,
                                onClick = { viewModel.setDefaultReadingMode(ReadingMode.VERTICAL_CONTINUOUS) },
                                label = { Text(strings.continuousScroll) }
                            )
                            FilterChip(
                                selected = uiState.preferences.defaultReadingMode == ReadingMode.HORIZONTAL_PAGER,
                                onClick = { viewModel.setDefaultReadingMode(ReadingMode.HORIZONTAL_PAGER) },
                                label = { Text(strings.singlePageSwipe) }
                            )
                        }

                        Text(
                            text = strings.eyeCareFilter,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = uiState.preferences.defaultEyeCareFilter == EyeCareFilter.ORIGINAL,
                                onClick = { viewModel.setDefaultEyeCareFilter(EyeCareFilter.ORIGINAL) },
                                label = { Text(strings.filterOriginal) }
                            )
                            FilterChip(
                                selected = uiState.preferences.defaultEyeCareFilter == EyeCareFilter.SEPIA,
                                onClick = { viewModel.setDefaultEyeCareFilter(EyeCareFilter.SEPIA) },
                                label = { Text(strings.filterSepia) }
                            )
                            FilterChip(
                                selected = uiState.preferences.defaultEyeCareFilter == EyeCareFilter.OLED_NIGHT,
                                onClick = { viewModel.setDefaultEyeCareFilter(EyeCareFilter.OLED_NIGHT) },
                                label = { Text(strings.filterOledNight) }
                            )
                        }
                    }
                }

                SettingsSectionCard(title = strings.storageCacheSection) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = strings.cacheUsage(uiState.cacheSizeText),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = {
                                viewModel.clearCache()
                                scope.launch {
                                    snackbarHostState.showSnackbar(strings.cacheCleared)
                                }
                            },
                            modifier = Modifier.testTag("clear_cache_button")
                        ) {
                            Text(strings.clearCache)
                        }
                    }
                }

                SettingsSectionCard(title = strings.aboutSection) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = strings.versionLabel(BuildConfig.VERSION_NAME),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = strings.privacyStatement,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = strings.engineInfo,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            content()
        }
    }
}
