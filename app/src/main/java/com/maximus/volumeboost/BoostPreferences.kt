package com.maximus.volumeboost

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "volume_boost_prefs")

class BoostPreferences(private val context: Context) {
    private val boostLevelKey = floatPreferencesKey("boost_level")
    private val boostEnabledKey = booleanPreferencesKey("boost_enabled")
    private val softCapKey = booleanPreferencesKey("soft_cap")
    private val batteryNudgeDismissedKey = booleanPreferencesKey("battery_nudge_dismissed")

    val boostLevel: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[boostLevelKey] ?: 50f
    }

    val boostEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[boostEnabledKey] ?: false
    }

    /** When true, boost level is capped at [SOFT_CAP_PERCENT]. Default off. */
    val softCap: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[softCapKey] ?: false
    }

    val batteryNudgeDismissed: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[batteryNudgeDismissedKey] ?: false
    }

    suspend fun setBoostLevel(level: Float) {
        context.dataStore.edit { prefs ->
            prefs[boostLevelKey] = level.coerceIn(0f, 100f)
        }
    }

    suspend fun setBoostEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[boostEnabledKey] = enabled
        }
    }

    suspend fun setSoftCap(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[softCapKey] = enabled
        }
    }

    suspend fun setBatteryNudgeDismissed(dismissed: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[batteryNudgeDismissedKey] = dismissed
        }
    }

    companion object {
        const val SOFT_CAP_PERCENT = 60f
        /** Approximate: MAX_GAIN_MB/100 = 25 dB at 100%. */
        const val MAX_GAIN_DB = 25f
    }
}
