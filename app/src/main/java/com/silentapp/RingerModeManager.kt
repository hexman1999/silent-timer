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
    }

    fun setVibrate(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
    }

    fun setNormal(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
    }

    fun setDnd(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.isNotificationPolicyAccessGranted) {
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
        }
    }

    fun getCurrentMode(context: Context): Int {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return if (nm.isNotificationPolicyAccessGranted &&
            nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_NONE
        ) {
            MODE_DND
        } else {
            audioManager.ringerMode
        }
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
