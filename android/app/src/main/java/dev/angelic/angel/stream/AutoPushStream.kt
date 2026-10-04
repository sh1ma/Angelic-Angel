package dev.angelic.angel.stream

import dev.angelic.angel.data.AngelApplication
import dev.angelic.angel.data.Preferences
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject

/** A single serial actor owns the connection and prevents overlapping reconnects. */
class AutoPushStream(
    private val app: AngelApplication,
    private val readSecrets: () -> JSONObject = { app.secrets.read() },
    private val updateSecrets: ((JSONObject) -> Unit) -> Unit = { app.secrets.update(it) },
    private val endpoint: String = "wss://push.services.mozilla.com/",
    private val x: XClient = XClient(),
) {
    private val actor = Executors.newSingleThreadScheduledExecutor()
    private val client = OkHttpClient.Builder().pingInterval(150, TimeUnit.SECONDS).build()
    private var socket: WebSocket? = null
    private var retry: ScheduledFuture<*>? = null
    private var handshake: ScheduledFuture<*>? = null
    private var stopped = false
    private var failures = 0
    private var session = JSONObject()
    private var generation = ""

    fun start() { actor.execute { retry?.cancel(false); socket?.cancel(); socket = null; connect() } }
    fun stop() {
        actor.execute {
            stopped = true; retry?.cancel(false); handshake?.cancel(false)
            socket?.cancel(); socket = null
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
            app.status("停止中")
            actor.shutdown()
        }
    }

    private fun dispatch(block: () -> Unit) {
        if (!actor.isShutdown) runCatching { actor.execute { if (!stopped) block() } }
    }

    private fun connect() {
        if (stopped) return
        session = runCatching {
            val secrets = readSecrets()
            generation = secrets.optString("generation")
            secrets.optJSONObject("session") ?: JSONObject()
        }.getOrElse {
            app.status("保存した認証情報を読めません。設定し直してください"); return
        }
        app.status("Mozilla Pushに接続中")
        socket = client.newWebSocket(Request.Builder().url(endpoint).header("Origin", "https://x.com").build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) = dispatch {
                if (socket !== webSocket) return@dispatch
                val hello = JSONObject().put("messageType", "hello").put("use_webpush", true).put("broadcasts", JSONObject())
                if (session.has("uaid")) hello.put("uaid", session.getString("uaid")).put("channelIDs", JSONArray().put(session.getString("channel")))
                webSocket.send(hello.toString())
            }
            override fun onMessage(webSocket: WebSocket, text: String) = dispatch {
                if (socket !== webSocket) return@dispatch
                if (text.length > 131072) { fail("通知サイズが大きすぎます", false); return@dispatch }
                runCatching { synchronized(app.accountLock) { message(JSONObject(text)) } }.onFailure { fail("Pushの応答を処理できません。再接続します", true) }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = dispatch {
                if (socket === webSocket) fail("接続が切れました。再接続待ち", true)
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) = dispatch {
                if (socket === webSocket) fail("サーバーが接続を終了しました。再接続待ち", true, if (code == 4774) 1800 else null)
            }
        })
        handshake?.cancel(false)
        handshake = actor.schedule({ fail("Pushの接続がタイムアウトしました", true) }, 45, TimeUnit.SECONDS)
    }

    private fun message(json: JSONObject) {
        if (readSecrets().optString("generation") != generation) { fail("設定が変更されました。受信を開始し直してください", false); return }
        when (json.optString("messageType")) {
            "hello" -> {
                require(json.optInt("status") == 200)
                val uaid = json.getString("uaid")
                if (session.optString("uaid") == uaid && session.optBoolean("registered")) {
                    ready()
                } else {
                    val keys = WebPushCrypto.generate()
                    session = JSONObject().put("uaid", uaid).put("channel", UUID.randomUUID().toString())
                        .put("public", WebPushCrypto.encode(keys.publicKey)).put("private", WebPushCrypto.encode(keys.privateKey)).put("auth", WebPushCrypto.encode(keys.auth))
                    val vapid = byteArrayOf(4,94,104,18,-115,49,13,74,96,-54,82,-125,78,91,29,-14,-106,102,-59,0,53,-107,-26,8,54,38,62,-83,43,28,89,-126,-65,-34,-43,-128,-109,62,21,49,-69,95,-44,-62,-60,-3,-116,-99,-22,34,8,-22,-113,-98,-35,15,83,8,-34,111,100,-52,-43,48,75)
                    socket?.send(JSONObject().put("messageType", "register").put("channelID", session.getString("channel")).put("key", WebPushCrypto.encode(vapid)).toString())
                }
            }
            "register" -> {
                require(json.optInt("status") == 200 && json.getString("channelID") == session.getString("channel"))
                val endpoint = json.getString("pushEndpoint")
                require(endpoint.startsWith("https://"))
                app.status("Xに通知の受信先を登録中")
                val result = x.register(readSecrets(), endpoint, keys())
                if (!result.success) { fail(result.message, false); return }
                session.put("registered", true)
                updateSecrets { if (it.optString("generation") == generation) it.put("session", session) }
                ready()
            }
            "notification" -> notification(json)
        }
    }

    private fun keys() = WebPushCrypto.Keys(WebPushCrypto.decode(session.getString("public")), WebPushCrypto.decode(session.getString("private")), WebPushCrypto.decode(session.getString("auth")))

    private fun notification(json: JSONObject) {
        if (json.optString("channelID") != session.optString("channel")) return
        val version = json.getString("version")
        val code = if (json.isNull("data")) 100 else {
            val decrypted = runCatching {
                val headers = json.optJSONObject("headers") ?: JSONObject()
                val map = headers.keys().asSequence().associateWith { headers.getString(it) }
                WebPushCrypto.decrypt(WebPushCrypto.decode(json.getString("data")), map, keys())
            }
            decrypted.fold(onSuccess = { bytes ->
                runCatching {
                    val post = PushParser.parse(JSONObject(String(bytes, Charsets.UTF_8)), System.currentTimeMillis())
                    if (post == null) app.status("受信中・投稿者を特定できない通知をスキップ")
                    else {
                        app.store.ingest(post, Preferences(app).automatic)
                        app.changed()
                        BlockSync.sync(app, post.handle)
                        app.status("受信中")
                    }
                    100
                }.getOrElse { app.status("通知の保存に失敗しました"); 102 }
            }, onFailure = { app.status("通知の復号に失敗しました"); 101 })
        }
        val update = JSONObject().put("channelID", session.getString("channel")).put("version", version).put("code", code)
        socket?.send(JSONObject().put("messageType", "ack").put("updates", JSONArray().put(update)).toString())
    }

    private fun ready() { handshake?.cancel(false); failures = 0; app.status("受信中") }

    private fun fail(message: String, reconnect: Boolean, seconds: Int? = null) {
        handshake?.cancel(false)
        socket?.cancel(); socket = null
        app.status(message)
        if (!reconnect || stopped) return
        retry?.cancel(false)
        val delay = seconds ?: (5L shl failures.coerceAtMost(6)).coerceAtMost(300).toInt()
        failures = (failures + 1).coerceAtMost(7)
        retry = actor.schedule({ connect() }, delay.toLong(), TimeUnit.SECONDS)
    }
}
