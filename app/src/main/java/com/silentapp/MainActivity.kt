package com.silentapp

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.transition.TransitionManager
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
import com.google.android.material.color.DynamicColors

class MainActivity : AppCompatActivity() {

    private var editMode = false
    private lateinit var presetManager: PresetManager
    private val presets = mutableListOf<Preset>()

    private val handler = Handler(Looper.getMainLooper())
    private var lastTimerRunning = false
    private var lastActivePresetId: String? = null
    private val tickRunnable = object : Runnable {
        override fun run() {
            updateActiveTimerCard()
            updateStatusCard()
            if (lastTimerRunning != SilentTimerService.isTimerRunning || lastActivePresetId != SilentTimerService.activePresetId) {
                lastTimerRunning = SilentTimerService.isTimerRunning
                lastActivePresetId = SilentTimerService.activePresetId
                highlightAllPresets()
            }
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        presetManager = PresetManager(this)
        presets.addAll(presetManager.loadPresets())

        checkPermissions()
        setupButtons()
        setupActiveTimerCard()
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
        val seconds = intent.getIntExtra(ShortcutActivity.EXTRA_SECONDS, 0)
        val mode = intent.getIntExtra(ShortcutActivity.EXTRA_MODE, -1)
        val label = intent.getStringExtra(ShortcutActivity.EXTRA_LABEL)
        val presetId = intent.getStringExtra(ShortcutActivity.EXTRA_PRESET_ID)
        if (seconds > 0 && mode >= 0 && label != null) {
            startTimer(seconds, mode, label, presetId)
        }
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
            }
        }
    }

    private fun setupButtons() {
        findViewById<View>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<View>(R.id.btnOpenCustomTimer).setOnClickListener {
            CustomTimerDialog().show(supportFragmentManager, "customTimer")
        }
        findViewById<View>(R.id.btnEditPresets).setOnClickListener {
            editMode = !editMode
            renderPresets()
            val btn = it as MaterialButton
            btn.text = if (editMode) getString(R.string.done) else getString(R.string.edit)
        }
    }

    private fun renderPresets() {
        val container = findViewById<LinearLayout>(R.id.presetsContainer)
        container.removeAllViews()

        val isGrid = presetManager.getViewStyle() == "grid"
        val columns = if (isGrid) presetManager.getGridColumns() else 1

        var row: LinearLayout? = null
        var countInRow = 0

        for (preset in presets) {
            if (countInRow >= columns) {
                row = null
                countInRow = 0
            }
            if (row == null) {
                row = LinearLayout(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    orientation = LinearLayout.HORIZONTAL
                    container.addView(this)
                }
            }
            row.addView(createPresetCard(preset, isGrid))
            countInRow++
        }

        if (row == null || countInRow >= columns) {
            row = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.HORIZONTAL
                container.addView(this)
            }
        }
        row.addView(createAddCard(isGrid))
    }

    private fun createPresetCard(preset: Preset, isGrid: Boolean): View {
        val card = LayoutInflater.from(this).inflate(R.layout.item_preset, null) as MaterialCardView
        if (isGrid) {
            card.layoutParams = LinearLayout.LayoutParams(0, 88.dp(), 1f)
        } else {
            card.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 88.dp()
            )
        }
        val lp = card.layoutParams as ViewGroup.MarginLayoutParams
        lp.setMargins(0, 0, if (isGrid) 4.dp() else 0, 8.dp())

        card.tag = preset.id
        val labelView = card.findViewById<TextView>(R.id.presetLabel)
        labelView.text = preset.label
        labelView.setTag(R.id.presetLabel, preset.label)
        card.findViewById<TextView>(R.id.presetSub).text = "${preset.subText()} · ${preset.modeLabel()}"

        val deleteBtn = card.findViewById<View>(R.id.presetDeleteBtn)
        deleteBtn.visibility = if (editMode) View.VISIBLE else View.GONE

        val reorder = card.findViewById<View>(R.id.reorderButtons)
        reorder.visibility = if (editMode) View.VISIBLE else View.GONE

        updatePresetHighlight(card, preset.id)

        card.setOnClickListener {
            if (editMode) {
                showPresetDialog(preset)
            } else if (SilentTimerService.isTimerRunning && preset.id == SilentTimerService.activePresetId) {
                startService(Intent(this, SilentTimerService::class.java).apply {
                    action = SilentTimerService.ACTION_CANCEL
                })
            } else {
                startTimer(preset.totalSeconds, preset.mode, preset.label, preset.id)
            }
        }
        deleteBtn.setOnClickListener { showPresetDialog(preset) }

        val idx = presets.indexOfFirst { it.id == preset.id }
        card.findViewById<View>(R.id.btnMoveUp).setOnClickListener {
            if (idx > 0) movePreset(idx, idx - 1)
        }
        card.findViewById<View>(R.id.btnMoveDown).setOnClickListener {
            if (idx < presets.size - 1) movePreset(idx, idx + 1)
        }

        return card
    }

    private fun createAddCard(isGrid: Boolean): View {
        val addCard = LayoutInflater.from(this).inflate(R.layout.item_preset_add, null) as MaterialCardView
        if (isGrid) {
            addCard.layoutParams = LinearLayout.LayoutParams(0, 88.dp(), 1f)
        } else {
            addCard.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 88.dp()
            )
        }
        val lp = addCard.layoutParams as ViewGroup.MarginLayoutParams
        lp.setMargins(0, 0, 0, 8.dp())

        addCard.setOnClickListener {
            showPresetDialog(null)
        }
        return addCard
    }

    private fun movePreset(from: Int, to: Int) {
        val item = presets.removeAt(from)
        presets.add(to, item)
        presetManager.savePresets(presets)
        renderPresets()
        updateShortcuts()
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

    fun startTimer(seconds: Int, mode: Int, label: String, presetId: String? = null) {
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
            putExtra(SilentTimerService.EXTRA_PRESET_ID, presetId)
        }
        ContextCompat.startForegroundService(this, intent)
        val display = formatDuration(seconds)
        Toast.makeText(this, "$label for $display", Toast.LENGTH_SHORT).show()
    }

    private fun updateShortcuts() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return
        val manager = getSystemService(ShortcutManager::class.java) ?: return

        val shortcuts = presets.take(5).mapIndexed { i, preset ->
            val shortcutIntent = Intent(this, ShortcutActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra(ShortcutActivity.EXTRA_SECONDS, preset.totalSeconds)
                putExtra(ShortcutActivity.EXTRA_MODE, preset.mode)
                putExtra(ShortcutActivity.EXTRA_LABEL, preset.label)
                putExtra(ShortcutActivity.EXTRA_PRESET_ID, preset.id)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
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

    private fun updatePresetHighlight(card: MaterialCardView, presetId: String) {
        val isActive = SilentTimerService.isTimerRunning && presetId == SilentTimerService.activePresetId
        card.setCardBackgroundColor(
            if (isActive) android.content.res.ColorStateList.valueOf(
                androidx.core.content.ContextCompat.getColor(this, R.color.active_tint)
            ) else android.content.res.ColorStateList.valueOf(
                android.graphics.Color.TRANSPARENT
            )
        )
        val labelView = card.findViewById<TextView>(R.id.presetLabel)
        val baseLabel = labelView.getTag(R.id.presetLabel) as? String ?: ""
        if (isActive) {
            val spannable = SpannableString("$baseLabel ● Active")
            spannable.setSpan(
                ForegroundColorSpan(android.graphics.Color.GREEN),
                baseLabel.length + 1,
                spannable.length,
                0
            )
            labelView.text = spannable
        } else {
            labelView.text = baseLabel
        }
    }

    private fun highlightAllPresets() {
        val container = findViewById<LinearLayout>(R.id.presetsContainer)
        for (i in 0 until container.childCount) {
            val row = container.getChildAt(i) as? ViewGroup ?: continue
            for (j in 0 until row.childCount) {
                val card = row.getChildAt(j) as? MaterialCardView ?: continue
                val pid = card.tag as? String ?: continue
                updatePresetHighlight(card, pid)
            }
        }
    }

    private fun updateActiveTimerCard() {
        val section = findViewById<View>(R.id.timerSection)
        val timeText = findViewById<TextView>(R.id.activeTimerTime)
        val title = findViewById<TextView>(R.id.activeTimerTitle)

        if (SilentTimerService.isTimerRunning) {
            val rem = SilentTimerService.endTime - System.currentTimeMillis()
            val totalSecs = (rem / 1000).coerceAtLeast(0)
            val h = totalSecs / 3600
            val m = (totalSecs % 3600) / 60
            val s = totalSecs % 60
            timeText.text = if (h > 0)
                String.format("%d:%02d:%02d", h, m, s)
            else
                String.format("%d:%02d", m, s)

            val modeLabel = modeLabel(SilentTimerService.activeMode)
            title.text = "$modeLabel — ${getString(R.string.active_timer)}"

            if (section.visibility != View.VISIBLE) {
                TransitionManager.beginDelayedTransition(
                    findViewById<View>(R.id.statusCard) as ViewGroup
                )
                section.visibility = View.VISIBLE
            }
        } else {
            if (section.visibility == View.VISIBLE) {
                TransitionManager.beginDelayedTransition(
                    findViewById<View>(R.id.statusCard) as ViewGroup
                )
                section.visibility = View.GONE
            }
        }
    }

    private fun Int.dp(): Int =
        (this * resources.displayMetrics.density).toInt()

    companion object {
    }
}

