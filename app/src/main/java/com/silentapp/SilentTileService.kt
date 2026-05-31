package com.silentapp

import android.app.AlertDialog
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.text.format.DateFormat
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

    private fun showPresetDialog() {
        if (qsTile == null) return

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
                val dialogAction = actions[which]
                when (dialogAction) {
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
                        val p = dialogAction.preset
                        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                        if (p.mode == MODE_DND && !nm.isNotificationPolicyAccessGranted) {
                            Toast.makeText(this, "DND access required", Toast.LENGTH_LONG).show()
                            return@setItems
                        }
                        RingerModeManager.applyMode(this, p.mode)
                        Intent(this, SilentTimerService::class.java).apply {
                            action = SilentTimerService.ACTION_START
                            putExtra(SilentTimerService.EXTRA_SECONDS, p.totalSeconds)
                            putExtra(SilentTimerService.EXTRA_MODE, p.mode)
                            putExtra(SilentTimerService.EXTRA_PRESET_ID, p.id)
                            startForegroundServiceSafe(this@SilentTileService, this)
                        }
                        refreshTile()
                        collapseQsPanel()
                    }
                    is DialogAction.MODE -> {
                        RingerModeManager.applyMode(this, dialogAction.mode)
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

    private fun collapseQsPanel() {
        @Suppress("DEPRECATION")
        startActivityAndCollapse(
            Intent(this, ShortcutActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )
    }

    private fun refreshTile() {
        val tile = qsTile ?: return

        if (SilentTimerService.isTimerRunning) {
            val rem = SilentTimerService.endTime - System.currentTimeMillis()
            tile.label = formatRemaining(rem)
            tile.state = Tile.STATE_ACTIVE
            tile.updateTile()
            return
        }

        val presetManager = PresetManager(this)
        val presets = presetManager.loadPresets().map {
            CycleItem(it.label, it.mode, it.totalSeconds, it.id)
        }
        val ringerModes = listOf(
            CycleItem("Sound", MODE_NORMAL, 0),
            CycleItem("Silent", MODE_SILENT, 0),
            CycleItem("Vibrate", MODE_VIBRATE, 0),
        )
        val items = presets + ringerModes

        if (items.isEmpty()) {
            tile.label = getString(R.string.tile_normal)
            tile.state = Tile.STATE_INACTIVE
            tile.updateTile()
            return
        }

        val pos = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_POS, 0) % items.size
        val current = items[pos]
        tile.label = current.label
        tile.state = when (current.mode) {
            MODE_SILENT, MODE_VIBRATE, MODE_DND -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.updateTile()
    }

    companion object {
        private const val PREFS_NAME = "silent_timer_tile"
        private const val KEY_POS = "cycle_position"
    }
}

private fun startForegroundServiceSafe(context: Context, intent: Intent) {
    context.startForegroundService(intent)
}
