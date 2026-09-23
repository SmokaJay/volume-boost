package com.maximus.volumeboost

import android.content.Context
import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer
import android.util.Log

/**
 * Applies digital gain via [LoudnessEnhancer] and can max system volume streams.
 * Without root, Android cannot exceed the hardware maximum — digital gain
 * amplifies the signal (which can clip/distort) but cannot push the speaker
 * beyond its physical limit.
 */
class LoudnessBoostController(private val context: Context) {

    companion object {
        private const val TAG = "LoudnessBoost"
        /** Max targetGain in millibels (~25 dB) — same ballpark as commercial boosters. */
        const val MAX_GAIN_MB = 2500
        const val AUDIO_SESSION_GLOBAL = 0
    }

    private var enhancer: LoudnessEnhancer? = null
    private var lastError: String? = null

    fun getLastError(): String? = lastError

    /**
     * Enable boost at [levelPercent] 0–100 mapped to 0–[MAX_GAIN_MB] mB.
     * @return true if enhancer attached successfully
     */
    fun enable(levelPercent: Float): Boolean {
        lastError = null
        val gainMb = ((levelPercent.coerceIn(0f, 100f) / 100f) * MAX_GAIN_MB).toInt()
        return try {
            if (enhancer == null) {
                enhancer = LoudnessEnhancer(AUDIO_SESSION_GLOBAL)
            }
            enhancer?.setTargetGain(gainMb)
            enhancer?.enabled = true
            Log.i(TAG, "Boost enabled at ${gainMb}mB (session $AUDIO_SESSION_GLOBAL)")
            true
        } catch (e: UnsupportedOperationException) {
            lastError = "This device does not support global LoudnessEnhancer."
            Log.w(TAG, "UnsupportedOperationException", e)
            releaseQuietly()
            false
        } catch (e: RuntimeException) {
            lastError = "Could not attach boost: ${e.message ?: "unknown error"}"
            Log.w(TAG, "Failed to enable LoudnessEnhancer", e)
            releaseQuietly()
            false
        }
    }

    fun updateGain(levelPercent: Float) {
        if (enhancer == null) return
        val gainMb = ((levelPercent.coerceIn(0f, 100f) / 100f) * MAX_GAIN_MB).toInt()
        try {
            enhancer?.setTargetGain(gainMb)
        } catch (e: RuntimeException) {
            lastError = "Failed to update gain: ${e.message}"
            Log.w(TAG, "updateGain failed", e)
        }
    }

    fun disable() {
        try {
            enhancer?.enabled = false
        } catch (_: RuntimeException) {
        }
        releaseQuietly()
        Log.i(TAG, "Boost disabled")
    }

    fun isActive(): Boolean = enhancer?.enabled == true

    /** Max STREAM_MUSIC plus RING, ALARM, NOTIFICATION, SYSTEM. */
    fun maxAllVolumes() {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val streams = intArrayOf(
            AudioManager.STREAM_MUSIC,
            AudioManager.STREAM_RING,
            AudioManager.STREAM_ALARM,
            AudioManager.STREAM_NOTIFICATION,
            AudioManager.STREAM_SYSTEM
        )
        for (stream in streams) {
            try {
                val max = am.getStreamMaxVolume(stream)
                am.setStreamVolume(stream, max, 0)
            } catch (e: SecurityException) {
                Log.w(TAG, "Cannot set stream $stream", e)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Invalid stream $stream", e)
            }
        }
    }

    private fun releaseQuietly() {
        try {
            enhancer?.release()
        } catch (_: RuntimeException) {
        }
        enhancer = null
    }
}
