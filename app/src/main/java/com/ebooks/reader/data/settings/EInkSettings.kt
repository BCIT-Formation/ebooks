package com.ebooks.reader.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.einkDataStore by preferencesDataStore(name = "eink_settings")

/**
 * Settings for E-Ink mode and optimizations
 */
class EInkSettings(private val context: Context) {

    private val einkEnabledKey = booleanPreferencesKey("eink_enabled")
    private val disableAnimationsKey = booleanPreferencesKey("disable_animations")
    private val forceGrayscaleKey = booleanPreferencesKey("force_grayscale")
    private val volumeKeyNavigationKey = booleanPreferencesKey("volume_key_navigation")
    private val increaseContrastKey = booleanPreferencesKey("increase_contrast")

    val isEInkEnabled: Flow<Boolean> = context.einkDataStore.data.map { prefs ->
        prefs[einkEnabledKey] ?: false
    }

    val isAnimationsDisabled: Flow<Boolean> = context.einkDataStore.data.map { prefs ->
        prefs[disableAnimationsKey] ?: true
    }

    val isGrayscaleForced: Flow<Boolean> = context.einkDataStore.data.map { prefs ->
        prefs[forceGrayscaleKey] ?: true
    }

    val isVolumeKeyNavigationEnabled: Flow<Boolean> = context.einkDataStore.data.map { prefs ->
        prefs[volumeKeyNavigationKey] ?: true
    }

    val isContrastIncreased: Flow<Boolean> = context.einkDataStore.data.map { prefs ->
        prefs[increaseContrastKey] ?: true
    }

    suspend fun setEInkEnabled(enabled: Boolean) {
        context.einkDataStore.edit { prefs ->
            prefs[einkEnabledKey] = enabled
        }
    }

    suspend fun setDisableAnimations(disable: Boolean) {
        context.einkDataStore.edit { prefs ->
            prefs[disableAnimationsKey] = disable
        }
    }

    suspend fun setForceGrayscale(force: Boolean) {
        context.einkDataStore.edit { prefs ->
            prefs[forceGrayscaleKey] = force
        }
    }

    suspend fun setVolumeKeyNavigation(enabled: Boolean) {
        context.einkDataStore.edit { prefs ->
            prefs[volumeKeyNavigationKey] = enabled
        }
    }

    suspend fun setIncreaseContrast(increase: Boolean) {
        context.einkDataStore.edit { prefs ->
            prefs[increaseContrastKey] = increase
        }
    }

    companion object {
        @Volatile
        private var instance: EInkSettings? = null

        fun getInstance(context: Context): EInkSettings {
            return instance ?: synchronized(this) {
                instance ?: EInkSettings(context.applicationContext)
                    .also { instance = it }
            }
        }
    }
}
