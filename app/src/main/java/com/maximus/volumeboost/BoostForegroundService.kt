package com.maximus.volumeboost

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class BoostForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "volume_boost_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.maximus.volumeboost.START"
        const val ACTION_STOP = "com.maximus.volumeboost.STOP"
        const val ACTION_UPDATE_GAIN = "com.maximus.volumeboost.UPDATE_GAIN"
        const val EXTRA_LEVEL = "boost_level"

        @Volatile
        var isRunning: Boolean = false
            private set

        fun start(context: Context, level: Float) {
            val intent = Intent(context, BoostForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_LEVEL, level)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, BoostForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun updateGain(context: Context, level: Float) {
            if (!isRunning) return
            val intent = Intent(context, BoostForegroundService::class.java).apply {
                action = ACTION_UPDATE_GAIN
                putExtra(EXTRA_LEVEL, level)
            }
            context.startService(intent)
        }
    }

    private lateinit var controller: LoudnessBoostController
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        controller = LoudnessBoostController(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                controller.disable()
                isRunning = false
                serviceScope.launch {
                    BoostPreferences(applicationContext).setBoostEnabled(false)
                }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_UPDATE_GAIN -> {
                val level = intent.getFloatExtra(EXTRA_LEVEL, 50f)
                controller.updateGain(level)
                return START_STICKY
            }
            else -> {
                val level = intent?.getFloatExtra(EXTRA_LEVEL, 50f) ?: 50f
                val ok = controller.enable(level)
                if (!ok) {
                    sendFailureBroadcast(controller.getLastError())
                    serviceScope.launch {
                        BoostPreferences(applicationContext).setBoostEnabled(false)
                    }
                    stopSelf()
                    return START_NOT_STICKY
                }
                val notification = buildNotification(level)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceCompat.startForeground(
                        this,
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                isRunning = true
                return START_STICKY
            }
        }
    }

    override fun onDestroy() {
        controller.disable()
        isRunning = false
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_desc)
            setShowBadge(false)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(level: Float): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, BoostForegroundService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val pct = level.toInt()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text_level, pct))
            .setSmallIcon(R.drawable.ic_speaker)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .addAction(0, getString(R.string.notification_turn_off), stopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun sendFailureBroadcast(message: String?) {
        val intent = Intent(MainActivity.ACTION_BOOST_FAILED).apply {
            setPackage(packageName)
            putExtra(MainActivity.EXTRA_ERROR, message ?: "Boost failed")
        }
        sendBroadcast(intent)
    }
}
