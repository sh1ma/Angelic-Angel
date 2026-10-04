package dev.angelic.angel.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HypeDetectorTest {
    private fun post(id: Int, text: String = "衝撃！生成AIで誰でも一瞬で完成。今すぐフォロー") =
        Post(id.toString(), "author", "Author", text, id.toLong())

    @Test fun normalAndOffTopicPostsDoNotTrigger() {
        assertFalse(HypeDetector.assess("生成AIの評価結果を公開しました。誤答率は12%でした。").suspicious)
        assertFalse(HypeDetector.assess("衝撃の展開、誰でも参加できるスポーツ大会").suspicious)
        assertFalse(HypeDetector.assess("今日は衝撃的なAI論文を読んだ").suspicious)
        assertFalse(HypeDetector.assess("").suspicious)
    }

    @Test fun independentSignalsAndUnicodeNormalizationAreRequired() {
        assertTrue(HypeDetector.assess("衝撃！ＡＩなら１００％誰でも成功").suspicious)
        assertEquals(3, HypeDetector.assess(post(1).text).reasons.size)
    }

    @Test fun repeatedDeliveryAndTwoPostsCannotBlock() {
        assertFalse(HypeDetector.shouldBlock(List(8) { post(1) }))
        assertFalse(HypeDetector.shouldBlock(listOf(post(1), post(2))))
        assertTrue(HypeDetector.shouldBlock(listOf(post(1), post(2), post(3))))
    }

    @Test fun windowAndMajorityPreventOldEvidenceFromDominating() {
        val suspicious = (1..3).map { post(it) }
        assertTrue(HypeDetector.shouldBlock(suspicious + (4..6).map { post(it, "普通の投稿") }))
        assertFalse(HypeDetector.shouldBlock(suspicious + (4..7).map { post(it, "普通の投稿") }))
        assertFalse(HypeDetector.shouldBlock(suspicious + (4..13).map { post(it, "普通の投稿") }))
    }

    @Test fun handlesAreCaseInsensitiveButNeverInferredFromNames() {
        assertEquals("alice_1", normalizeHandle(" @Alice_1 "))
        listOf("", "a/b", "表示名", "a".repeat(16), "a b", "@a\n@b").forEach {
            assertNull(normalizeHandle(it))
        }
    }
}
