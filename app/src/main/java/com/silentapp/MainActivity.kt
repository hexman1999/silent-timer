package com.silentapp

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat

class MainActivity : AppCompatActivity() {

    private var selectedMinutes: Int = 0
    private var selectedSeconds: Int = 0
    private lateinit var timerText: TextView
    private lateinit var statusText: TextView
    private lateinit var btnSetSilent: Button
    private lateinit var btnSetVibrate: Button
    private lateinit var btnCancel: Button

    private val audioManager: AudioManager by lazy {
        getSystemService(AUDIO_SERVICE) as AudioManager
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        timerText = findViewById(R.id.timerText)
        statusText = findViewById(R.id.statusText)
        btnSetSilent = findViewById(R.id.btnSetSilent)
        btnSetVibrate = findViewById(R.id.btnSetVibrate)
        btnCancel = findViewById(R.id.btnCancel)

        checkPermissions()

        findViewById<Button>(R.id.btnSelectTime).setOnClickListener {
            showTimePicker()
        }

        btnSetSilent.setOnClickListener {
            applyMode(AudioManager.RINGER_MODE_SILENT)
        }

        btnSetVibrate.setOnClickListener {
            applyMode(AudioManager.RINGER_MODE_VIBRATE)
        }

        btnCancel.setOnClickListener {
            cancelTimer()
        }

        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun checkPermissions() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) {
            Toast.makeText(this, getString(R.string.perm_toast), Toast.LENGTH_LONG).show()
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

    private fun showTimePicker() {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(0)
            .setMinute(0)
            .setTitleText(R.string.select_time)
            .build()

        picker.addOnPositiveButtonClickListener {
            val hour = picker.hour
            val minute = picker.minute
            selectedMinutes = hour * 60 + minute
            selectedSeconds = 0
            updateTimerDisplay()
        }

        picker.show(supportFragmentManager, "timePicker")
    }

    private fun updateTimerDisplay() {
        val hours = selectedMinutes / 60
        val mins = selectedMinutes % 60
        timerText.text = String.format("%02d:%02d", hours, mins)
    }

    private fun applyMode(mode: Int) {
        if (selectedMinutes <= 0 && selectedSeconds <= 0) {
            Toast.makeText(this, getString(R.string.select_time_first), Toast.LENGTH_SHORT).show()
            return
        }

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            RingerModeManager.requestPolicyPermission(this)
            return
        }

        val intent = Intent(this, SilentTimerService::class.java).apply {
            action = SilentTimerService.ACTION_START
            putExtra(SilentTimerService.EXTRA_MINUTES, selectedMinutes)
            putExtra(SilentTimerService.EXTRA_MODE, mode)
        }
        ContextCompat.startForegroundService(this, intent)

        Toast.makeText(this, getString(R.string.timer_started), Toast.LENGTH_SHORT).show()
        updateStatus()
    }

    private fun cancelTimer() {
        val intent = Intent(this, SilentTimerService::class.java).apply {
            action = SilentTimerService.ACTION_CANCEL
        }
        startService(intent)
        Toast.makeText(this, getString(R.string.cancelled), Toast.LENGTH_SHORT).show()
    }

    private fun updateStatus() {
        val mode = audioManager.ringerMode
        val modeText = when (mode) {
            AudioManager.RINGER_MODE_NORMAL -> getString(R.string.status_normal)
            AudioManager.RINGER_MODE_SILENT -> getString(R.string.status_silent)
            AudioManager.RINGER_MODE_VIBRATE -> getString(R.string.status_vibrate)
            else -> "Unknown"
        }
        statusText.text = getString(R.string.status_format, modeText)
    }
}
