package com.maximus.volumeboost

import android.content.ComponentName
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Quick Settings tile that toggles volume boost on/off using the last saved level.
 */
class BoostTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStartListening() {
        super.onStartListening()
        scope.launch {
            refreshTile()
        }
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            val prefs = BoostPreferences(applicationContext)
            val enabled = prefs.boostEnabled.first()
            if (enabled) {
                prefs.setBoostEnabled(false)
                BoostForegroundService.stop(applicationContext)
            } else {
                val level = prefs.boostLevel.first()
                prefs.setBoostEnabled(true)
                BoostForegroundService.start(applicationContext, level)
            }
            refreshTile()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun refreshTile() {
        val tile = qsTile ?: return
        val prefs = BoostPreferences(applicationContext)
        val enabled = prefs.boostEnabled.first()
        val level = prefs.boostLevel.first().toInt()
        tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_label)
        tile.contentDescription = getString(R.string.tile_label)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (enabled) {
                getString(R.string.tile_subtitle_on, level)
            } else {
                getString(R.string.tile_subtitle_off)
            }
        }
        tile.icon = Icon.createWithResource(this, R.drawable.ic_speaker)
        tile.updateTile()
    }

    companion object {
        fun componentName(packageName: String): ComponentName =
            ComponentName(packageName, BoostTileService::class.java.name)
    }
}
