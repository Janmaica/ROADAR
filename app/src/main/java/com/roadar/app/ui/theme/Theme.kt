package com.roadar.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = RoadarDarkPrimary,
    onPrimary = RoadarNavy,
    primaryContainer = RoadarDeepBlue,
    onPrimaryContainer = RoadarWhite,
    secondary = RoadarSkyBlue,
    onSecondary = RoadarNavy,
    tertiary = RoadarLightBlue,
    onTertiary = RoadarNavy,
    background = RoadarDarkBackground,
    onBackground = RoadarDarkText,
    surface = RoadarDarkSurface,
    onSurface = RoadarDarkText,
    surfaceVariant = RoadarDarkSurfaceVariant,
    onSurfaceVariant = RoadarDarkSecondaryText,
    outline = RoadarDarkBorder,
    outlineVariant = RoadarDarkBorder,
    secondaryContainer = RoadarDarkSurfaceVariant,
    onSecondaryContainer = RoadarDarkText
)

private val LightColorScheme = lightColorScheme(
    primary = RoadarRoyalBlue,
    onPrimary = RoadarWhite,
    primaryContainer = RoadarLightBlue,
    onPrimaryContainer = RoadarNavy,
    secondary = RoadarDeepBlue,
    onSecondary = RoadarWhite,
    tertiary = RoadarNavy,
    onTertiary = RoadarWhite,
    background = RoadarIceBlue,
    onBackground = RoadarText,
    surface = RoadarWhite,
    onSurface = RoadarText,
    surfaceVariant = RoadarIceBlue,
    onSurfaceVariant = RoadarSecondaryText,
    outline = RoadarLightBlue,
    outlineVariant = RoadarBorder,
    secondaryContainer = RoadarIceBlue,
    onSecondaryContainer = RoadarNavy
)

private val RoadarShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun ROADARTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        shapes = RoadarShapes,
        content = content
    )
}
