package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.data.prefs.AppThemeMode

private val OledDarkColorScheme = darkColorScheme(
    primary = ElectricCoralDark,
    onPrimary = Color(0xFF1A0503),
    primaryContainer = Color(0xFF3D1410),
    onPrimaryContainer = Color(0xFFFFDAD5),
    secondary = DeepIndigoLight,
    onSecondary = Color(0xFF100E2E),
    secondaryContainer = Color(0xFF211F4B),
    onSecondaryContainer = Color(0xFFE0E0FF),
    tertiary = EmeraldSuccess,
    background = OledPitchBlack,
    onBackground = PaperWhite,
    surface = OledCardSurface,
    onSurface = PaperWhite,
    surfaceVariant = OledSurfaceVariant,
    onSurfaceVariant = PaperMuted,
    outline = OledOutline,
    outlineVariant = Color(0xFF222229),
    error = CoralError,
    onError = Color.White
)

private val WarmPaperLightColorScheme = lightColorScheme(
    primary = ElectricCoral,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE5E2),
    onPrimaryContainer = Color(0xFF5C1009),
    secondary = DeepIndigo,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8E7FF),
    onSecondaryContainer = Color(0xFF1A155E),
    tertiary = EmeraldSuccess,
    background = WarmPaperBg,
    onBackground = InkBlack,
    surface = WarmPaperSurface,
    onSurface = InkBlack,
    surfaceVariant = WarmPaperSurfaceVariant,
    onSurfaceVariant = InkMuted,
    outline = WarmPaperOutline,
    outlineVariant = Color(0xFFECE9E1),
    error = CoralError,
    onError = Color.White
)

val PdfMvShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun PdfMvTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        AppThemeMode.SYSTEM -> systemDark
        AppThemeMode.LIGHT -> false
        AppThemeMode.OLED_DARK -> true
    }

    val colorScheme = if (useDark) OledDarkColorScheme else WarmPaperLightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !useDark
                isAppearanceLightNavigationBars = !useDark
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PdfMvTypography,
        shapes = PdfMvShapes,
        content = content
    )
}
