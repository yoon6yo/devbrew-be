package com.devbrew.auth

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
}
