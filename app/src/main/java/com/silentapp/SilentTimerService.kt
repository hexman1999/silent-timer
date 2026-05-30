package com.silentapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

class SilentTimerService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var remainingMillis = 0L
    private var currentMode = AudioManager.RINGER_MODE_SILENT
    private var running = false

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            remainingMillis -= 1000
            this@SilentTimerService.remainingMillis = remainingMillis
            if (remainingMillis <= 0) {
                onTimerFinished()
                return
            }
            updateNotification()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val minutes = intent.getIntExtra(EXTRA_MINUTES, 0)
                val mode = intent.getIntExtra(EXTRA_MODE, AudioManager.RINGER_MODE_SILENT)
                val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
                audioManager.ringerMode = mode
                startTimer(minutes, mode)
            }
            ACTION_EXTEND -> {
                extendTimer()
            }
            ACTION_CANCEL -> {
                stopTimer()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startTimer(minutes: Int, mode: Int) {
        handler.removeCallbacks(tickRunnable)
        running = true
        isTimerRunning = true
        remainingMillis = minutes * 60 * 1000L
        this@SilentTimerService.remainingMillis = remainingMillis
        currentMode = mode
        activeMode = mode
        updateNotification()
        handler.postDelayed(tickRunnable, 1000)

        startForeground(NOTIFICATION_ID, buildNotification())
    }

    private fun extendTimer() {
        remainingMillis += 10 * 60 * 1000L
        this@SilentTimerService.remainingMillis = remainingMillis
        updateNotification()
    }

    private fun stopTimer() {
        running = false
        isTimerRunning = false
        handler.removeCallbacks(tickRunnable)
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun onTimerFinished() {
        running = false
        isTimerRunning = false
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updateNotification() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val totalSecs = remainingMillis / 1000
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        val timeStr = String.format("%d:%02d", mins, secs)

        val modeLabel = when (currentMode) {
            AudioManager.RINGER_MODE_SILENT -> getString(R.string.notif_silent)
            AudioManager.RINGER_MODE_VIBRATE -> getString(R.string.notif_vibrate)
            else -> getString(R.string.notif_silent)
        }

        val extendIntent = Intent(this, SilentTimerService::class.java).apply {
            action = ACTION_EXTEND
        }
        val cancelIntent = Intent(this, SilentTimerService::class.java).apply {
            action = ACTION_CANCEL
        }

        val extendPendingIntent = PendingIntent.getService(
            this, REQ_EXTEND, extendIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val cancelPendingIntent = PendingIntent.getService(
            this, REQ_CANCEL, cancelIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(modeLabel)
            .setContentText(getString(R.string.notif_remaining, timeStr))
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_input_add,
                getString(R.string.notif_extend),
                extendPendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.notif_stop),
                cancelPendingIntent
            )
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_desc)
            }
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "silent_timer"
        const val NOTIFICATION_ID = 1001
        const val REQ_EXTEND = 2001
        const val REQ_CANCEL = 2002

        const val ACTION_START = "com.silentapp.START_TIMER"
        const val ACTION_EXTEND = "com.silentapp.EXTEND_TIMER"
        const val ACTION_CANCEL = "com.silentapp.CANCEL_TIMER"

        const val EXTRA_MINUTES = "minutes"
        const val EXTRA_MODE = "mode"

        var isTimerRunning = false
            private set
        var remainingMillis = 0L
            private set
        var activeMode = AudioManager.RINGER_MODE_SILENT
            private set
    }
}
