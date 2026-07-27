package com.daybrew.idea

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class StarRateLimiterTest {

    private lateinit var limiter: StarRateLimiter

    @BeforeEach
    fun setUp() {
        limiter = StarRateLimiter()
    }

    @Test
    fun `first request is allowed`() {
        assertThat(limiter.checkAndRecord("1.2.3.4")).isNull()
    }

    @Test
    fun `requests up to MAX_REQUESTS are all allowed`() {
        repeat(StarRateLimiter.MAX_REQUESTS) {
            assertThat(limiter.checkAndRecord("1.2.3.4")).isNull()
        }
    }

    @Test
    fun `request exceeding MAX_REQUESTS returns positive Retry-After seconds`() {
        repeat(StarRateLimiter.MAX_REQUESTS) { limiter.checkAndRecord("1.2.3.4") }

        val retryAfter = limiter.checkAndRecord("1.2.3.4")

        assertThat(retryAfter).isNotNull
        assertThat(retryAfter!!).isGreaterThanOrEqualTo(1L)
        assertThat(retryAfter).isLessThanOrEqualTo(StarRateLimiter.WINDOW_MS / 1000 + 1)
    }

    @Test
    fun `different IPs are tracked independently`() {
        repeat(StarRateLimiter.MAX_REQUESTS) { limiter.checkAndRecord("blocked.ip") }

        assertThat(limiter.checkAndRecord("clean.ip")).isNull()
    }

    @Test
    fun `evictExpired runs without error on empty store`() {
        limiter.evictExpired()
    }

    @Test
    fun `evictExpired runs without error with active entries`() {
        repeat(5) { limiter.checkAndRecord("1.2.3.4") }

        limiter.evictExpired()

        // Entry still within window so limiter still tracks it
        assertThat(limiter.checkAndRecord("1.2.3.4")).isNull()
    }
}
