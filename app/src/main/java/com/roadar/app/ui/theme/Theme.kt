package com.roadar.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = RoadarDarkNavy,
    onPrimary = RoadarDarkBackground,
    secondary = RoadarDarkSlate,
    onSecondary = RoadarDarkBackground,
    tertiary = RoadarDarkOrange,
    onTertiary = RoadarDarkBackground,
    background = RoadarDarkBackground,
    onBackground = RoadarDarkText,
    surface = RoadarDarkSurface,
    onSurface = RoadarDarkText,
    surfaceVariant = RoadarDarkSurfaceVariant,
    onSurfaceVariant = RoadarDarkSlate,
    secondaryContainer = RoadarDarkSurfaceVariant,
    onSecondaryContainer = RoadarDarkText
)

private val LightColorScheme = lightColorScheme(
    primary = RoadarNavy,
    onPrimary = RoadarLightSurface,
    secondary = RoadarSlate,
    onSecondary = RoadarLightSurface,
    tertiary = RoadarOrange,
    onTertiary = RoadarLightSurface,
    background = RoadarLightBackground,
    onBackground = RoadarLightText,
    surface = RoadarLightSurface,
    onSurface = RoadarLightText,
    surfaceVariant = RoadarLightSurfaceVariant,
    onSurfaceVariant = RoadarSlate,
    secondaryContainer = RoadarLightSurfaceVariant,
    onSecondaryContainer = RoadarNavy
)

@Composable
fun ROADARTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}