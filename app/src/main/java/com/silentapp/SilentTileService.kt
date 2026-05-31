package com.silentapp

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.text.format.DateFormat
import android.widget.Toast
import java.util.Calendar

class SilentTileService : TileService() {

    private data class CycleItem(val label: String, val mode: Int, val seconds: Int, val presetId: String? = null)

    private val tileHandler = Handler(Looper.getMainLooper())
    private val tileTickRunnable = object : Runnable {
        override fun run() {
            if (SilentTimerService.isTimerRunning) {
                updateTile()
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
        updateTile()
    }

    override fun onStopListening() {
        super.onStopListening()
        tileHandler.removeCallbacks(tileTickRunnable)
    }

    override fun onClick() {
        try {
            if (!isSecure()) {
                handleClick()
            } else {
                unlockAndRun { handleClick() }
            }
        } catch (_: Exception) {
            updateTile()
        }
    }

    private fun handleClick() {
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
            updateTile()
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
        updateTile()
        collapseQsPanel()
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

    private fun updateTile() {
        val tile = qsTile ?: return
        val items = buildCycleList()

        if (items.isEmpty()) {
            tile.label = getString(R.string.tile_normal)
            tile.state = Tile.STATE_INACTIVE
            tile.updateTile()
            return
        }

        if (SilentTimerService.isTimerRunning) {
            val rem = SilentTimerService.endTime - System.currentTimeMillis()
            tile.label = formatRemaining(rem)
            tile.state = Tile.STATE_ACTIVE
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

    override fun onStartCommand(intent: android.content.Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    companion object {
        private const val PREFS_NAME = "silent_timer_tile"
        private const val KEY_POS = "cycle_position"
    }
}

private fun startForegroundServiceSafe(context: Context, intent: android.content.Intent) {
    context.startForegroundService(intent)
}
