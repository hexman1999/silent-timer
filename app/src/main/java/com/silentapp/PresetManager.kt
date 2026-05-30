package com.silentapp

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import org.json.JSONArray
import org.json.JSONObject

const val MODE_SILENT = AudioManager.RINGER_MODE_SILENT
const val MODE_VIBRATE = AudioManager.RINGER_MODE_VIBRATE
const val MODE_NORMAL = AudioManager.RINGER_MODE_NORMAL
const val MODE_DND = 10

fun formatDuration(seconds: Int): String = when {
    seconds >= 3600 -> "${seconds / 3600}h ${(seconds % 3600) / 60}m ${seconds % 60}s"
    seconds >= 60 -> "${seconds / 60}m ${seconds % 60}s"
    else -> "${seconds}s"
}

fun modeLabel(mode: Int): String = when (mode) {
    MODE_SILENT -> "Silent"
    MODE_VIBRATE -> "Vibrate"
    MODE_DND -> "DND"
    else -> "Sound"
}

data class Preset(
    val label: String,
    val totalSeconds: Int,
    val mode: Int,
    val id: String = java.util.UUID.randomUUID().toString()
) : java.io.Serializable {
    fun modeLabel(): String = when (mode) {
        MODE_SILENT -> "Silent"
        MODE_VIBRATE -> "Vibrate"
        MODE_DND -> "DND"
        else -> "Sound"
    }

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

    fun getViewStyle(): String = prefs.getString(KEY_VIEW_STYLE, "grid") ?: "grid"

    fun setViewStyle(style: String) {
        prefs.edit().putString(KEY_VIEW_STYLE, style).apply()
    }

    fun getGridColumns(): Int = prefs.getInt(KEY_GRID_COLUMNS, 3).coerceIn(2, 4)

    fun setGridColumns(columns: Int) {
        prefs.edit().putInt(KEY_GRID_COLUMNS, columns.coerceIn(2, 4)).apply()
    }

    companion object {
        private const val PREFS_NAME = "silent_timer_prefs"
        private const val KEY_PRESETS = "presets"
        private const val KEY_VIEW_STYLE = "view_style"
        private const val KEY_GRID_COLUMNS = "grid_columns"

        fun defaultPresets() = listOf(
            Preset("Silent", 1800, MODE_SILENT),
            Preset("Vibrate", 900, MODE_VIBRATE),
            Preset("DND", 3600, MODE_DND),
            Preset("Vibrate", 300, MODE_VIBRATE),
        )
    }
}
