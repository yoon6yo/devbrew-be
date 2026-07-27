package com.daybrew.auth

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LoginRateLimiterTest {

    private lateinit var limiter: LoginRateLimiter

    @BeforeEach
    fun setUp() {
        limiter = LoginRateLimiter()
    }

    @Test
    fun `not blocked initially`() {
        assertThat(limiter.isBlocked("1.2.3.4")).isFalse()
    }

    @Test
    fun `not blocked before threshold`() {
        repeat(LoginRateLimiter.MAX_FAILURES - 1) { limiter.recordFailure("1.2.3.4") }
        assertThat(limiter.isBlocked("1.2.3.4")).isFalse()
    }

    @Test
    fun `blocked after max failures`() {
        repeat(LoginRateLimiter.MAX_FAILURES) { limiter.recordFailure("1.2.3.4") }
        assertThat(limiter.isBlocked("1.2.3.4")).isTrue()
    }

    @Test
    fun `success clears failure count`() {
        repeat(LoginRateLimiter.MAX_FAILURES - 1) { limiter.recordFailure("1.2.3.4") }
        limiter.recordSuccess("1.2.3.4")
        repeat(LoginRateLimiter.MAX_FAILURES - 1) { limiter.recordFailure("1.2.3.4") }
        assertThat(limiter.isBlocked("1.2.3.4")).isFalse()
    }

    @Test
    fun `different IPs are tracked independently`() {
        repeat(LoginRateLimiter.MAX_FAILURES) { limiter.recordFailure("1.2.3.4") }
        assertThat(limiter.isBlocked("5.6.7.8")).isFalse()
    }

    @Test
    fun `retryAfterSeconds returns positive value when key is locked`() {
        repeat(LoginRateLimiter.MAX_FAILURES) { limiter.recordFailure("1.2.3.4") }
        assertThat(limiter.isBlocked("1.2.3.4")).isTrue()
        assertThat(limiter.retryAfterSeconds("1.2.3.4")).isGreaterThanOrEqualTo(1L)
        assertThat(limiter.retryAfterSeconds("1.2.3.4"))
            .isLessThanOrEqualTo(LoginRateLimiter.LOCKOUT_MS / 1000 + 1)
    }

    @Test
    fun `retryAfterSeconds returns 1 for unknown key`() {
        assertThat(limiter.retryAfterSeconds("unknown")).isEqualTo(1L)
    }
}
