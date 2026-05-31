package com.silentapp

import android.content.Context
import android.graphics.drawable.Icon
import androidx.core.graphics.drawable.IconCompat

object IconCreator {

    fun getSilentIcon(context: Context): Icon {
        return Icon.createWithResource(context, R.drawable.ic_silent)
    }

    fun getVibrateIcon(context: Context): Icon {
        return Icon.createWithResource(context, R.drawable.ic_vibrate)
    }

    fun getNormalIcon(context: Context): Icon {
        return Icon.createWithResource(context, R.drawable.ic_normal)
    }

    fun getDndIcon(context: Context): Icon {
        return Icon.createWithResource(context, R.drawable.ic_dnd)
    }

    fun getIconForMode(context: Context, mode: Int): Icon = when (mode) {
        MODE_SILENT -> getSilentIcon(context)
        MODE_VIBRATE -> getVibrateIcon(context)
        MODE_DND -> getDndIcon(context)
        else -> getNormalIcon(context)
    }
}
