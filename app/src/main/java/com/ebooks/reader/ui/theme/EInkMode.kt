package com.ebooks.reader.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

/**
 * E-Ink mode optimizations for e-readers (Boox, Kobo, etc.)
 * - Disables animations
 * - Forces pure black/white contrast
 * - Optimizes for grayscale displays
 */
object EInkMode {

    /**
     * Detect if the device is an e-reader (Boox, Kobo, PocketBook, etc.)
     */
    fun isEReaderDevice(context: Context): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val model = Build.MODEL.lowercase()
        val device = Build.DEVICE.lowercase()

        val eReaderPatterns = listOf(
            "boox", "kobo", "pocketbook", "kindle", "inkpad",
            "onyx", "boyue", "tolino", "icarus"
        )

        return eReaderPatterns.any { pattern ->
            manufacturer.contains(pattern) || model.contains(pattern) || device.contains(pattern)
        }
    }

    /**
     * Check if device has e-ink display capability
     */
    fun hasEInkDisplay(): Boolean {
        return try {
            // Check for known e-ink properties
            val features = arrayOf(
                "android.hardware.type.e_ink",
                "android.hardware.screen.monochrome"
            )

            val runtime = Runtime.getRuntime()
            features.any { feature ->
                try {
                    val process = runtime.exec("getprop ro.hardware.eink")
                    val reader = process.inputStream.bufferedReader()
                    val result = reader.readText().trim()
                    result == "true" || result == "1"
                } catch (e: Exception) {
                    false
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get E-Ink optimized light color scheme
     * Pure black text on white background for maximum contrast
     */
    fun getEInkLightColorScheme(): ColorScheme {
        return lightColorScheme(
            primary = Color.Black,
            onPrimary = Color.White,
            secondary = Color.Black,
            onSecondary = Color.White,
            tertiary = Color.Black,
            onTertiary = Color.White,
            background = Color.White,
            onBackground = Color.Black,
            surface = Color.White,
            onSurface = Color.Black,
            surfaceVariant = Color(0xF0F0F0),
            onSurfaceVariant = Color(0x333333),
            error = Color.Black,
            onError = Color.White,
            outline = Color.Black,
            outlineVariant = Color(0x666666),
            scrim = Color.Black
        )
    }

    /**
     * Get E-Ink optimized dark color scheme
     * Maximum contrast for readability
     */
    fun getEInkDarkColorScheme(): ColorScheme {
        return darkColorScheme(
            primary = Color.White,
            onPrimary = Color.Black,
            secondary = Color.White,
            onSecondary = Color.Black,
            tertiary = Color.White,
            onTertiary = Color.Black,
            background = Color.Black,
            onBackground = Color.White,
            surface = Color.Black,
            onSurface = Color.White,
            surfaceVariant = Color(0x1A1A1A),
            onSurfaceVariant = Color(0xCCCCCC),
            error = Color.White,
            onError = Color.Black,
            outline = Color.White,
            outlineVariant = Color(0x999999),
            scrim = Color.White
        )
    }

    /**
     * Composition locals for E-Ink mode configuration
     */
    data class EInkConfig(
        val enabledEInkMode: Boolean = false,
        val disableAnimations: Boolean = true,
        val forceGrayscale: Boolean = true,
        val volumeKeyNavigation: Boolean = true,
        val increaseContrast: Boolean = true
    )

    /**
     * JavaScript to disable animations and optimize for e-readers
     */
    fun getEInkOptimizationScript(): String {
        return """
            (function() {
                // Disable all CSS animations
                let style = document.createElement('style');
                style.textContent = `
                    * {
                        animation: none !important;
                        transition: none !important;
                        -webkit-animation: none !important;
                        -webkit-transition: none !important;
                    }

                    body, * {
                        -webkit-font-smoothing: antialiased;
                        color-scheme: light dark;
                    }

                    img {
                        image-rendering: pixelated;
                        image-rendering: crisp-edges;
                    }
                `;
                document.head.appendChild(style);

                // Disable smooth scrolling
                document.documentElement.style.scrollBehavior = 'auto';

                // Prevent reflow/repaint on scroll
                if (window.innerWidth && window.innerHeight) {
                    document.addEventListener('scroll', function(e) {
                        e.preventDefault();
                    }, { passive: false });
                }
            })();
        """.trimIndent()
    }

    /**
     * Convert color to grayscale
     */
    fun toGrayscale(color: Color): Color {
        val rgb = color.value.toLong()
        val r = ((rgb shr 16) and 0xFF) / 255f
        val g = ((rgb shr 8) and 0xFF) / 255f
        val b = (rgb and 0xFF) / 255f
        val alpha = (rgb shr 24 and 0xFF) / 255f

        // Luminance calculation (standardized)
        val gray = 0.299f * r + 0.587f * g + 0.114f * b

        return Color(gray, gray, gray, alpha)
    }

    /**
     * Increase contrast of a color
     */
    fun increaseContrast(color: Color, factor: Float = 1.5f): Color {
        val rgb = color.value.toLong()
        val r = ((rgb shr 16) and 0xFF) / 255f
        val g = ((rgb shr 8) and 0xFF) / 255f
        val b = (rgb and 0xFF) / 255f
        val alpha = (rgb shr 24 and 0xFF) / 255f

        // Push toward white or black based on luminance
        val luminance = 0.299f * r + 0.587f * g + 0.114f * b

        val newR = if (luminance > 0.5) (r * factor).coerceAtMost(1f) else (r / factor).coerceAtLeast(0f)
        val newG = if (luminance > 0.5) (g * factor).coerceAtMost(1f) else (g / factor).coerceAtLeast(0f)
        val newB = if (luminance > 0.5) (b * factor).coerceAtMost(1f) else (b / factor).coerceAtLeast(0f)

        return Color(newR, newG, newB, alpha)
    }
}

/**
 * E-Ink PageTurner handler for volume key navigation
 * Can be used in Activity.onKeyDown() to handle volume buttons
 */
class EInkPageTurner(
    private val onPagePrevious: () -> Unit,
    private val onPageNext: () -> Unit
) {

    fun handleVolumeKey(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        return when (keyCode) {
            android.view.KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (event?.action == android.view.KeyEvent.ACTION_UP) {
                    onPageNext()
                    true
                } else {
                    false
                }
            }

            android.view.KeyEvent.KEYCODE_VOLUME_UP -> {
                if (event?.action == android.view.KeyEvent.ACTION_UP) {
                    onPagePrevious()
                    true
                } else {
                    false
                }
            }

            else -> false
        }
    }

    companion object {
        const val TAG = "EInkPageTurner"
    }
}
