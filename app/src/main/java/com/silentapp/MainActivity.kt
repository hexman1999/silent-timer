package com.silentapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat

class MainActivity : AppCompatActivity() {

    private var selectedSeconds = 0
    private var customMode = AudioManager.RINGER_MODE_SILENT
    private var editMode = false

    private lateinit var presetManager: PresetManager
    private val presets = mutableListOf<Preset>()

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

        presetManager = PresetManager(this)
        presets.addAll(presetManager.loadPresets())

        checkPermissions()
        setupCustomTimer()
        setupActiveTimerCard()
        setupEditButton()
        renderPresets()
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
            }
        }
    }

    private fun setupEditButton() {
        findViewById<MaterialButton>(R.id.btnEditPresets).setOnClickListener {
            editMode = !editMode
            renderPresets()
        }
    }

    private fun renderPresets() {
        val container = findViewById<LinearLayout>(R.id.presetsContainer)
        container.removeAllViews()
        val inflater = LayoutInflater.from(this)

        val btn = findViewById<MaterialButton>(R.id.btnEditPresets)
        btn.text = if (editMode) getString(R.string.done) else getString(R.string.edit)

        val rows = presets.chunked(2)
        for (rowPresets in rows) {
            val row = LinearLayout(this).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.HORIZONTAL
            }

            for (preset in rowPresets) {
                val card = inflater.inflate(R.layout.item_preset, row, false) as MaterialCardView
                card.layoutParams = LinearLayout.LayoutParams(0, 88.dp(), 1f).apply {
                    val m = 4.dp()
                    setMargins(m, 0, m, 0)
                }

                card.findViewById<TextView>(R.id.presetLabel).text = preset.label
                card.findViewById<TextView>(R.id.presetSub).text = preset.subText()

                if (editMode) {
                    val deleteBtn = card.findViewById<View>(R.id.presetDeleteBtn)
                    deleteBtn.visibility = View.VISIBLE
                    deleteBtn.setOnClickListener {
                        presets.remove(preset)
                        presetManager.savePresets(presets)
                        renderPresets()
                    }
                    card.setOnClickListener {
                        showPresetDialog(preset)
                    }
                    card.foreground = null
                } else {
                    card.findViewById<View>(R.id.presetDeleteBtn).visibility = View.GONE
                    card.setOnClickListener {
                        startTimer(preset.totalSeconds, preset.mode, preset.label)
                    }
                    card.isClickable = true
                    card.isFocusable = true
                }
                row.addView(card)
            }

            if (rowPresets.size == 1) {
                val spacer = android.widget.Space(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
                }
                row.addView(spacer)
            }

            container.addView(row)

            val params = row.layoutParams as ViewGroup.MarginLayoutParams
            params.bottomMargin = 8.dp()
            row.layoutParams = params
        }

        if (editMode) {
            val addRow = LinearLayout(this).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.HORIZONTAL
            }

            val addCard = inflater.inflate(R.layout.item_preset_add, container, false) as MaterialCardView
            addCard.layoutParams = LinearLayout.LayoutParams(0, 88.dp(), 1f)
            addCard.setOnClickListener { showPresetDialog(null) }

            addRow.addView(addCard)
            addRow.addView(android.widget.Space(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
            })
            container.addView(addRow)
        }
    }

    private fun showPresetDialog(preset: Preset?) {
        val dialog = PresetEditDialog.newInstance(preset)
        dialog.setOnSave { newPreset ->
            val idx = presets.indexOfFirst { it.id == newPreset.id }
            if (idx >= 0) {
                presets[idx] = newPreset
            } else {
                presets.add(newPreset)
            }
            presetManager.savePresets(presets)
            renderPresets()
        }
        dialog.setOnDelete { delPreset ->
            presets.removeAll { it.id == delPreset.id }
            presetManager.savePresets(presets)
            renderPresets()
        }
        dialog.show(supportFragmentManager, "presetEdit")
    }

    private fun setupCustomTimer() {
        findViewById<View>(R.id.customTimerText).setOnClickListener { showTimePicker() }
        findViewById<View>(R.id.btnSelectTime).setOnClickListener { showTimePicker() }

        findViewById<View>(R.id.btnSecInc).setOnClickListener {
            selectedSeconds = (selectedSeconds + 10).coerceAtMost(3599)
            updateCustomTimerDisplay()
        }
        findViewById<View>(R.id.btnSecDec).setOnClickListener {
            selectedSeconds = (selectedSeconds - 10).coerceAtLeast(0)
            updateCustomTimerDisplay()
        }

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
        val h = selectedSeconds / 3600
        val m = (selectedSeconds % 3600) / 60
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(h)
            .setMinute(m)
            .setTitleText(R.string.select_time)
            .build()

        picker.addOnPositiveButtonClickListener {
            val newSeconds = (picker.hour % 24) * 3600 + (picker.minute % 60) * 60 + (selectedSeconds % 60)
            selectedSeconds = newSeconds.coerceAtMost(3599)
            updateCustomTimerDisplay()
        }

        picker.show(supportFragmentManager, "timePicker")
    }

    private fun updateCustomTimerDisplay() {
        val h = selectedSeconds / 3600
        val m = (selectedSeconds % 3600) / 60
        val s = selectedSeconds % 60
        findViewById<TextView>(R.id.customTimerText).text =
            String.format("%02d:%02d", h * 60 + m, s)
        findViewById<TextView>(R.id.secondsDisplay).text =
            String.format("%02d", s)
    }

    private fun applyCustomTimer() {
        if (selectedSeconds <= 0) {
            Toast.makeText(this, R.string.select_time_first, Toast.LENGTH_SHORT).show()
            return
        }
        val label = if (customMode == AudioManager.RINGER_MODE_SILENT) "Silent" else "Vibrate"
        startTimer(selectedSeconds, customMode, label)
    }

    private fun startTimer(seconds: Int, mode: Int, label: String) {
        val intent = Intent(this, SilentTimerService::class.java).apply {
            action = SilentTimerService.ACTION_START
            putExtra(SilentTimerService.EXTRA_SECONDS, seconds)
            putExtra(SilentTimerService.EXTRA_MODE, mode)
        }
        ContextCompat.startForegroundService(this, intent)
        val display = when {
            seconds >= 3600 -> "${seconds / 3600}h ${(seconds % 3600) / 60}m ${seconds % 60}s"
            seconds >= 60 -> "${seconds / 60}m ${seconds % 60}s"
            else -> "${seconds}s"
        }
        Toast.makeText(this, "$label for $display", Toast.LENGTH_SHORT).show()
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
            val h = totalSecs / 3600
            val m = (totalSecs % 3600) / 60
            val s = totalSecs % 60
            timeText.text = if (h > 0)
                String.format("%d:%02d:%02d", h, m, s)
            else
                String.format("%d:%02d", m, s)

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

    private fun Int.dp(): Int =
        (this * resources.displayMetrics.density).toInt()
}
