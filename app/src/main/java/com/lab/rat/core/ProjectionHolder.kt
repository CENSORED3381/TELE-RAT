package com.lab.rat.core

import android.content.Intent
import android.media.projection.MediaProjection

object ProjectionHolder {
    @Volatile var resultCode: Int = 0
    @Volatile var data: Intent? = null

    fun hasToken(): Boolean = resultCode != 0 && data != null

    fun createProjection(mpm: android.media.projection.MediaProjectionManager): MediaProjection? {
        val d = data ?: return null
        return mpm.getMediaProjection(resultCode, d)
    }

    fun store(code: Int, intent: Intent) {
        resultCode = code
        data = intent
    }

    fun clear() {
        resultCode = 0
        data = null
    }
}
