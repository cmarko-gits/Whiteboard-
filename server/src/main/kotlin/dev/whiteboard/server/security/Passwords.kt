package dev.whiteboard.server.security

import dev.whiteboard.server.ServiceFailure
import dev.whiteboard.server.invalidRequest
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.Semaphore
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters

interface Passwords {
    fun hash(password: String): String
    fun verify(password: String, encoded: String): Boolean
}

fun validatePassword(password: String) {
    if (password.length !in 12..128 || password.toByteArray(Charsets.UTF_8).size > 512) invalidRequest()
}

/** Two concurrent 64 MiB jobs bound the process's password-hashing memory use. */
class ArgonPasswords : Passwords {
    private val jobs = Semaphore(2)
    private val random = SecureRandom()
    private val encoder = Base64.getEncoder().withoutPadding()

    override fun hash(password: String): String {
        validatePassword(password)
        val salt = ByteArray(16).also(random::nextBytes)
        return "\$argon2id\$v=19\$m=65536,t=3,p=1\$${encoder.encodeToString(salt)}\$${encoder.encodeToString(derive(password, salt))}"
    }

    override fun verify(password: String, encoded: String): Boolean {
        if (password.length !in 12..128 || password.toByteArray(Charsets.UTF_8).size > 512) return false
        val parts = encoded.split('$')
        if (parts.size != 6 || parts[1] != "argon2id" || parts[2] != "v=19" || parts[3] != "m=65536,t=3,p=1") return false
        val salt = runCatching { Base64.getDecoder().decode(parts[4]) }.getOrNull() ?: return false
        val expected = runCatching { Base64.getDecoder().decode(parts[5]) }.getOrNull() ?: return false
        if (salt.size != 16 || expected.size != 32) return false
        return MessageDigest.isEqual(expected, derive(password, salt))
    }

    private fun derive(password: String, salt: ByteArray): ByteArray {
        if (!jobs.tryAcquire()) throw ServiceFailure(429, "password_service_busy")
        val input = password.toByteArray(Charsets.UTF_8)
        try {
            val parameters = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withMemoryAsKB(65536).withIterations(3).withParallelism(1).withSalt(salt).build()
            return ByteArray(32).also {
                Argon2BytesGenerator().apply { init(parameters) }.generateBytes(input, it)
            }
        } finally {
            input.fill(0)
            jobs.release()
        }
    }
}
