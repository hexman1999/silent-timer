package com.silentapp

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat

class MainActivity : AppCompatActivity() {

    private data class Preset(
        val label: String, val sub: String, val minutes: Int, val mode: Int
    )

    private val presets = listOf(
        Preset("Silent", "30 min", 30, AudioManager.RINGER_MODE_SILENT),
        Preset("Vibrate", "15 min", 15, AudioManager.RINGER_MODE_VIBRATE),
        Preset("Silent", "1 hour", 60, AudioManager.RINGER_MODE_SILENT),
        Preset("Vibrate", "5 min", 5, AudioManager.RINGER_MODE_VIBRATE),
    )

    private var selectedMinutes = 0
    private var customMode = AudioManager.RINGER_MODE_SILENT

    private val audioManager by lazy {
        getSystemService(AUDIO_SERVICE) as AudioManager
    }

    private val handler = Handler(Looper.getMainLooper())
    private val tickRunnable = object : Runnable {
        override fun run() {
            updateActiveTimerCard()
            updateStatusCard()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkPermissions()
        setupPresets()
        setupCustomTimer()
        setupActiveTimerCard()
    }

    override fun onResume() {
        super.onResume()
        updateDashboard()
        handler.post(tickRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tickRunnable)
    }

    private fun checkPermissions() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) {
            Toast.makeText(this, R.string.perm_toast, Toast.LENGTH_LONG).show()
            RingerModeManager.requestPolicyPermission(this)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
            }
        }
    }

    private fun setupPresets() {
        val cards = listOf(
            R.id.preset1, R.id.preset2, R.id.preset3, R.id.preset4
        )
        val labelIds = listOf(
            R.id.preset1Label, R.id.preset2Label, R.id.preset3Label, R.id.preset4Label
        )
        val subIds = listOf(
            R.id.preset1Sub, R.id.preset2Sub, R.id.preset3Sub, R.id.preset4Sub
        )

        presets.forEachIndexed { i, preset ->
            findViewById<TextView>(labelIds[i]).text = preset.label
            findViewById<TextView>(subIds[i]).text = preset.sub
            findViewById<MaterialCardView>(cards[i]).setOnClickListener {
                startTimer(preset.minutes, preset.mode, preset.label)
            }
        }
    }

    private fun setupCustomTimer() {
        findViewById<View>(R.id.customTimerText).apply {
            setOnClickListener { showTimePicker() }
        }
        findViewById<View>(R.id.btnSelectTime).setOnClickListener { showTimePicker() }
        findViewById<View>(R.id.btnSetSilentCustom).setOnClickListener {
            customMode = AudioManager.RINGER_MODE_SILENT
            applyCustomTimer()
        }
        findViewById<View>(R.id.btnSetVibrateCustom).setOnClickListener {
            customMode = AudioManager.RINGER_MODE_VIBRATE
            applyCustomTimer()
        }
    }

    private fun setupActiveTimerCard() {
        findViewById<View>(R.id.btnExtend).setOnClickListener {
            startService(Intent(this, SilentTimerService::class.java).apply {
                action = SilentTimerService.ACTION_EXTEND
            })
        }
        findViewById<View>(R.id.btnCancelTimer).setOnClickListener {
            startService(Intent(this, SilentTimerService::class.java).apply {
                action = SilentTimerService.ACTION_CANCEL
            })
        }
    }

    private fun showTimePicker() {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(0)
            .setMinute(0)
            .setTitleText(R.string.select_time)
            .build()

        picker.addOnPositiveButtonClickListener {
            val h = picker.hour
            val m = picker.minute
            selectedMinutes = h * 60 + m
            updateCustomTimerDisplay()
        }

        picker.show(supportFragmentManager, "timePicker")
    }

    private fun updateCustomTimerDisplay() {
        val h = selectedMinutes / 60
        val m = selectedMinutes % 60
        findViewById<TextView>(R.id.customTimerText).text =
            String.format("%02d:%02d", h, m)
    }

    private fun applyCustomTimer() {
        if (selectedMinutes <= 0) {
            Toast.makeText(this, R.string.select_time_first, Toast.LENGTH_SHORT).show()
            return
        }
        val label = if (customMode == AudioManager.RINGER_MODE_SILENT) "Silent" else "Vibrate"
        startTimer(selectedMinutes, customMode, label)
    }

    private fun startTimer(minutes: Int, mode: Int, label: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) {
            RingerModeManager.requestPolicyPermission(this)
            return
        }

        val intent = Intent(this, SilentTimerService::class.java).apply {
            action = SilentTimerService.ACTION_START
            putExtra(SilentTimerService.EXTRA_MINUTES, minutes)
            putExtra(SilentTimerService.EXTRA_MODE, mode)
        }
        ContextCompat.startForegroundService(this, intent)

        Toast.makeText(this, "$label for $minutes min", Toast.LENGTH_SHORT).show()
    }

    private fun updateDashboard() {
        updateStatusCard()
        updateActiveTimerCard()
    }

    private fun updateStatusCard() {
        val mode = audioManager.ringerMode
        val icon = findViewById<TextView>(R.id.statusIcon)
        val label = findViewById<TextView>(R.id.statusLabel)
        when (mode) {
            AudioManager.RINGER_MODE_NORMAL -> {
                icon.text = "\uD83D\uDD14"
                label.text = getString(R.string.status_normal)
            }
            AudioManager.RINGER_MODE_SILENT -> {
                icon.text = "\uD83D\uDD15"
                label.text = getString(R.string.status_silent)
            }
            AudioManager.RINGER_MODE_VIBRATE -> {
                icon.text = "\uD83D\uDCF3"
                label.text = getString(R.string.status_vibrate)
            }
        }
    }

    private fun updateActiveTimerCard() {
        val card = findViewById<View>(R.id.activeTimerCard)
        val timeText = findViewById<TextView>(R.id.activeTimerTime)
        val title = findViewById<TextView>(R.id.activeTimerTitle)

        if (SilentTimerService.isTimerRunning) {
            card.visibility = View.VISIBLE
            val totalSecs = SilentTimerService.remainingMillis / 1000
            val mins = totalSecs / 60
            val secs = totalSecs % 60
            timeText.text = String.format("%d:%02d", mins, secs)

            val modeLabel = when (SilentTimerService.activeMode) {
                AudioManager.RINGER_MODE_SILENT -> getString(R.string.status_silent)
                AudioManager.RINGER_MODE_VIBRATE -> getString(R.string.status_vibrate)
                else -> ""
            }
            title.text = "$modeLabel — ${getString(R.string.active_timer)}"
        } else {
            card.visibility = View.GONE
        }
    }
}
