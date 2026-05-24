package com.deepshield.ai.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ============================================================
// DeepShield AI — Material 3 Dark Theme
// Always dark — cybersecurity aesthetic demands it
// ============================================================

private val DeepShieldColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = CyberBlack,
    primaryContainer = NeonCyanDark,
    onPrimaryContainer = NeonCyanLight,

    secondary = DeepPurple,
    onSecondary = TextPrimary,
    secondaryContainer = DeepPurpleDark,
    onSecondaryContainer = DeepPurpleLight,

    tertiary = NeonGreen,
    onTertiary = CyberBlack,
    tertiaryContainer = NeonGreenDark,
    onTertiaryContainer = NeonGreen,

    error = DangerRed,
    onError = TextPrimary,
    errorContainer = DangerRedDark,
    onErrorContainer = DangerRedLight,

    background = CyberBlack,
    onBackground = TextPrimary,

    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceCard,
    onSurfaceVariant = TextSecondary,

    outline = GlassBorder,
    outlineVariant = GlassBorderHover,

    inverseSurface = TextPrimary,
    inverseOnSurface = CyberBlack,
    inversePrimary = NeonCyanDark,

    surfaceTint = NeonCyan,
    scrim = CyberBlack
)

@Composable
fun DeepShieldTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = DeepShieldColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = CyberBlack.toArgb()
            window.navigationBarColor = CyberBlack.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = DeepShieldTypography,
        shapes = DeepShieldShapes,
        content = content
    )
}
