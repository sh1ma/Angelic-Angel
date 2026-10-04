package dev.angelic.angel.stream

import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPrivateKeySpec
import java.security.spec.ECPublicKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** RFC 8291 / RFC 8188 plus the legacy aesgcm format used by the existing CLI. */
object WebPushCrypto {
    data class Keys(val publicKey: ByteArray, val privateKey: ByteArray, val auth: ByteArray)
    fun encode(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    fun decode(value: String): ByteArray = Base64.getUrlDecoder().decode(value)
    private fun ascii(value: String) = value.toByteArray(Charsets.US_ASCII)
    private fun scalar(value: BigInteger): ByteArray = value.toByteArray().takeLast(32).toByteArray().let { ByteArray(32 - it.size) + it }

    fun generate(): Keys {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"))
        val pair = generator.generateKeyPair()
        val public = pair.public as ECPublicKey
        return Keys(byteArrayOf(4) + scalar(public.w.affineX) + scalar(public.w.affineY), scalar((pair.private as ECPrivateKey).s), ByteArray(16).also { SecureRandom().nextBytes(it) })
    }

    private fun hmac(key: ByteArray, value: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(key, "HmacSHA256")); doFinal(value)
    }
    private fun expand(key: ByteArray, info: ByteArray, length: Int) = hmac(key, info + byteArrayOf(1)).copyOf(length)
    private fun shared(keys: Keys, sender: ByteArray): ByteArray {
        require(sender.size == 65 && sender[0] == 4.toByte())
        val parameters = AlgorithmParameters.getInstance("EC").apply { init(ECGenParameterSpec("secp256r1")) }.getParameterSpec(ECParameterSpec::class.java)
        val factory = KeyFactory.getInstance("EC")
        val private = factory.generatePrivate(ECPrivateKeySpec(BigInteger(1, keys.privateKey), parameters))
        val point = ECPoint(BigInteger(1, sender.copyOfRange(1, 33)), BigInteger(1, sender.copyOfRange(33, 65)))
        val public = factory.generatePublic(ECPublicKeySpec(point, parameters))
        return KeyAgreement.getInstance("ECDH").run { init(private); doPhase(public, true); generateSecret() }
    }

    fun decrypt(data: ByteArray, headers: Map<String, String>, keys: Keys): ByteArray {
        require(data.size <= 65536 && keys.auth.size == 16)
        return when (headers["encoding"] ?: "aes128gcm") {
            "aes128gcm" -> modern(data, keys)
            "aesgcm" -> legacy(data, headers, keys)
            else -> throw IllegalArgumentException("Unsupported push encoding")
        }
    }

    private fun modern(data: ByteArray, keys: Keys): ByteArray {
        require(data.size >= 103)
        val buffer = ByteBuffer.wrap(data)
        val salt = ByteArray(16).also { buffer.get(it) }
        val recordSize = buffer.int.toLong() and 0xffffffffL
        require(buffer.get().toInt() and 255 == 65)
        val sender = ByteArray(65).also { buffer.get(it) }
        // Web Push is a single record, unlike general encrypted HTTP content.
        require(recordSize > buffer.remaining())
        val ikm = expand(hmac(keys.auth, shared(keys, sender)), ascii("WebPush: info\u0000") + keys.publicKey + sender, 32)
        val prk = hmac(salt, ikm)
        val key = expand(prk, ascii("Content-Encoding: aes128gcm\u0000"), 16)
        val nonce = expand(prk, ascii("Content-Encoding: nonce\u0000"), 12)
        val plain = aes(key, nonce, data.copyOfRange(86, data.size))
        val delimiter = plain.indexOfLast { it != 0.toByte() }
        require(delimiter >= 0 && plain[delimiter] == 2.toByte())
        return plain.copyOf(delimiter)
    }

    private fun parameter(value: String?, name: String): String = value.orEmpty().split(';').map { it.trim() }
        .firstOrNull { it.startsWith("$name=") }?.substringAfter('=')?.trim('"')
        ?: throw IllegalArgumentException("Missing encryption header")

    private fun legacy(data: ByteArray, headers: Map<String, String>, keys: Keys): ByteArray {
        // The original CLI and the Web Push draft accept a single legacy record.
        require(data.size in 18..4111)
        val sender = decode(parameter(headers["crypto_key"], "dh"))
        val salt = decode(parameter(headers["encryption"], "salt"))
        require(salt.size == 16)
        val context = ascii("P-256\u0000") + byteArrayOf(0, 65) + keys.publicKey + byteArrayOf(0, 65) + sender
        val ikm = expand(hmac(keys.auth, shared(keys, sender)), ascii("Content-Encoding: auth\u0000"), 32)
        val prk = hmac(salt, ikm)
        val key = expand(prk, ascii("Content-Encoding: aesgcm\u0000") + context, 16)
        val nonce = expand(prk, ascii("Content-Encoding: nonce\u0000") + context, 12)
        val plain = aes(key, nonce, data)
        require(plain.size >= 2)
        val padding = ((plain[0].toInt() and 255) shl 8) or (plain[1].toInt() and 255)
        require(padding <= plain.size - 2 && plain.copyOfRange(2, 2 + padding).all { it == 0.toByte() })
        return plain.copyOfRange(2 + padding, plain.size)
    }

    private fun aes(key: ByteArray, nonce: ByteArray, data: ByteArray): ByteArray = Cipher.getInstance("AES/GCM/NoPadding").run {
        init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce)); doFinal(data)
    }
}
