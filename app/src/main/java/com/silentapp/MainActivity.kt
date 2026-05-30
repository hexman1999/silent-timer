package com.silentapp

import android.app.NotificationManager
import android.media.AudioManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.util.concurrent.TimeUnit

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
            cancelScheduledRestore()
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

        audioManager.ringerMode = mode

        val targetMode = AudioManager.RINGER_MODE_NORMAL
        val inputData = Data.Builder()
            .putInt(SilentModeWorker.KEY_TARGET_MODE, targetMode)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<SilentModeWorker>()
            .setInitialDelay(selectedMinutes.toLong(), TimeUnit.MINUTES)
            .setInputData(inputData)
            .addTag("silent_restore")
            .build()

        WorkManager.getInstance(this).cancelAllWorkByTag("silent_restore")
        WorkManager.getInstance(this).enqueue(workRequest)

        val label = if (mode == AudioManager.RINGER_MODE_SILENT) "Silent" else "Vibrate"
        Toast.makeText(
            this,
            "$label for $selectedMinutes minutes",
            Toast.LENGTH_SHORT
        ).show()
        updateStatus()
    }

    private fun cancelScheduledRestore() {
        WorkManager.getInstance(this).cancelAllWorkByTag("silent_restore")
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
