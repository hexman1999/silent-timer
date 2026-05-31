package com.silentapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_ACTIVE, false)) return

        val endTime = prefs.getLong(KEY_END_TIME, 0L)
        val now = System.currentTimeMillis()
        if (endTime <= now) {
            prefs.edit().clear().apply()
            return
        }

        val remainingSeconds = ((endTime - now) / 1000).toInt()
        val mode = prefs.getInt(KEY_MODE, MODE_SILENT)
        val presetId = prefs.getString(KEY_PRESET_ID, null)

        val serviceIntent = Intent(context, SilentTimerService::class.java).apply {
            action = SilentTimerService.ACTION_START
            putExtra(SilentTimerService.EXTRA_SECONDS, remainingSeconds)
            putExtra(SilentTimerService.EXTRA_MODE, mode)
            putExtra(SilentTimerService.EXTRA_PRESET_ID, presetId)
        }
        ContextCompat.startForegroundService(context, serviceIntent)
    }

    companion object {
        private const val PREFS_NAME = "silent_timer_state"
        private const val KEY_ACTIVE = "active"
        private const val KEY_END_TIME = "end_time"
        private const val KEY_MODE = "mode"
        private const val KEY_PRESET_ID = "preset_id"

        fun saveState(context: Context, endTime: Long, mode: Int, presetId: String?) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
                putBoolean(KEY_ACTIVE, true)
                putLong(KEY_END_TIME, endTime)
                putInt(KEY_MODE, mode)
                putString(KEY_PRESET_ID, presetId)
                apply()
            }
        }

        fun clearState(context: Context) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
        }
    }
}
