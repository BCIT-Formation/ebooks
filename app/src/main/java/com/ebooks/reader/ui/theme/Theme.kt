package com.ebooks.reader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    secondary = Secondary,
    tertiary = Tertiary,
    background = Background,
    surface = Surface,
    surfaceVariant = SurfaceVariant,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = OnSurface,
    onSurface = OnSurface,
    onSurfaceVariant = OnSurfaceVariant,
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF7BB4F5),
    secondary = Secondary,
    tertiary = Tertiary,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onPrimary = Color(0xFF0A2A4A),
    onBackground = OnSurfaceDark,
    onSurface = OnSurfaceDark,
    onSurfaceVariant = Color(0xFFC7C7C7),
)

// Pure-black profile for OLED screens.
private val AmoledColorScheme = darkColorScheme(
    primary = AmoledPrimary,
    onPrimary = Color(0xFF00121F),
    secondary = AmoledPrimary,
    background = AmoledBackground,
    surface = AmoledSurface,
    surfaceVariant = AmoledSurfaceVariant,
    onBackground = AmoledOnSurface,
    onSurface = AmoledOnSurface,
    onSurfaceVariant = AmoledOnSurfaceVariant,
    outline = AmoledOutline,
)

// Maximum-contrast grayscale for e-ink/e-reader screens.
private val EinkColorScheme = lightColorScheme(
    primary = EinkPrimary,
    onPrimary = Color.White,
    secondary = EinkPrimary,
    onSecondary = Color.White,
    tertiary = EinkPrimary,
    background = EinkBackground,
    surface = EinkSurface,
    surfaceVariant = EinkSurfaceVariant,
    onBackground = EinkOnSurface,
    onSurface = EinkOnSurface,
    onSurfaceVariant = EinkOnSurfaceVariant,
    outline = EinkOutline,
    primaryContainer = Color(0xFFDCDCDC),
    onPrimaryContainer = EinkOnSurface,
    secondaryContainer = Color(0xFFDCDCDC),
    onSecondaryContainer = EinkOnSurface,
)

/**
 * App theme. The [displayMode] picks a fixed, high-contrast colour scheme —
 * we deliberately do NOT use Material You dynamic colours, because deriving the
 * palette from the wallpaper produced washed-out, hard-to-read buttons.
 */
@Composable
fun EbookReaderTheme(
    displayMode: DisplayMode = DisplayMode.LCD,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when (displayMode) {
        DisplayMode.EINK -> EinkColorScheme
        DisplayMode.AMOLED -> AmoledColorScheme
        DisplayMode.LCD -> if (darkTheme) DarkColorScheme else LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
