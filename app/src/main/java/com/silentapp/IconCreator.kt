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
}
