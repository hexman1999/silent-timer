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
import android.animation.ValueAnimator
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

        val dragHandle = card.findViewById<View>(R.id.dragHandle)
        dragHandle.visibility = if (editMode) View.VISIBLE else View.GONE
        dragHandle.setOnLongClickListener {
            val data = android.content.ClipData.newPlainText("preset_id", preset.id)
            card.startDragAndDrop(data, View.DragShadowBuilder(card), null, 0)
            true
        }

        card.setOnDragListener { _, event ->
            when (event.action) {
                android.view.DragEvent.ACTION_DRAG_STARTED -> {
                    (event.clipData?.getItemAt(0)?.text?.toString()?.let { it != preset.id }) ?: false
                }
                android.view.DragEvent.ACTION_DRAG_ENTERED -> {
                    card.strokeWidth = 3.dp()
                    true
                }
                android.view.DragEvent.ACTION_DRAG_EXITED -> {
                    card.strokeWidth = 1.dp()
                    true
                }
                android.view.DragEvent.ACTION_DROP -> {
                    val draggedId = event.clipData?.getItemAt(0)?.text?.toString()
                    if (draggedId != null && draggedId != preset.id) {
                        val fromIdx = presets.indexOfFirst { it.id == draggedId }
                        val toIdx = presets.indexOfFirst { it.id == preset.id }
                        if (fromIdx >= 0 && toIdx >= 0) {
                            val item = presets.removeAt(fromIdx)
                            presets.add(toIdx, item)
                            presetManager.savePresets(presets)
                            updateShortcuts()
                            card.post { renderPresets() }
                        }
                    }
                    card.strokeWidth = 1.dp()
                    true
                }
                android.view.DragEvent.ACTION_DRAG_ENDED -> {
                    card.strokeWidth = 1.dp()
                    true
                }
                else -> true
            }
        }

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
        val iconBg = findViewById<View>(R.id.statusIconBg)
        when (mode) {
            MODE_NORMAL -> {
                icon.text = "\uD83D\uDD14"
                label.text = getString(R.string.status_normal)
                iconBg.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    androidx.core.content.ContextCompat.getColor(this, R.color.primary_container)
                ))
            }
            MODE_SILENT -> {
                icon.text = "\uD83D\uDD15"
                label.text = getString(R.string.status_silent)
                iconBg.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    androidx.core.content.ContextCompat.getColor(this, R.color.primary_container)
                ))
            }
            MODE_VIBRATE -> {
                icon.text = "\uD83D\uDCF3"
                label.text = getString(R.string.status_vibrate)
                iconBg.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    androidx.core.content.ContextCompat.getColor(this, R.color.secondary_container)
                ))
            }
            MODE_DND -> {
                icon.text = "\uD83D\uDCF4"
                label.text = "DND"
                iconBg.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    androidx.core.content.ContextCompat.getColor(this, R.color.error)
                ))
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
        val progress = findViewById<com.google.android.material.progressindicator.LinearProgressIndicator>(R.id.timerProgress)

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
            title.text = modeLabel

            val totalMs = SilentTimerService.totalDuration.coerceAtLeast(1)
            val remainingMs = rem.coerceAtLeast(0)
            val pct = ((totalMs - remainingMs) * 1000 / totalMs).toInt()
            progress.setProgressCompat(pct, true)

            if (section.visibility != View.VISIBLE) {
                smoothExpand(section)
            }
        } else {
            if (section.visibility == View.VISIBLE) {
                smoothCollapse(section)
            }
        }
    }

    private fun smoothExpand(view: View) {
        view.measure(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        val targetHeight = view.measuredHeight
        view.layoutParams.height = 0
        view.visibility = View.VISIBLE
        view.alpha = 0f
        ValueAnimator.ofInt(0, targetHeight).apply {
            addUpdateListener { anim ->
                view.layoutParams.height = anim.animatedValue as Int
                view.requestLayout()
                view.alpha = anim.animatedFraction
            }
            interpolator = android.view.animation.DecelerateInterpolator()
            duration = 300
            start()
        }
    }

    private fun smoothCollapse(view: View) {
        val startHeight = view.height
        ValueAnimator.ofInt(startHeight, 0).apply {
            addUpdateListener { anim ->
                view.layoutParams.height = anim.animatedValue as Int
                view.requestLayout()
                view.alpha = 1f - anim.animatedFraction
            }
            addListener(object : android.animation.Animator.AnimatorListener {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    view.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    view.visibility = View.GONE
                    view.alpha = 1f
                }
                override fun onAnimationStart(animation: android.animation.Animator) {}
                override fun onAnimationCancel(animation: android.animation.Animator) {}
                override fun onAnimationRepeat(animation: android.animation.Animator) {}
            })
            interpolator = android.view.animation.DecelerateInterpolator()
            duration = 300
            start()
        }
    }

    private fun Int.dp(): Int =
        (this * resources.displayMetrics.density).toInt()

    companion object {
    }
}

