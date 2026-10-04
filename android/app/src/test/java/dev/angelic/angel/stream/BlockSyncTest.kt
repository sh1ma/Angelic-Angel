package dev.angelic.angel.stream

import dev.angelic.angel.data.AngelApplication
import dev.angelic.angel.data.Preferences
import dev.angelic.angel.model.Post
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = AngelApplication::class)
class BlockSyncTest {
    private val server = MockWebServer()
    private lateinit var app: AngelApplication
    private lateinit var prefs: Preferences
    private lateinit var client: XClient
    private val secrets = JSONObject().put("auth_token", "test-token").put("ct0", "test-csrf")

    @Before fun setup() {
        app = RuntimeEnvironment.getApplication() as AngelApplication
        app.store.reset()
        prefs = Preferences(app)
        prefs.clearBlockResults()
        prefs.remoteBlock = true
        server.start()
        client = XClient(OkHttpClient.Builder().followRedirects(false).build(), server.url("/i/api/1.1").toString())
    }

    @After fun teardown() { server.shutdown(); app.store.close() }

    private fun sync(retry: Boolean = false) = BlockSync.sync(app, "alice", retry, client) { secrets }
    private fun hide() {
        app.store.ingest(Post("1", "alice", "Alice", "test body", 1), false)
        app.store.block("alice", "test")
    }

    @Test fun disabledRemoteAndVisibleAuthorsNeverSendRequests() {
        sync()
        hide()
        prefs.remoteBlock = false
        sync()
        assertEquals(0, server.requestCount)
        assertEquals("アプリ内で非表示", prefs.blockResult("alice"))
    }

    @Test fun confirmedBlockIsNotRepeatedForAnotherNotification() {
        hide()
        server.enqueue(MockResponse().setBody("{\"screen_name\":\"alice\",\"blocking\":true}"))
        sync(); sync()
        assertEquals(1, server.requestCount)
        assertEquals("X上のブロック確認済み", prefs.blockResult("alice"))
        assertTrue(app.store.posts().isEmpty())
    }

    @Test fun apiFailureKeepsLocalHidingAndRequiresExplicitRetry() {
        hide()
        server.enqueue(MockResponse().setResponseCode(429))
        sync(); sync()
        assertEquals(1, server.requestCount)
        assertTrue(prefs.blockResult("alice").contains("HTTP 429"))
        assertTrue(app.store.posts().isEmpty())
        server.enqueue(MockResponse().setBody("{\"screen_name\":\"alice\",\"blocking\":true}"))
        sync(retry = true)
        assertEquals(2, server.requestCount)
        assertEquals("X上のブロック確認済み", prefs.blockResult("alice"))
    }

    @Test fun unreadableSecretsAreRecordedWithoutNetworkOrUnhiding() {
        hide()
        BlockSync.sync(app, "alice", x = client, readSecrets = { error("test storage failure") })
        assertEquals(0, server.requestCount)
        assertEquals("X上の操作に失敗。アプリ内は非表示", prefs.blockResult("alice"))
        assertTrue(app.store.posts().isEmpty())
    }
}
