package com.silentapp

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
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
    private var customMode = MODE_SILENT
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
        updateShortcuts()

        handleShortcutIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShortcutIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        updateDashboard()
        presets.clear()
        presets.addAll(presetManager.loadPresets())
        renderPresets()
        updateShortcuts()
        handler.post(tickRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tickRunnable)
    }

    private fun handleShortcutIntent(intent: Intent) {
        val seconds = intent.getIntExtra(EXTRA_SHORTCUT_SECONDS, 0)
        val mode = intent.getIntExtra(EXTRA_SHORTCUT_MODE, -1)
        val label = intent.getStringExtra(EXTRA_SHORTCUT_LABEL)
        if (seconds > 0 && mode >= 0 && label != null) {
            startTimer(seconds, mode, label)
        }
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
                card.layoutParams = LinearLayout.LayoutParams(0, 100.dp(), 1f).apply {
                    val m = 4.dp()
                    setMargins(m, 0, m, 0)
                }

                card.findViewById<TextView>(R.id.presetLabel).text = preset.label
                card.findViewById<TextView>(R.id.presetSub).text = preset.subText() + " · " + preset.modeLabel()

                if (editMode) {
                    val deleteBtn = card.findViewById<View>(R.id.presetDeleteBtn)
                    deleteBtn.visibility = View.VISIBLE
                    deleteBtn.setOnClickListener {
                        presets.remove(preset)
                        presetManager.savePresets(presets)
                        renderPresets()
                        updateShortcuts()
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
            addCard.layoutParams = LinearLayout.LayoutParams(0, 100.dp(), 1f)
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
            updateShortcuts()
        }
        dialog.setOnDelete { delPreset ->
            presets.removeAll { it.id == delPreset.id }
            presetManager.savePresets(presets)
            renderPresets()
            updateShortcuts()
        }
        dialog.show(supportFragmentManager, "presetEdit")
    }

    private fun setupCustomTimer() {
        findViewById<View>(R.id.customTimerText).setOnClickListener { showTimePicker() }
        findViewById<View>(R.id.btnSelectTime).setOnClickListener { showTimePicker() }

        findViewById<View>(R.id.btnSecInc).setOnClickListener {
            selectedSeconds = (selectedSeconds + 10).coerceAtMost(35999)
            updateCustomTimerDisplay()
        }
        findViewById<View>(R.id.btnSecDec).setOnClickListener {
            selectedSeconds = (selectedSeconds - 10).coerceAtLeast(0)
            updateCustomTimerDisplay()
        }

        findViewById<View>(R.id.btnSetSilentCustom).setOnClickListener {
            customMode = MODE_SILENT
            applyCustomTimer()
        }
        findViewById<View>(R.id.btnSetVibrateCustom).setOnClickListener {
            customMode = MODE_VIBRATE
            applyCustomTimer()
        }
        findViewById<View>(R.id.btnSetDndCustom).setOnClickListener {
            customMode = MODE_DND
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
            selectedSeconds = newSeconds.coerceAtMost(35999)
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
        val label = modeLabel(customMode)
        startTimer(selectedSeconds, customMode, label)
    }

    private fun startTimer(seconds: Int, mode: Int, label: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (mode == MODE_DND && !nm.isNotificationPolicyAccessGranted) {
            RingerModeManager.requestPolicyPermission(this)
            Toast.makeText(this, "DND access required", Toast.LENGTH_LONG).show()
            return
        }

        val intent = Intent(this, SilentTimerService::class.java).apply {
            action = SilentTimerService.ACTION_START
            putExtra(SilentTimerService.EXTRA_SECONDS, seconds)
            putExtra(SilentTimerService.EXTRA_MODE, mode)
        }
        ContextCompat.startForegroundService(this, intent)
        val display = formatDuration(seconds)
        Toast.makeText(this, "$label for $display", Toast.LENGTH_SHORT).show()
    }

    private fun updateShortcuts() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return
        val manager = getSystemService(ShortcutManager::class.java) ?: return

        val shortcuts = presets.take(5).mapIndexed { i, preset ->
            val shortcutIntent = Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra(EXTRA_SHORTCUT_SECONDS, preset.totalSeconds)
                putExtra(EXTRA_SHORTCUT_MODE, preset.mode)
                putExtra(EXTRA_SHORTCUT_LABEL, preset.label)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            }

            ShortcutInfo.Builder(this, "preset_$i")
                .setShortLabel(preset.label)
                .setLongLabel("${preset.label} · ${preset.subText()} · ${preset.modeLabel()}")
                .setIcon(Icon.createWithResource(this, android.R.drawable.ic_lock_silent_mode))
                .setIntent(shortcutIntent)
                .build()
        }

        manager.dynamicShortcuts = shortcuts
    }

    private fun updateDashboard() {
        updateStatusCard()
        updateActiveTimerCard()
    }

    private fun updateStatusCard() {
        val mode = RingerModeManager.getCurrentMode(this)
        val icon = findViewById<TextView>(R.id.statusIcon)
        val label = findViewById<TextView>(R.id.statusLabel)
        when (mode) {
            MODE_NORMAL -> {
                icon.text = "\uD83D\uDD14"
                label.text = getString(R.string.status_normal)
            }
            MODE_SILENT -> {
                icon.text = "\uD83D\uDD15"
                label.text = getString(R.string.status_silent)
            }
            MODE_VIBRATE -> {
                icon.text = "\uD83D\uDCF3"
                label.text = getString(R.string.status_vibrate)
            }
            MODE_DND -> {
                icon.text = "\uD83D\uDCF4"
                label.text = "DND"
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

            val modeLabel = modeLabel(SilentTimerService.activeMode)
            title.text = "$modeLabel — ${getString(R.string.active_timer)}"
        } else {
            card.visibility = View.GONE
        }
    }

    private fun Int.dp(): Int =
        (this * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_SHORTCUT_SECONDS = "shortcut_seconds"
        const val EXTRA_SHORTCUT_MODE = "shortcut_mode"
        const val EXTRA_SHORTCUT_LABEL = "shortcut_label"
    }
}
