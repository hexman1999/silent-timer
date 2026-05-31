package com.silentapp

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object RingerModeManager {

    fun applyMode(context: Context, mode: Int) {
        when (mode) {
            MODE_DND -> setDnd(context)
            MODE_SILENT -> setSilent(context)
            MODE_VIBRATE -> setVibrate(context)
            MODE_NORMAL -> setNormal(context)
        }
        vibrateShort(context)
    }

    private fun vibrateShort(context: Context) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(200)
        }
    }

    fun setSilent(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.isNotificationPolicyAccessGranted) {
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        }
    }

    fun setVibrate(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
    }

    fun setNormal(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.isNotificationPolicyAccessGranted) {
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        }
    }

    fun setDnd(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.isNotificationPolicyAccessGranted) {
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
        }
    }

    fun getCurrentMode(context: Context): Int {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        when (audioManager.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (nm.isNotificationPolicyAccessGranted &&
                    nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_NONE
                ) {
                    return MODE_DND
                }
                return MODE_SILENT
            }
            AudioManager.RINGER_MODE_VIBRATE -> return MODE_VIBRATE
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.isNotificationPolicyAccessGranted) {
            when (nm.currentInterruptionFilter) {
                NotificationManager.INTERRUPTION_FILTER_NONE,
                NotificationManager.INTERRUPTION_FILTER_PRIORITY,
                NotificationManager.INTERRUPTION_FILTER_ALARMS -> return MODE_DND
            }
        }
        return MODE_NORMAL
    }

    fun actualRingerMode(mode: Int): Int = when (mode) {
        MODE_DND -> AudioManager.RINGER_MODE_SILENT
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
