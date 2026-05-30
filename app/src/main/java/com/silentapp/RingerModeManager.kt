package com.silentapp

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager

object RingerModeManager {

    fun applyMode(context: Context, mode: Int) {
        when (mode) {
            MODE_DND -> setDnd(context)
            MODE_SILENT -> setSilent(context)
            MODE_VIBRATE -> setVibrate(context)
            MODE_NORMAL -> setNormal(context)
        }
    }

    fun setSilent(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
        clearDndIfGranted(context)
    }

    fun setVibrate(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
    }

    fun setNormal(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
        clearDndIfGranted(context)
    }

    fun setDnd(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.isNotificationPolicyAccessGranted) {
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
        }
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
    }

    private fun clearDndIfGranted(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.isNotificationPolicyAccessGranted) {
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        }
    }

    fun getCurrentMode(context: Context): Int {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.isNotificationPolicyAccessGranted) {
            when (nm.currentInterruptionFilter) {
                NotificationManager.INTERRUPTION_FILTER_NONE,
                NotificationManager.INTERRUPTION_FILTER_PRIORITY,
                NotificationManager.INTERRUPTION_FILTER_ALARMS -> return MODE_DND
            }
        }
        return audioManager.ringerMode
    }

    fun actualRingerMode(mode: Int): Int = when (mode) {
        MODE_DND -> AudioManager.RINGER_MODE_NORMAL
        MODE_SILENT -> AudioManager.RINGER_MODE_SILENT
        MODE_VIBRATE -> AudioManager.RINGER_MODE_VIBRATE
        else -> AudioManager.RINGER_MODE_NORMAL
    }

    fun requestPolicyPermission(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) {
            val intent = android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS
            context.startActivity(
                android.content.Intent(intent).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
