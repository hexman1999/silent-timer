package com.silentapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.text.format.DateFormat
import java.util.Calendar
import androidx.core.app.NotificationCompat

class SilentTimerService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var endTime = 0L
    private var totalDuration = 0L
    private var currentMode = MODE_SILENT
    private var running = false
    private var expectedRingerMode = AudioManager.RINGER_MODE_NORMAL

    private val remaining: Long
        get() = (endTime - System.currentTimeMillis()).coerceAtLeast(0)

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            if (remaining <= 0) {
                onTimerFinished()
                return
            }
            updateNotification()
            handler.postDelayed(this, 1000)
        }
    }

    private val ringerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!running) return
            val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
            if (audioManager.ringerMode != expectedRingerMode) {
                stopTimer()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        registerReceiver(
            ringerReceiver,
            IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION)
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(ringerReceiver)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val seconds = intent.getIntExtra(EXTRA_SECONDS, 0)
                val mode = intent.getIntExtra(EXTRA_MODE, MODE_SILENT)
                val presetId = intent.getStringExtra(EXTRA_PRESET_ID)
                startTimer(seconds, mode, presetId)
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

    private fun startTimer(seconds: Int, mode: Int, presetId: String? = null) {
        if (running) {
            running = false
            handler.removeCallbacks(tickRunnable)
        }
        expectedRingerMode = RingerModeManager.actualRingerMode(mode)
        RingerModeManager.applyMode(this, mode)
        handler.removeCallbacks(tickRunnable)
        running = true
        isTimerRunning = true
        endTime = System.currentTimeMillis() + seconds * 1000L
        totalDuration = seconds * 1000L
        currentMode = mode
        activeMode = mode
        activePresetId = presetId
        SilentTimerService.endTime = endTime
        updateNotification()
        handler.postDelayed(tickRunnable, 1000)
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    private fun extendTimer() {
        endTime += 10 * 60 * 1000L
        totalDuration += 10 * 60 * 1000L
        SilentTimerService.endTime = endTime
        updateNotification()
    }

    private fun stopTimer() {
        running = false
        isTimerRunning = false
        activePresetId = null
        handler.removeCallbacks(tickRunnable)
        RingerModeManager.applyMode(this, MODE_NORMAL)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun onTimerFinished() {
        running = false
        isTimerRunning = false
        activePresetId = null
        RingerModeManager.applyMode(this, MODE_NORMAL)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updateNotification() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val rem = remaining
        val totalSecs = rem / 1000
        val h = totalSecs / 3600
        val m = (totalSecs % 3600) / 60
        val s = totalSecs % 60
        val timeStr = if (h > 0) String.format("%d:%02d:%02d", h, m, s)
        else String.format("%d:%02d", m, s)

        val modeLabel = modeLabel(currentMode)
        val progressMax = (totalDuration / 1000).toInt()
        val progressCurrent = (rem / 1000).toInt()

        val cal = Calendar.getInstance().apply { timeInMillis = endTime }
        val endHour = cal.get(Calendar.HOUR_OF_DAY)
        val endMin = cal.get(Calendar.MINUTE)
        val is24 = DateFormat.is24HourFormat(this)
        val endTimeStr = if (is24) {
            String.format("%02d:%02d", endHour, endMin)
        } else {
            val amPm = if (endHour < 12) "AM" else "PM"
            val h12 = when (endHour) { 0 -> 12; 12 -> 12; else -> endHour % 12 }
            String.format("%d:%02d %s", h12, endMin, amPm)
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

        val notifText = getString(R.string.notif_remaining, timeStr, endTimeStr)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(modeLabel)
            .setContentText(timeStr)
            .setSubText(getString(R.string.notif_until, endTimeStr))
            .setStyle(NotificationCompat.BigTextStyle().bigText(notifText))
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(progressMax, progressCurrent, false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
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
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(R.string.notif_channel_desc)
                enableVibration(false)
                setSound(null, null)
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

        const val EXTRA_SECONDS = "seconds"
        const val EXTRA_MODE = "mode"
        const val EXTRA_PRESET_ID = "preset_id"

        var isTimerRunning = false
            private set
        var endTime = 0L
            private set
        var activeMode = MODE_SILENT
            private set
        var activePresetId: String? = null
            private set
    }
}
