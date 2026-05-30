package com.silentapp

import android.content.Context
import android.media.AudioManager
import androidx.work.Worker
import androidx.work.WorkerParameters

class SilentModeWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val targetMode = inputData.getInt(KEY_TARGET_MODE, AudioManager.RINGER_MODE_NORMAL)
        val audioManager = applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.ringerMode = targetMode
        return Result.success()
    }

    companion object {
        const val KEY_TARGET_MODE = "target_mode"
    }
}
