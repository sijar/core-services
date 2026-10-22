package com.example.coreservices.common

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant

@Component
class WindowRateLimiter(
    private val clock: Clock,
    @Value("\${security.rate-limit.window}") private val window: Duration
) {
    private data class Counter(val start: Instant, var used: Int)
    private val counters = Caffeine.newBuilder().maximumSize(100_000).expireAfterAccess(Duration.ofHours(1)).build<String, Counter>()

    @Synchronized
    fun allow(key: String, maximum: Int): Boolean {
        val now = clock.instant()
        val current = counters.getIfPresent(key)
        if (current == null || !now.isBefore(current.start.plus(window))) {
            counters.put(key, Counter(now, 1))
            return true
        }
        if (current.used >= maximum) return false
        current.used++
        return true
    }
}
