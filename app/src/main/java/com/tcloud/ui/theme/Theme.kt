package com.tcloud.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = TCloudPrimary,
    onPrimary = DarkOnBackground,
    primaryContainer = TCloudPrimaryDark,
    onPrimaryContainer = DarkOnBackground,
    secondary = TCloudSecondary,
    onSecondary = DarkBackground,
    secondaryContainer = TCloudSecondaryDark,
    onSecondaryContainer = DarkOnBackground,
    tertiary = TCloudTertiary,
    onTertiary = DarkBackground,
    tertiaryContainer = TCloudTertiaryDark,
    onTertiaryContainer = DarkOnBackground,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    error = TCloudError,
    onError = DarkOnBackground,
    errorContainer = TCloudError.copy(alpha = 0.2f),
    onErrorContainer = TCloudError
)

private val LightColorScheme = lightColorScheme(
    primary = TCloudPrimary,
    onPrimary = LightSurface,
    primaryContainer = TCloudPrimaryLight.copy(alpha = 0.15f),
    onPrimaryContainer = TCloudPrimaryDark,
    secondary = TCloudSecondary,
    onSecondary = LightSurface,
    secondaryContainer = TCloudSecondaryLight.copy(alpha = 0.15f),
    onSecondaryContainer = TCloudSecondaryDark,
    tertiary = TCloudTertiary,
    onTertiary = LightSurface,
    tertiaryContainer = TCloudTertiaryLight.copy(alpha = 0.15f),
    onTertiaryContainer = TCloudTertiaryDark,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    error = TCloudError,
    onError = LightSurface,
    errorContainer = TCloudError.copy(alpha = 0.1f),
    onErrorContainer = TCloudError
)

@Composable
fun TCloudTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = TCloudTypography,
        content = content
    )
}
