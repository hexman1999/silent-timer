package com.silentapp

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import org.json.JSONArray
import org.json.JSONObject

data class Preset(
    val label: String,
    val totalSeconds: Int,
    val mode: Int,
    val id: String = java.util.UUID.randomUUID().toString()
) : java.io.Serializable {
    fun modeLabel(): String = if (mode == AudioManager.RINGER_MODE_SILENT) "Silent" else "Vibrate"

    fun subText(): String {
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return buildString {
            if (h > 0) append("${h}h ")
            if (m > 0) append("${m}m ")
            if (s > 0 || (h == 0 && m == 0)) append("${s}s")
        }.trimEnd()
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
                        totalSeconds = obj.getInt("totalSeconds"),
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
                    put("totalSeconds", p.totalSeconds)
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
            Preset("Silent", 1800, AudioManager.RINGER_MODE_SILENT),
            Preset("Vibrate", 900, AudioManager.RINGER_MODE_VIBRATE),
            Preset("Silent", 3600, AudioManager.RINGER_MODE_SILENT),
            Preset("Vibrate", 300, AudioManager.RINGER_MODE_VIBRATE),
        )
    }
}
