package dev.angelic.angel.data

import android.app.Application
import dev.angelic.angel.model.Post
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
@Config(sdk = [35], application = Application::class)
class TimelineStoreTest {
    private lateinit var store: TimelineStore
    @Before fun setup() {
        RuntimeEnvironment.getApplication().deleteDatabase("timeline.db")
        store = TimelineStore(RuntimeEnvironment.getApplication())
    }
    @After fun teardown() { store.close() }
    private fun post(id: Int, handle: String = "alice") = Post(id.toString(), handle, "Author", "衝撃！生成AIで誰でも一瞬で完成。今すぐフォロー", id.toLong())

    @Test fun thirdDistinctPostHidesAllPastPostsImmediatelyAndSurvivesReopen() {
        store.ingest(post(1), true); store.ingest(post(2), true)
        assertEquals(2, store.posts().size)
        assertFalse(store.ingest(post(2), true))
        assertTrue(store.blocks().isEmpty())
        store.ingest(post(3), true)
        assertTrue(store.posts().isEmpty())
        assertEquals(3, store.posts("alice", true).size)
        store.close(); store = TimelineStore(RuntimeEnvironment.getApplication())
        assertEquals("alice", store.blocks().single().handle)
        assertTrue(store.posts().isEmpty())
    }

    @Test fun unrelatedAccountsAndDisabledDetectorRemainVisible() {
        (1..3).forEach { store.ingest(post(it), true) }
        store.ingest(post(4, "bob"), true)
        assertEquals("bob", store.posts().single().handle)
        store.reset()
        (1..3).forEach { store.ingest(post(it), false) }
        assertEquals(3, store.posts().size)
    }

    @Test fun unblockRestoresHistoryAndPreventsReblocking() {
        (1..3).forEach { store.ingest(post(it), true) }
        store.unblock("alice")
        store.ingest(post(4), true)
        assertEquals(4, store.posts().size)
        assertTrue(store.isAllowed("alice"))
        store.close(); store = TimelineStore(RuntimeEnvironment.getApplication())
        assertTrue(store.isAllowed("alice"))
    }

    @Test fun tabsRejectDuplicatesAndPersistWithoutDeletingHistory() {
        assertTrue(store.addTab("@Alice")); assertFalse(store.addTab("alice"))
        assertTrue(store.addTab("bob")); assertFalse(store.addTab("bad/id"))
        store.ingest(post(1), false)
        store.close(); store = TimelineStore(RuntimeEnvironment.getApplication())
        assertEquals(listOf("alice", "bob"), store.tabs())
        store.removeTab("alice")
        assertEquals(listOf("bob"), store.tabs())
        assertEquals(1, store.posts("alice").size)
        assertTrue(store.posts("bob").isEmpty())
    }

    @Test fun invalidPostsAndSqlLikeHandlesCannotEnterStorage() {
        assertFalse(store.ingest(post(1, "'; DROP TABLE posts;--"), true))
        assertFalse(store.ingest(post(1).copy(text = ""), true))
        assertFalse(store.ingest(post(1).copy(id = ""), true))
        assertTrue(store.posts().isEmpty())
    }
}
