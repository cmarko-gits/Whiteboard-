package dev.whiteboard.server.http

import dev.whiteboard.server.ServiceFailure
import java.time.Clock

/** A bounded per-process abuse gate. Production ingress must also enforce distributed limits. */
class RateLimiter(private val clock: Clock = Clock.systemUTC()) {
    private data class Window(val ends: Long, var attempts: Int)
    private val windows = mutableMapOf<String, Window>()

    @Synchronized
    fun check(key: String, limit: Int) {
        val now = clock.millis()
        if (windows.size >= 4096) windows.entries.removeAll { it.value.ends <= now }
        val existing = windows[key]
        if (existing == null || existing.ends <= now) {
            if (existing == null && windows.size >= 4096) throw ServiceFailure(429, "rate_limited")
            windows[key] = Window(now + 60_000, 1)
        } else {
            if (existing.attempts >= limit) throw ServiceFailure(429, "rate_limited")
            existing.attempts++
        }
    }
}
