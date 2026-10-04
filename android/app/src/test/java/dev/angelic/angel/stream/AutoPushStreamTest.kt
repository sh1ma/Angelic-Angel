package dev.angelic.angel.stream

import dev.angelic.angel.data.AngelApplication
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = AngelApplication::class)
class AutoPushStreamTest {
    private val push = MockWebServer()
    private val api = MockWebServer()
    private val messages = LinkedBlockingQueue<JSONObject>()
    private val sockets = LinkedBlockingQueue<WebSocket>()
    private lateinit var app: AngelApplication
    private lateinit var stream: AutoPushStream
    private val saved = JSONObject().put("generation", "one").put("auth_token", "test").put("ct0", "test")
    private lateinit var serverSocket: WebSocket

    @Before fun setup() {
        app = RuntimeEnvironment.getApplication() as AngelApplication
        push.start(); api.start()
        push.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { sockets.offer(webSocket) }
            override fun onMessage(webSocket: WebSocket, text: String) { messages.offer(JSONObject(text)) }
        }))
        stream = AutoPushStream(app, { synchronized(saved) { JSONObject(saved.toString()) } },
            { transform -> synchronized(saved) { transform(saved) } }, push.url("/").toString(),
            XClient(OkHttpClient.Builder().followRedirects(false).build(), api.url("/i/api/1.1").toString()))
    }
    @After fun teardown() { stream.stop(); push.shutdown(); api.shutdown(); app.store.close() }
    private fun next(): JSONObject = requireNotNull(messages.poll(5, TimeUnit.SECONDS)) { "Expected protocol message" }
    private fun begin() {
        stream.start(); serverSocket = requireNotNull(sockets.poll(5, TimeUnit.SECONDS))
        assertEquals("hello", next().getString("messageType"))
    }
    private fun hello(uaid: String) { serverSocket.send(JSONObject().put("messageType", "hello").put("status", 200).put("uaid", uaid).toString()) }
    private fun registeredSession() {
        val keys = WebPushCrypto.generate()
        saved.put("session", JSONObject().put("uaid", "known").put("channel", "channel").put("registered", true)
            .put("public", WebPushCrypto.encode(keys.publicKey)).put("private", WebPushCrypto.encode(keys.privateKey)).put("auth", WebPushCrypto.encode(keys.auth)))
    }

    @Test fun registersNewSessionAndPersistsOnlyAfterXAccepts() {
        begin(); hello("fresh")
        val registration = next()
        assertEquals("register", registration.getString("messageType"))
        assertFalse(saved.has("session"))
        api.enqueue(MockResponse().setBody("{}"))
        serverSocket.send(JSONObject().put("messageType", "register").put("status", 200).put("channelID", registration.getString("channelID"))
            .put("pushEndpoint", "https://push.example.test/endpoint").toString())
        assertEquals("/i/api/1.1/notifications/settings/login.json", api.takeRequest(5, TimeUnit.SECONDS)?.path)
        serverSocket.send(JSONObject().put("messageType", "notification").put("channelID", registration.getString("channelID")).put("version", "v1").toString())
        assertEquals(100, next().getJSONArray("updates").getJSONObject(0).getInt("code"))
        synchronized(saved) { assertTrue(saved.getJSONObject("session").getBoolean("registered")) }
    }

    @Test fun resumedSessionAvoidsXAndAcknowledgesCorruptAndEmptyPayloadsSeparately() {
        registeredSession(); begin(); hello("known")
        serverSocket.send("{\"messageType\":\"notification\",\"channelID\":\"channel\",\"version\":\"v1\",\"data\":\"bad\"}")
        assertEquals(101, next().getJSONArray("updates").getJSONObject(0).getInt("code"))
        serverSocket.send("{\"messageType\":\"notification\",\"channelID\":\"channel\",\"version\":\"v2\"}")
        assertEquals(100, next().getJSONArray("updates").getJSONObject(0).getInt("code"))
        assertEquals(0, api.requestCount)
    }

    @Test fun invalidatedUaidRotatesKeysAndChannel() {
        registeredSession()
        val original = saved.getJSONObject("session").getString("public")
        begin(); hello("replacement")
        val registration = next()
        assertEquals("register", registration.getString("messageType"))
        assertTrue(registration.getString("channelID") != "channel")
        // Old credentials remain durable until the replacement registration succeeds.
        assertEquals(original, saved.getJSONObject("session").getString("public"))
    }
}
