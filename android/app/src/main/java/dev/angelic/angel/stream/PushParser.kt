package dev.angelic.angel.stream

import dev.angelic.angel.model.Post
import dev.angelic.angel.model.normalizeHandle
import java.net.URI
import org.json.JSONObject

/** Never infer the account to block from a mutable display name or body mention. */
object PushParser {
    fun parse(payload: JSONObject, received: Long): Post? {
        val category = payload.optString("tag").substringBefore('-').lowercase(java.util.Locale.ROOT)
        if (category in setOf("retweet", "favorite", "like", "follow", "message")) return null
        val nested = payload.optJSONObject("data")
        val url = sequenceOf(nested?.optString("url"), payload.optString("url"), nested?.optString("uri"))
            .filterNotNull().firstOrNull { it.isNotBlank() } ?: return null
        val absolute = if (url.startsWith('/') && !url.startsWith("//")) "https://x.com$url" else url
        val uri = runCatching { URI(absolute) }.getOrNull() ?: return null
        if (uri.scheme != "https" || uri.host !in setOf("x.com", "twitter.com", "www.x.com", "www.twitter.com") || uri.userInfo != null || uri.port !in setOf(-1, 443)) return null
        val match = Regex("^/([A-Za-z0-9_]{1,15})/status/([0-9]{1,25})/?$").matchEntire(uri.path) ?: return null
        val handle = normalizeHandle(match.groupValues[1]) ?: return null
        if (handle == "i") return null
        val body = payload.optString("body").ifBlank { payload.optString("text") }
        if (body.isBlank()) return null
        return Post(match.groupValues[2], handle, payload.optString("title", handle), body, received, "https://x.com/$handle/status/${match.groupValues[2]}")
    }
}
