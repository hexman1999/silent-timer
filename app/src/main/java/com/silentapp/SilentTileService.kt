package com.silentapp

import android.media.AudioManager
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class SilentTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        when (audioManager.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> {
                audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
            }
            AudioManager.RINGER_MODE_SILENT -> {
                audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
            }
            AudioManager.RINGER_MODE_VIBRATE -> {
                audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
            }
        }
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        when (audioManager.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> {
                tile.label = getString(R.string.tile_normal)
                tile.icon = IconCreator.getNormalIcon(this)
                tile.state = Tile.STATE_INACTIVE
            }
            AudioManager.RINGER_MODE_SILENT -> {
                tile.label = getString(R.string.tile_silent)
                tile.icon = IconCreator.getSilentIcon(this)
                tile.state = Tile.STATE_ACTIVE
            }
            AudioManager.RINGER_MODE_VIBRATE -> {
                tile.label = getString(R.string.tile_vibrate)
                tile.icon = IconCreator.getVibrateIcon(this)
                tile.state = Tile.STATE_ACTIVE
            }
        }
        tile.updateTile()
    }
}
