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

    val boostLevel: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[boostLevelKey] ?: 50f
    }

    val boostEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[boostEnabledKey] ?: false
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
}
