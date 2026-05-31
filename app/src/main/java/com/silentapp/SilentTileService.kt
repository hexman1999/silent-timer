package com.silentapp

import android.app.AlertDialog
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.app.PendingIntent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.text.format.DateFormat
import android.widget.RemoteViews
import android.widget.Toast
import java.util.Calendar

class SilentTileService : TileService() {

    private data class CycleItem(val label: String, val mode: Int, val seconds: Int, val presetId: String? = null)

    private sealed class DialogAction {
        data object STOP : DialogAction()
        data class PRESET(val preset: Preset) : DialogAction()
        data class MODE(val mode: Int) : DialogAction()
    }

    private val tileHandler = Handler(Looper.getMainLooper())
    private val tileTickRunnable = object : Runnable {
        override fun run() {
            if (SilentTimerService.isTimerRunning) {
                refreshTile()
                tileHandler.postDelayed(this, 1000)
            }
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        tileHandler.removeCallbacks(tileTickRunnable)
        if (SilentTimerService.isTimerRunning) {
            tileHandler.post(tileTickRunnable)
        }
        refreshTile()
    }

    override fun onStopListening() {
        super.onStopListening()
        tileHandler.removeCallbacks(tileTickRunnable)
    }

    override fun onClick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
        try {
            if (!isSecure()) {
                showPresetDialog()
            } else {
                unlockAndRun { showPresetDialog() }
            }
        } catch (_: Exception) {
            refreshTile()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ICON_TAP -> handleIconTap()
            ACTION_LABEL_TAP -> {
                if (isAdded()) showPresetDialog()
                else startActivityAndCollapse(
                    Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                )
            }
        }
        return START_NOT_STICKY
    }

    private fun handleIconTap() {
        val items = buildCycleList()
        if (items.isEmpty()) return

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val pos = if (SilentTimerService.isTimerRunning) 0
                  else prefs.getInt(KEY_POS, 0) % items.size
        val current = items[pos]

        if (current.label == "Stop") {
            Intent(this, SilentTimerService::class.java).apply {
                action = SilentTimerService.ACTION_CANCEL
                startForegroundServiceSafe(this@SilentTileService, this)
            }
            prefs.edit().putInt(KEY_POS, 0).apply()
            tileHandler.removeCallbacks(tileTickRunnable)
            refreshTile()
            collapseQsPanel()
            return
        }

        val nextPos = (pos + 1) % items.size
        val item = items[nextPos]

        RingerModeManager.applyMode(this, item.mode)

        if (item.seconds > 0) {
            val intent = Intent(this, SilentTimerService::class.java).apply {
                action = SilentTimerService.ACTION_START
                putExtra(SilentTimerService.EXTRA_SECONDS, item.seconds)
                putExtra(SilentTimerService.EXTRA_MODE, item.mode)
                putExtra(SilentTimerService.EXTRA_PRESET_ID, item.presetId)
            }
            startForegroundServiceSafe(this, intent)

            val endTime = System.currentTimeMillis() + item.seconds * 1000L
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
            val timeStr = formatDuration(item.seconds)
            val toastText = "${item.label} for $timeStr (until $endTimeStr)"

            Handler(Looper.getMainLooper()).post {
                Toast.makeText(this, toastText, Toast.LENGTH_LONG).show()
            }
        }

        prefs.edit().putInt(KEY_POS, nextPos).apply()
        tileHandler.removeCallbacks(tileTickRunnable)
        if (SilentTimerService.isTimerRunning) {
            tileHandler.post(tileTickRunnable)
        }
        refreshTile()
        collapseQsPanel()
    }

    private fun showPresetDialog() {
        if (!isAdded()) return

        val presetManager = PresetManager(this)
        val presets = presetManager.loadPresets()

        val labels = mutableListOf<String>()
        val actions = mutableListOf<DialogAction>()

        if (SilentTimerService.isTimerRunning) {
            labels.add("\u23F9 Stop Timer")
            actions.add(DialogAction.STOP)
        }

        for (p in presets) {
            labels.add("${p.label} (${p.subText()})")
            actions.add(DialogAction.PRESET(p))
        }

        labels.add("Sound")
        actions.add(DialogAction.MODE(MODE_NORMAL))
        labels.add("Silent")
        actions.add(DialogAction.MODE(MODE_SILENT))
        labels.add("Vibrate")
        actions.add(DialogAction.MODE(MODE_VIBRATE))

        val dialog = AlertDialog.Builder(this)
            .setTitle("Select Mode")
            .setItems(labels.toTypedArray()) { _, which ->
                val action = actions[which]
                when (action) {
                    is DialogAction.STOP -> {
                        Intent(this, SilentTimerService::class.java).apply {
                            action = SilentTimerService.ACTION_CANCEL
                            startForegroundServiceSafe(this@SilentTileService, this)
                        }
                        tileHandler.removeCallbacks(tileTickRunnable)
                        refreshTile()
                        collapseQsPanel()
                    }
                    is DialogAction.PRESET -> {
                        val p = action.preset
                        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                        if (p.mode == MODE_DND && !nm.isNotificationPolicyAccessGranted) {
                            Toast.makeText(this, "DND access required", Toast.LENGTH_LONG).show()
                            return@setItems
                        }
                        RingerModeManager.applyMode(this, p.mode)
                        val intent = Intent(this, SilentTimerService::class.java).apply {
                            action = SilentTimerService.ACTION_START
                            putExtra(SilentTimerService.EXTRA_SECONDS, p.totalSeconds)
                            putExtra(SilentTimerService.EXTRA_MODE, p.mode)
                            putExtra(SilentTimerService.EXTRA_PRESET_ID, p.id)
                        }
                        startForegroundServiceSafe(this, intent)
                        refreshTile()
                        collapseQsPanel()
                    }
                    is DialogAction.MODE -> {
                        RingerModeManager.applyMode(this, action.mode)
                        refreshTile()
                        collapseQsPanel()
                    }
                }
            }
            .setOnDismissListener { refreshTile() }
            .create()
        showDialog(dialog)
    }

    private fun formatRemaining(ms: Long): String {
        val totalSecs = (ms / 1000).coerceAtLeast(0)
        val h = totalSecs / 3600
        val m = (totalSecs % 3600) / 60
        val s = totalSecs % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
        else String.format("%d:%02d", m, s)
    }

    private fun getModeDrawable(mode: Int): Int = when (mode) {
        MODE_SILENT -> R.drawable.ic_silent
        MODE_VIBRATE -> R.drawable.ic_vibrate
        MODE_DND -> R.drawable.ic_dnd
        else -> R.drawable.ic_normal
    }

    private fun collapseQsPanel() {
        @Suppress("DEPRECATION")
        startActivityAndCollapse(
            Intent(this, ShortcutActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )
    }

    private fun buildCycleList(): List<CycleItem> {
        val presetManager = PresetManager(this)
        val presets = presetManager.loadPresets().map {
            CycleItem(it.label, it.mode, it.totalSeconds, it.id)
        }
        val ringerModes = listOf(
            CycleItem("Sound", MODE_NORMAL, 0),
            CycleItem("Silent", MODE_SILENT, 0),
            CycleItem("Vibrate", MODE_VIBRATE, 0),
        )

        if (SilentTimerService.isTimerRunning) {
            val activeId = SilentTimerService.activePresetId
            val filtered = presets.filter { it.presetId != activeId }
            return listOf(CycleItem("Stop", MODE_NORMAL, 0)) + filtered + ringerModes
        }

        return presets + ringerModes
    }

    private fun updateQsTile() {
        val tile = qsTile ?: return
        if (SilentTimerService.isTimerRunning) {
            tile.state = Tile.STATE_ACTIVE
        } else {
            val items = buildCycleList()
            if (items.isEmpty()) {
                tile.state = Tile.STATE_INACTIVE
            } else {
                val pos = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_POS, 0) % items.size
                val current = items[pos]
                tile.state = when (current.mode) {
                    MODE_SILENT, MODE_VIBRATE, MODE_DND -> Tile.STATE_ACTIVE
                    else -> Tile.STATE_INACTIVE
                }
            }
        }
        tile.updateTile()
    }

    private fun refreshTile() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val rv = RemoteViews(packageName, R.layout.qs_tile_visual)

                val iconRes: Int
                val label: String

                if (SilentTimerService.isTimerRunning) {
                    val rem = SilentTimerService.endTime - System.currentTimeMillis()
                    label = formatRemaining(rem)
                    iconRes = getModeDrawable(SilentTimerService.activeMode)
                } else {
                    val items = buildCycleList()
                    if (items.isEmpty()) {
                        label = getString(R.string.tile_normal)
                        iconRes = R.drawable.ic_normal
                    } else {
                        val pos = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_POS, 0) % items.size
                        val current = items[pos]
                        label = current.label
                        iconRes = getModeDrawable(current.mode)
                    }
                }

                rv.setTextViewText(R.id.tile_label, label)
                rv.setImageViewResource(R.id.tile_icon, iconRes)

                val iconIntent = Intent(this, SilentTileService::class.java).apply {
                    action = ACTION_ICON_TAP
                }
                val labelIntent = Intent(this, SilentTileService::class.java).apply {
                    action = ACTION_LABEL_TAP
                }

                val iconPI = PendingIntent.getService(
                    this, ICON_TAP_REQ, iconIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                val labelPI = PendingIntent.getService(
                    this, LABEL_TAP_REQ, labelIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )

                rv.setOnClickPendingIntent(R.id.tile_icon_area, iconPI)
                rv.setOnClickPendingIntent(R.id.tile_label, labelPI)

                setTileVisual(android.service.quicksettings.TileVisual(rv, this))
            } catch (_: Exception) {
            }
        }
        updateQsTile()
    }

    companion object {
        private const val PREFS_NAME = "silent_timer_tile"
        private const val KEY_POS = "cycle_position"
        private const val ACTION_ICON_TAP = "icon_tap"
        private const val ACTION_LABEL_TAP = "label_tap"
        private const val ICON_TAP_REQ = 3001
        private const val LABEL_TAP_REQ = 3002
    }
}

private fun startForegroundServiceSafe(context: Context, intent: Intent) {
    context.startForegroundService(intent)
}
