package com.maximus.volumeboost

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Restarts [BoostForegroundService] after reboot when the user left boost enabled.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = BoostPreferences(appContext)
                val enabled = prefs.boostEnabled.first()
                if (enabled) {
                    val level = prefs.boostLevel.first()
                    BoostForegroundService.start(appContext, level)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
