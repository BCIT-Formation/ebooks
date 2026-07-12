package com.ebooks.reader.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

enum class DisplayMode(val label: String) {
    NORMAL("Standard"),
    E_PAPER("E-Paper"),
    AMOLED("AMOLED"),
    NIGHT_READING("Night Reading");

    companion object {
        fun getColorScheme(displayMode: DisplayMode, isDarkMode: Boolean): ColorScheme {
            return when {
                displayMode == E_PAPER && !isDarkMode -> epaperLightScheme()
                displayMode == E_PAPER && isDarkMode -> epaperDarkScheme()
                displayMode == AMOLED && isDarkMode -> amoledDarkScheme()
                displayMode == NIGHT_READING -> nightReadingScheme()
                isDarkMode -> darkColorScheme(
                    primary = Primary,
                    secondary = Secondary,
                    tertiary = Tertiary,
                    background = DarkBackground,
                    surface = DarkSurface,
                    surfaceVariant = DarkSurfaceVariant,
                    onSurface = OnSurfaceDark,
                    onSurfaceVariant = OnSurfaceVariant
                )
                else -> lightColorScheme(
                    primary = Primary,
                    secondary = Secondary,
                    tertiary = Tertiary,
                    background = Background,
                    surface = Surface,
                    surfaceVariant = SurfaceVariant,
                    onSurface = OnSurface,
                    onSurfaceVariant = OnSurfaceVariant
                )
            }
        }
    }
}

private fun epaperLightScheme() = lightColorScheme(
    primary = Color(0xFF000000),
    secondary = Color(0xFF404040),
    tertiary = Color(0xFF606060),
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF5F5F5),
    onSurface = Color(0xFF000000),
    onSurfaceVariant = Color(0xFF808080)
)

private fun epaperDarkScheme() = darkColorScheme(
    primary = Color(0xFFFFFFFF),
    secondary = Color(0xFFC0C0C0),
    tertiary = Color(0xFF909090),
    background = Color(0xFF000000),
    surface = Color(0xFF101010),
    surfaceVariant = Color(0xFF202020),
    onSurface = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xFF909090)
)

private fun amoledDarkScheme() = darkColorScheme(
    primary = Primary,
    secondary = Secondary,
    tertiary = Tertiary,
    background = Color(0xFF000000),
    surface = Color(0xFF000000),
    surfaceVariant = Color(0xFF1A1A1A),
    onSurface = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xFFA0A0A0)
)

private fun nightReadingScheme() = darkColorScheme(
    primary = Color(0xFFFF9900),
    secondary = Color(0xFFFF6600),
    tertiary = Color(0xFFCC6600),
    background = Color(0xFF0A0A0A),
    surface = Color(0xFF141414),
    surfaceVariant = Color(0xFF1F1F1F),
    onSurface = Color(0xFFD4A574),
    onSurfaceVariant = Color(0xFF997755)
)
