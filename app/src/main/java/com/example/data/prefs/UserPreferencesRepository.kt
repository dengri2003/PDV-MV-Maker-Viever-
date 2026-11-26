package com.example.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class AppLanguage { SYSTEM, ENGLISH, RUSSIAN }
enum class AppThemeMode { SYSTEM, LIGHT, OLED_DARK }
enum class ReadingMode { VERTICAL_CONTINUOUS, HORIZONTAL_PAGER }
enum class EyeCareFilter { ORIGINAL, SEPIA, OLED_NIGHT }
enum class PdfPageSize { A4, LETTER, FIT_IMAGE }
enum class PdfMarginSize { NONE, SMALL, NORMAL }
enum class PdfExportQuality(val jpegQuality: Int, val maxDimensionPx: Int) {
    HIGH(92, 2400),
    BALANCED(76, 1750),
    COMPACT(54, 1200)
}

enum class ImageEnhancementFilter { ORIGINAL, BW_SCAN, HIGH_CONTRAST }

data class UserPreferences(
    val language: AppLanguage = AppLanguage.SYSTEM,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val defaultReadingMode: ReadingMode = ReadingMode.VERTICAL_CONTINUOUS,
    val defaultEyeCareFilter: EyeCareFilter = EyeCareFilter.ORIGINAL
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "pdf_mv_prefs")

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val LANGUAGE = stringPreferencesKey("language")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val READING_MODE = stringPreferencesKey("reading_mode")
        val EYE_CARE_FILTER = stringPreferencesKey("eye_care_filter")
    }

    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            language = prefs[Keys.LANGUAGE]?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() }
                ?: AppLanguage.SYSTEM,
            themeMode = prefs[Keys.THEME_MODE]?.let { runCatching { AppThemeMode.valueOf(it) }.getOrNull() }
                ?: AppThemeMode.SYSTEM,
            defaultReadingMode = prefs[Keys.READING_MODE]?.let { runCatching { ReadingMode.valueOf(it) }.getOrNull() }
                ?: ReadingMode.VERTICAL_CONTINUOUS,
            defaultEyeCareFilter = prefs[Keys.EYE_CARE_FILTER]?.let { runCatching { EyeCareFilter.valueOf(it) }.getOrNull() }
                ?: EyeCareFilter.ORIGINAL
        )
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.dataStore.edit { it[Keys.LANGUAGE] = language.name }
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setDefaultReadingMode(mode: ReadingMode) {
        context.dataStore.edit { it[Keys.READING_MODE] = mode.name }
    }

    suspend fun setDefaultEyeCareFilter(filter: EyeCareFilter) {
        context.dataStore.edit { it[Keys.EYE_CARE_FILTER] = filter.name }
    }
}
