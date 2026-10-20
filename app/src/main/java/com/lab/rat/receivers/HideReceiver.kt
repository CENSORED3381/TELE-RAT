package com.lab.rat.receivers

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.lab.rat.MainActivity

class HideReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == "com.lab.rat.SHOW") {
            ctx.packageManager.setComponentEnabledSetting(
                ComponentName(ctx, MainActivity::class.java),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP)
        }
    }
}
