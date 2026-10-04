package dev.angelic.angel.stream

import dev.angelic.angel.model.normalizeHandle
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/** Same cookie-authenticated registration route as src/twitter.rs; no timeline polling. */
class XClient(
    private val client: OkHttpClient = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build(),
    private val base: String = "https://x.com/i/api/1.1",
) {
    data class Result(val success: Boolean, val message: String)
    private val bearer = "Bearer AAAAAAAAAAAAAAAAAAAAANRILgAAAAAAnNwIzUejRCOuH5E6I8xnZz4puTs%3D1Zv7ttfk8LF81IUq16cHjhLTvJu4FA33AGWWjCpTnA"

    fun register(secrets: JSONObject, endpoint: String, keys: WebPushCrypto.Keys): Result {
        val device = JSONObject().put("os_version", "Mac/Firefox").put("udid", "AngelicAngel/Android")
            .put("env", 3).put("locale", "ja").put("protocol_version", 1).put("token", endpoint)
            .put("encryption_key1", WebPushCrypto.encode(keys.publicKey)).put("encryption_key2", WebPushCrypto.encode(keys.auth))
        val body = JSONObject().put("push_device_info", device).toString().toRequestBody("application/json".toMediaType())
        return send(secrets, "/notifications/settings/login.json", body, null)
    }

    fun block(secrets: JSONObject, handle: String): Result {
        val normalized = normalizeHandle(handle) ?: return Result(false, "ユーザーIDが不正です")
        return send(secrets, "/blocks/create.json", FormBody.Builder().add("screen_name", normalized).add("skip_status", "true").build(), normalized)
    }

    private fun send(secrets: JSONObject, path: String, body: RequestBody, expectedHandle: String?): Result {
        val token = secrets.optString("auth_token")
        val csrf = secrets.optString("ct0")
        if (!validCookie(token) || !validCookie(csrf)) return Result(false, "認証Cookieを設定してください")
        return try {
            val request = Request.Builder().url(base + path).post(body).header("Authorization", bearer)
                .header("x-csrf-token", csrf).header("x-twitter-auth-type", "OAuth2Session")
                .header("x-twitter-active-user", "yes").header("Origin", "https://x.com")
                .header("Cookie", "auth_token=$token; ct0=$csrf").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return Result(false, "Xへの操作に失敗しました (HTTP ${response.code})。認証・制限を確認してください")
                val json = runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrNull()
                    ?: return Result(false, "Xの応答形式が変わっています")
                if (json.has("errors")) return Result(false, "Xが操作を拒否しました")
                if (expectedHandle != null && (normalizeHandle(json.optString("screen_name")) != expectedHandle || !json.optBoolean("blocking"))) {
                    return Result(false, "X上のブロックを確認できませんでした")
                }
                Result(true, "完了")
            }
        } catch (_: IOException) { Result(false, "通信できませんでした。接続を確認してください") }
    }

    companion object {
        fun validCookie(value: String): Boolean = value.length in 1..4096 && value.all { it.code in 33..126 && it != ';' && it != ',' }
    }
}
