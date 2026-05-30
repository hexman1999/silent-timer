package com.silentapp

import android.content.Context
import android.content.SharedPreferences
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class SilentTileService : TileService() {

    private data class CycleItem(val label: String, val mode: Int, val seconds: Int)

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val pos = prefs.getInt(KEY_POS, 0)
        val items = buildCycleList()
        if (items.isEmpty()) return

        val nextPos = (pos + 1) % items.size
        val item = items[nextPos]

        RingerModeManager.applyMode(this, item.mode)

        if (item.seconds > 0) {
            val intent = android.content.Intent(this, SilentTimerService::class.java).apply {
                action = SilentTimerService.ACTION_START
                putExtra(SilentTimerService.EXTRA_SECONDS, item.seconds)
                putExtra(SilentTimerService.EXTRA_MODE, item.mode)
            }
            startForegroundServiceSafe(this, intent)
        }

        prefs.edit().putInt(KEY_POS, nextPos).apply()
        updateTile()
    }

    private fun buildCycleList(): List<CycleItem> {
        val presetManager = PresetManager(this)
        val presets = presetManager.loadPresets().map {
            CycleItem(it.label, it.mode, it.totalSeconds)
        }
        val ringerModes = listOf(
            CycleItem("Sound", MODE_NORMAL, 0),
            CycleItem("Silent", MODE_SILENT, 0),
            CycleItem("Vibrate", MODE_VIBRATE, 0),
        )
        return presets + ringerModes
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val pos = prefs.getInt(KEY_POS, 0)
        val items = buildCycleList()

        if (items.isEmpty()) {
            tile.label = getString(R.string.tile_normal)
            tile.state = Tile.STATE_INACTIVE
            tile.updateTile()
            return
        }

        val current = items[pos % items.size]
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
