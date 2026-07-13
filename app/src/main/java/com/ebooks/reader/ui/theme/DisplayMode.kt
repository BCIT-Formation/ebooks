package com.ebooks.reader.ui.theme

import android.content.Context

/**
 * App-wide display profile that tunes the whole UI (toolbars, dialogs, library)
 * to the screen technology. Independent of the in-reader page themes.
 *
 * - [LCD]    — standard high-contrast colour UI, follows the system light/dark mode.
 * - [AMOLED] — pure-black surfaces so OLED pixels switch off (saves battery, deep blacks).
 * - [EINK]   — maximum-contrast black-on-white, no colour, for e-ink / e-reader screens.
 */
enum class DisplayMode {
    LCD, AMOLED, EINK;

    companion object {
        private const val PREFS = "app_prefs"
        private const val KEY = "display_mode"

        fun load(context: Context): DisplayMode {
            val name = context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY, LCD.name)
            return entries.firstOrNull { it.name == name } ?: LCD
        }

        fun save(context: Context, mode: DisplayMode) {
            context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY, mode.name)
                .apply()
        }
    }
}
