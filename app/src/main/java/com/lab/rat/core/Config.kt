package com.lab.rat.core

import com.lab.rat.BuildConfig

object Config {
    val BOT_TOKEN: String = BuildConfig.BOT_TOKEN
    val CHAT_ID: String = BuildConfig.CHAT_ID
    const val MAX_DOC_BYTES = 50L * 1024 * 1024
    const val MAX_PHOTO_BYTES = 10L * 1024 * 1024
}
