package dev.angelic.angel.stream

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PushParserTest {
    private fun payload(url: String) = JSONObject().put("title", "同じ表示名 @wrong").put("body", "投稿本文 @another")
        .put("data", JSONObject().put("url", url))

    @Test fun accountComesOnlyFromTrustedStatusUrl() {
        val post = PushParser.parse(payload("https://x.com/Alice/status/123?s=20"), 42)
        assertEquals("alice", post?.handle)
        assertEquals("123", post?.id)
        assertEquals("https://x.com/alice/status/123", post?.url)
    }

    @Test fun relativeUrlsFromTwitterDocumentedPushFormatAreSupported() {
        assertEquals("alice", PushParser.parse(payload("/Alice/status/123"), 42)?.handle)
        assertNull(PushParser.parse(payload("//evil.test/alice/status/123"), 42))
        assertNull(PushParser.parse(payload("/alice/status/123").put("tag", "retweet-123"), 42))
        assertNull(PushParser.parse(payload("/alice/status/123").put("tag", "favorite-123"), 42))
    }

    @Test fun rejectsUnknownIdentitiesAndForgedHosts() {
        listOf("https://x.com.evil.test/alice/status/1", "http://x.com/alice/status/1", "https://x.com@evil.test/alice/status/1",
            "https://x.com/i/status/123", "https://x.com/i/web/status/123", "https://x.com/alice", "javascript:alert(1)", "https://x.com/a/status/notanid").forEach {
            assertNull(it, PushParser.parse(payload(it), 1))
        }
        assertNull(PushParser.parse(JSONObject().put("title", "@alice").put("body", "test"), 1))
        assertNull(PushParser.parse(payload("https://x.com/alice/status/1").put("body", ""), 1))
    }
}
