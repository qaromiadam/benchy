package com.benchy.X.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.benchy.X.data.AccentPreset
import com.benchy.X.data.CardDensity
import com.benchy.X.data.ThemeMode

data class BenchyLayoutMetrics(
    val cardPadding: Dp = 16.dp,
    val sectionSpacing: Dp = 14.dp
)

val LocalBenchyLayoutMetrics = staticCompositionLocalOf { BenchyLayoutMetrics() }

@Composable
fun BenchyTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    accentPreset: AccentPreset = AccentPreset.ELECTRIC_CYAN,
    cardDensity: CardDensity = CardDensity.COMFORTABLE,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
    }

    val accent = Color(accentPreset.hex)
    val onAccent = if (accentPreset == AccentPreset.COBALT_PULSE) Color.White else Color(0xFF041418)

    val colorScheme = when {
        themeMode == ThemeMode.AMOLED || (themeMode == ThemeMode.SYSTEM && systemDark && false) -> {
            darkColorScheme(
                primary = accent,
                onPrimary = onAccent,
                primaryContainer = accent.copy(alpha = 0.16f),
                onPrimaryContainer = accent,
                secondary = Color(0xFF7EE787),
                onSecondary = Color(0xFF00210B),
                secondaryContainer = Color(0xFF141B24),
                onSecondaryContainer = GraphiteOnSurface,
                background = AmoledBackground,
                onBackground = GraphiteOnSurface,
                surface = AmoledSurface,
                onSurface = GraphiteOnSurface,
                surfaceVariant = AmoledSurfaceVariant,
                onSurfaceVariant = GraphiteOnSurfaceMuted,
                outline = AmoledOutline,
                error = TierCriticalRed,
                onError = Color.White
            )
        }
        isDark -> {
            darkColorScheme(
                primary = accent,
                onPrimary = onAccent,
                primaryContainer = accent.copy(alpha = 0.16f),
                onPrimaryContainer = accent,
                secondary = Color(0xFF7EE787),
                onSecondary = Color(0xFF00210B),
                secondaryContainer = GraphiteSurfaceVariant,
                onSecondaryContainer = GraphiteOnSurface,
                background = GraphiteBackground,
                onBackground = GraphiteOnSurface,
                surface = GraphiteSurface,
                onSurface = GraphiteOnSurface,
                surfaceVariant = GraphiteSurfaceVariant,
                onSurfaceVariant = GraphiteOnSurfaceMuted,
                outline = GraphiteOutline,
                error = TierCriticalRed,
                onError = Color.White
            )
        }
        else -> {
            val lightAccent = when (accentPreset) {
                AccentPreset.ELECTRIC_CYAN -> Color(0xFF008394)
                AccentPreset.COBALT_PULSE -> Color(0xFF1A56DB)
                AccentPreset.EMERALD_TELEMETRY -> Color(0xFF0F8A4B)
                AccentPreset.SOLAR_AMBER -> Color(0xFFB26A00)
            }
            lightColorScheme(
                primary = lightAccent,
                onPrimary = Color.White,
                primaryContainer = lightAccent.copy(alpha = 0.12f),
                onPrimaryContainer = lightAccent,
                secondary = Color(0xFF1B7F4B),
                onSecondary = Color.White,
                secondaryContainer = LightSurfaceVariant,
                onSecondaryContainer = LightOnSurface,
                background = LightBackground,
                onBackground = LightOnSurface,
                surface = LightSurface,
                onSurface = LightOnSurface,
                surfaceVariant = LightSurfaceVariant,
                onSurfaceVariant = LightOnSurfaceMuted,
                outline = LightOutline,
                error = TierCriticalRed,
                onError = Color.White
            )
        }
    }

    val metrics = when (cardDensity) {
        CardDensity.COMFORTABLE -> BenchyLayoutMetrics(cardPadding = 16.dp, sectionSpacing = 14.dp)
        CardDensity.COMPACT -> BenchyLayoutMetrics(cardPadding = 11.dp, sectionSpacing = 9.dp)
    }

    CompositionLocalProvider(LocalBenchyLayoutMetrics provides metrics) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = BenchyTypography,
            content = content
        )
    }
}
