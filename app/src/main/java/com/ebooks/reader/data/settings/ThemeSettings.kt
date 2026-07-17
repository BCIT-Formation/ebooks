package com.ebooks.reader.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.themeDataStore by preferencesDataStore(name = "theme_settings")

enum class AppTheme(val value: String) {
    LIGHT("light"),
    DARK("dark"),
    SYSTEM("system");

    companion object {
        fun fromValue(value: String): AppTheme = entries.find { it.value == value } ?: LIGHT
    }
}

class ThemeSettings(private val context: Context) {

    private val themeKey = stringPreferencesKey("app_theme")

    val currentTheme: Flow<AppTheme> = context.themeDataStore.data.map { prefs ->
        AppTheme.fromValue(prefs[themeKey] ?: AppTheme.LIGHT.value)
    }

    suspend fun setTheme(theme: AppTheme) {
        context.themeDataStore.edit { prefs ->
            prefs[themeKey] = theme.value
        }
    }

    companion object {
        @Volatile
        private var instance: ThemeSettings? = null

        fun getInstance(context: Context): ThemeSettings {
            return instance ?: synchronized(this) {
                instance ?: ThemeSettings(context.applicationContext)
                    .also { instance = it }
            }
        }
    }
}
