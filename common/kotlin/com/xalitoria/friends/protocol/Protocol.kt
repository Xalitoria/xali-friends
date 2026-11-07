package com.xalitoria.friends.protocol

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Seals a [Message] for the plugin channel and opens one that arrived.
 *
 * Anything a client can send to a plugin channel reaches the plugins too, and these messages move players and change
 * what they are told, so each travels signed: the 32-byte HMAC-SHA256 of the message, then the message. The key is
 * Velocity's forwarding secret, which the proxy and every server already share.
 */
object Protocol {
    const val CHANNEL = "xalifriends:main"
    private const val MAC_BYTES = 32

    /** What a plugin message may carry before Bukkit refuses it. */
    const val MAX_BYTES = 32_000

    /** [message] signed with [secret]. */
    fun seal(secret: String, message: Message): ByteArray {
        val inner = message.encode()
        return ByteArrayOutputStream().also {
            DataOutputStream(it).apply { write(mac(secret, inner)); write(inner) }
        }.toByteArray()
    }

    /** The message in a [frame]; null when it is unsigned, forged, signed with another secret, unknown or cut short. */
    fun open(secret: String?, frame: ByteArray): Message? {
        val secret = secret.orEmpty()
        if (secret.isBlank() || frame.size <= MAC_BYTES) return null
        val input = DataInputStream(ByteArrayInputStream(frame))
        val mac = input.readNBytes(MAC_BYTES)
        val inner = input.readAllBytes()
        if (!MessageDigest.isEqual(mac, mac(secret, inner))) return null
        return try {
            Message.decode(inner)
        } catch (e: java.io.IOException) {
            null
        } catch (e: RuntimeException) {
            null
        }
    }

    // Whitespace around the secret is trimmed on both sides, since a secret file usually ends in a newline.
    private fun mac(secret: String, data: ByteArray): ByteArray = Mac.getInstance("HmacSHA256")
        .apply { init(SecretKeySpec(secret.trim().toByteArray(), "HmacSHA256")) }
        .doFinal(data)
}
