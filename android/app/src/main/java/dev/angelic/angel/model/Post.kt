package dev.angelic.angel.model

import java.util.Locale

/** Only explicit handles identify accounts; display names are not unique. */
data class Post(
    val id: String,
    val handle: String,
    val displayName: String,
    val text: String,
    val receivedAt: Long,
    val url: String = "",
)

fun normalizeHandle(value: String): String? = value.trim().removePrefix("@")
    .takeIf { it.matches(Regex("[A-Za-z0-9_]{1,15}")) }
    ?.lowercase(Locale.ROOT)
