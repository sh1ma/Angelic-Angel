package dev.angelic.angel.stream

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class XClientTest {
    private val server = MockWebServer()
    private lateinit var client: XClient
    private val credentials = JSONObject().put("auth_token", "test-token").put("ct0", "test-csrf")
    @Before fun setup() {
        server.start()
        client = XClient(OkHttpClient.Builder().followRedirects(false).build(), server.url("/i/api/1.1").toString())
    }
    @After fun teardown() { server.shutdown() }

    @Test fun blockConfirmsIdentityAndState() {
        server.enqueue(MockResponse().setBody("{\"screen_name\":\"Alice\",\"blocking\":true}"))
        assertTrue(client.block(credentials, "@Alice").success)
        val request = server.takeRequest()
        assertEquals("/i/api/1.1/blocks/create.json", request.path)
        assertEquals("POST", request.method)
        assertEquals("screen_name=alice&skip_status=true", request.body.readUtf8())
        assertEquals("test-csrf", request.getHeader("x-csrf-token"))
    }

    @Test fun failureRedirectAndUnexpectedSuccessAreNotConfirmed() {
        listOf(MockResponse().setResponseCode(401), MockResponse().setResponseCode(429),
            MockResponse().setResponseCode(302).addHeader("Location", "https://example.com"),
            MockResponse().setBody("{}"), MockResponse().setBody("not json"),
            MockResponse().setBody("{\"screen_name\":\"other\",\"blocking\":true}"),
            MockResponse().setBody("{\"screen_name\":\"alice\",\"blocking\":false}"),
            MockResponse().setBody("{\"errors\":[]}")).forEach { response ->
            server.enqueue(response)
            assertFalse(client.block(credentials, "alice").success)
        }
    }

    @Test fun invalidCredentialsNeverSendRequest() {
        assertFalse(client.block(JSONObject(), "alice").success)
        assertFalse(client.block(JSONObject().put("auth_token", "a; injected=b").put("ct0", "c"), "alice").success)
        assertFalse(client.block(credentials, "invalid/name").success)
        assertEquals(0, server.requestCount)
    }

    @Test fun registrationContainsPublicSubscriptionMaterialOnly() {
        server.enqueue(MockResponse().setBody("{}"))
        val keys = WebPushCrypto.generate()
        assertTrue(client.register(credentials, "https://push.example.test/endpoint", keys).success)
        val request = server.takeRequest()
        assertEquals("/i/api/1.1/notifications/settings/login.json", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains(WebPushCrypto.encode(keys.publicKey)))
        assertFalse(body.contains(WebPushCrypto.encode(keys.privateKey)))
    }
}
