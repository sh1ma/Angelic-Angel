package dev.angelic.angel.stream

import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WebPushCryptoTest {
    private fun decode(value: String) = WebPushCrypto.decode(value)
    private val keys = WebPushCrypto.Keys(
        decode("BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4"),
        decode("q1dXpw3UpT5VOmu_cf_v6ih07Aems3njxI-JWgLcM94"),
        decode("BTBZMqHH6r4Tts7J_aSIgg"),
    )
    private val sender = decode("BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8")
    private val encrypted = decode("DGv6ra1nlYgDCS1FRnbzlw") + ByteBuffer.allocate(4).putInt(4096).array() + byteArrayOf(65) + sender +
        decode("8pfeW0KbunFT06SuDKoJH9Ql87S1QUrdirN6GcG7sFz1y1sqLgVi1VhjVkHsUoEsbI_0LpXMuGvnzQ")

    @Test fun decryptsPublishedRfc8291AppendixA() {
        assertEquals("When I grow up, I want to be a watermelon", String(WebPushCrypto.decrypt(encrypted, emptyMap(), keys)))
        assertEquals("When I grow up, I want to be a watermelon", String(WebPushCrypto.decrypt(encrypted, mapOf("encoding" to "aes128gcm"), keys)))
    }

    @Test fun authenticationAndRecordBoundariesRejectCorruption() {
        assertThrows(Exception::class.java) { WebPushCrypto.decrypt(encrypted.copyOf().also { it[it.lastIndex] = 0 }, emptyMap(), keys) }
        assertThrows(Exception::class.java) { WebPushCrypto.decrypt(encrypted, emptyMap(), WebPushCrypto.generate()) }
        assertThrows(Exception::class.java) { WebPushCrypto.decrypt(encrypted.copyOf(50), emptyMap(), keys) }
        assertThrows(Exception::class.java) { WebPushCrypto.decrypt(encrypted.copyOf().also { it[20] = 64 }, emptyMap(), keys) }
        assertThrows(Exception::class.java) { WebPushCrypto.decrypt(encrypted.copyOf().also { it[18] = 0; it[19] = 1 }, emptyMap(), keys) }
        assertThrows(Exception::class.java) { WebPushCrypto.decrypt(encrypted, mapOf("encoding" to "unknown"), keys) }
        assertThrows(Exception::class.java) { WebPushCrypto.decrypt(encrypted, mapOf("encoding" to "aesgcm"), keys) }
    }

    @Test fun generatedKeysHaveProtocolSizes() {
        val fresh = WebPushCrypto.generate()
        assertEquals(65, fresh.publicKey.size)
        assertEquals(32, fresh.privateKey.size)
        assertEquals(16, fresh.auth.size)
    }

    @Test fun decryptsLegacyDraft04PublishedVectorAndRejectsMissingHeaders() {
        val legacyKeys = WebPushCrypto.Keys(
            decode("BCEkBjzL8Z3C-oi2Q7oE5t2Np-p7osjGLg93qUP0wvqRT21EEWyf0cQDQcakQMqz4hQKYOQ3il2nNZct4HgAUQU"),
            decode("9FWl15_QUQAWDaD3k3l50ZBZQJ4au27F1V4F0uLSD_M"),
            decode("R29vIGdvbyBnJyBqb29iIQ"),
        )
        val headers = mapOf("encoding" to "aesgcm", "encryption" to "salt=\"lngarbyKfMoi9Z75xYXmkg\"",
            "crypto_key" to "dh=\"BNoRDbb84JGm8g5Z5CFxurSqsXWJ11ItfXEWYVLE85Y7CYkDjXsIEc4aqxYaQ1G8BqkXCJ6DPpDrWtdWj_mugHU\"")
        val data = decode("6nqAQUME8hNqw5J3kl8cpVVJylXKYqZOeseZG8UueKpA")
        assertEquals("I am the walrus", String(WebPushCrypto.decrypt(data, headers, legacyKeys)))
        assertThrows(Exception::class.java) { WebPushCrypto.decrypt(data, headers - "crypto_key", legacyKeys) }
        assertThrows(Exception::class.java) { WebPushCrypto.decrypt(data, headers - "encryption", legacyKeys) }
        assertThrows(Exception::class.java) { WebPushCrypto.decrypt(byteArrayOf(), headers, legacyKeys) }
        assertThrows(Exception::class.java) { WebPushCrypto.decrypt(data.copyOf().also { it[0] = 0 }, headers, legacyKeys) }
    }
}
