package com.example.coreservices.common

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class WindowRateLimiterTest {
    @Test
    fun `limits requests within a fixed window`() {
        val clock = Clock.fixed(Instant.parse("2026-10-22T00:00:00Z"), ZoneOffset.UTC)
        val limiter = WindowRateLimiter(clock, Duration.ofMinutes(15))

        assertTrue(limiter.allow("login:user", 2))
        assertTrue(limiter.allow("login:user", 2))
        assertFalse(limiter.allow("login:user", 2))
    }
}
