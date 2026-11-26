package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.prefs.AppLanguage
import com.example.data.prefs.AppThemeMode
import com.example.data.prefs.EyeCareFilter
import com.example.data.prefs.ReadingMode
import com.example.data.prefs.UserPreferences
import com.example.data.prefs.UserPreferencesRepository
import com.example.pdf.PdfRenderEngine
import com.example.util.formatFileSize
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val preferences: UserPreferences = UserPreferences(),
    val cacheSizeText: String = "0 B"
)

class SettingsViewModel(
    private val preferencesRepository: UserPreferencesRepository,
    private val renderEngine: PdfRenderEngine
) : ViewModel() {

    private val cacheSizeBytes = MutableStateFlow(renderEngine.getCacheSizeBytes())

    val uiState: StateFlow<SettingsUiState> = combine(
        preferencesRepository.preferencesFlow,
        cacheSizeBytes
    ) { prefs, bytes ->
        SettingsUiState(
            preferences = prefs,
            cacheSizeText = formatFileSize(bytes)
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    fun refreshCacheSize() {
        cacheSizeBytes.value = renderEngine.getCacheSizeBytes()
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { preferencesRepository.setLanguage(language) }
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
    }

    fun setDefaultReadingMode(mode: ReadingMode) {
        viewModelScope.launch { preferencesRepository.setDefaultReadingMode(mode) }
    }

    fun setDefaultEyeCareFilter(filter: EyeCareFilter) {
        viewModelScope.launch { preferencesRepository.setDefaultEyeCareFilter(filter) }
    }

    fun clearCache() {
        renderEngine.clearAllCaches()
        cacheSizeBytes.value = renderEngine.getCacheSizeBytes()
    }
}
