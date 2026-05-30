package com.silentapp

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import org.json.JSONArray
import org.json.JSONObject

data class Preset(
    val label: String,
    val minutes: Int,
    val mode: Int,
    val id: String = java.util.UUID.randomUUID().toString()
) : java.io.Serializable {
    fun modeLabel(): String = if (mode == AudioManager.RINGER_MODE_SILENT) "Silent" else "Vibrate"

    fun subText(): String = when {
        minutes >= 60 -> "${minutes / 60}h ${minutes % 60}m"
        else -> "$minutes min"
    }
}

class PresetManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadPresets(): MutableList<Preset> {
        val json = prefs.getString(KEY_PRESETS, null) ?: return defaultPresets().toMutableList()
        return try {
            val arr = JSONArray(json)
            val list = mutableListOf<Preset>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    Preset(
                        id = obj.getString("id"),
                        label = obj.getString("label"),
                        minutes = obj.getInt("minutes"),
                        mode = obj.getInt("mode")
                    )
                )
            }
            if (list.isEmpty()) defaultPresets().toMutableList() else list
        } catch (_: Exception) {
            defaultPresets().toMutableList()
        }
    }

    fun savePresets(presets: List<Preset>) {
        val arr = JSONArray()
        presets.forEach { p ->
            arr.put(
                JSONObject().apply {
                    put("id", p.id)
                    put("label", p.label)
                    put("minutes", p.minutes)
                    put("mode", p.mode)
                }
            )
        }
        prefs.edit().putString(KEY_PRESETS, arr.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "silent_timer_prefs"
        private const val KEY_PRESETS = "presets"

        fun defaultPresets() = listOf(
            Preset("Silent", 30, AudioManager.RINGER_MODE_SILENT),
            Preset("Vibrate", 15, AudioManager.RINGER_MODE_VIBRATE),
            Preset("Silent", 60, AudioManager.RINGER_MODE_SILENT),
            Preset("Vibrate", 5, AudioManager.RINGER_MODE_VIBRATE),
        )
    }
}
