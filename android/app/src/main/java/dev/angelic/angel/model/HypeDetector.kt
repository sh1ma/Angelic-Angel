package dev.angelic.angel.model

import java.text.Normalizer
import java.util.Locale

/** Explainable text heuristic, not a claim about an author's intent or factual accuracy. */
object HypeDetector {
    data class Assessment(val suspicious: Boolean, val reasons: List<String>)

    private val topic = Regex("(?i)((?<![a-z0-9])AI(?![a-z0-9])|生成AI|人工知能|chatgpt|claude|gemini|gpt[- ]?[0-9]|LLM)")
    private val hype = listOf("やばすぎ", "ヤバすぎ", "衝撃", "革命", "世界が変わ", "完全終了", "人類終了", "神すぎ", "震えた")
    private val absolute = listOf("絶対", "100%", "誰でも", "必ず", "一瞬で", "全員", "知らないと損", "知らない人は損", "乗り遅れ")
    private val bait = listOf("拡散希望", "今すぐ", "リプ", "リポスト", "フォロー", "いいね", "RT", "詳細はプロフ", "無料配布")

    fun assess(text: String): Assessment {
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
        fun containsAny(words: List<String>) = words.any { normalized.contains(it.lowercase(Locale.ROOT)) }
        val reasons = buildList {
            if (containsAny(hype)) add("強い驚き・煽りの表現")
            if (containsAny(absolute)) add("一律の断定・取り残される不安の誘導")
            if (containsAny(bait)) add("反応・拡散・プロフィールへの誘導")
        }
        return Assessment(topic.containsMatchIn(normalized) && reasons.size >= 2, reasons)
    }

    /** Distinct posts only; a notification redelivery must never add another strike. */
    fun shouldBlock(posts: List<Post>): Boolean {
        val recent = posts.distinctBy { it.id }.sortedByDescending { it.receivedAt }.take(10)
        val suspicious = recent.count { assess(it.text).suspicious }
        return suspicious >= 3 && suspicious * 2 >= recent.size
    }
}
