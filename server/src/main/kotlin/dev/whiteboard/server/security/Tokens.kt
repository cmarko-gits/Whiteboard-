package dev.whiteboard.server.security

import dev.whiteboard.server.unauthenticated
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.HexFormat

object Tokens {
    private val random = SecureRandom()
    fun create(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))
    fun hash(token: String): String {
        if (!token.matches(Regex("[A-Za-z0-9_-]{43}"))) unauthenticated()
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.US_ASCII)))
    }
}
