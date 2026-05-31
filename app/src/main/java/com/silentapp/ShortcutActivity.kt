package com.silentapp

import android.app.NotificationManager
import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.Calendar

class ShortcutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
        finish()
    }

    private fun handleIntent(intent: Intent) {
        val seconds = intent.getIntExtra(EXTRA_SECONDS, 0)
        val mode = intent.getIntExtra(EXTRA_MODE, -1)
        val label = intent.getStringExtra(EXTRA_LABEL) ?: return
        val presetId = intent.getStringExtra(EXTRA_PRESET_ID)
        if (seconds <= 0 || mode < 0) return

        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (mode == MODE_DND && !nm.isNotificationPolicyAccessGranted) {
            RingerModeManager.requestPolicyPermission(this)
            Toast.makeText(this, "DND access required", Toast.LENGTH_LONG).show()
            return
        }

        val serviceIntent = Intent(this, SilentTimerService::class.java).apply {
            action = SilentTimerService.ACTION_START
            putExtra(SilentTimerService.EXTRA_SECONDS, seconds)
            putExtra(SilentTimerService.EXTRA_MODE, mode)
            putExtra(SilentTimerService.EXTRA_PRESET_ID, presetId)
        }
        ContextCompat.startForegroundService(this, serviceIntent)

        val endTime = System.currentTimeMillis() + seconds * 1000L
        val cal = Calendar.getInstance().apply { timeInMillis = endTime }
        val is24 = DateFormat.is24HourFormat(this)
        val endTimeStr = if (is24) {
            String.format("%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
        } else {
            val amPm = if (cal.get(Calendar.HOUR_OF_DAY) < 12) "AM" else "PM"
            val h12 = when (cal.get(Calendar.HOUR_OF_DAY)) {
                0 -> 12; 12 -> 12; else -> cal.get(Calendar.HOUR_OF_DAY) % 12
            }
            String.format("%d:%02d %s", h12, cal.get(Calendar.MINUTE), amPm)
        }
        val timeStr = formatDuration(seconds)
        Toast.makeText(this, "$label for $timeStr (until $endTimeStr)", Toast.LENGTH_LONG).show()
    }

    companion object {
        const val EXTRA_SECONDS = "shortcut_seconds"
        const val EXTRA_MODE = "shortcut_mode"
        const val EXTRA_LABEL = "shortcut_label"
        const val EXTRA_PRESET_ID = "shortcut_preset_id"
    }
}
