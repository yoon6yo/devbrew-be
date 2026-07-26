package com.daybrew.auth

import com.daybrew.config.DayBrewProperties
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class JwtTokenProviderTest {

    private val props = DayBrewProperties(
        jwt = DayBrewProperties.JwtProps(
            secret = "test-secret-key-minimum-32-characters-ok",
            expirationMs = 3_600_000,
        )
    )
    private val provider = JwtTokenProvider(props)

    @Test
    fun `generated token is valid`() {
        val token = provider.generate(1L, "user@example.com", UserRole.USER)
        assertTrue(provider.validate(token))
    }

    @Test
    fun `claims are extracted correctly`() {
        val token = provider.generate(42L, "admin@example.com", UserRole.ADMIN)
        val claims = provider.getClaims(token)
        assertEquals(42L, claims.userId)
        assertEquals("admin@example.com", claims.email)
        assertEquals(UserRole.ADMIN, claims.role)
    }

    @Test
    fun `tampered token is rejected`() {
        val token = provider.generate(1L, "user@example.com", UserRole.USER)
        val tampered = token.dropLast(5) + "XXXXX"
        assertFalse(provider.validate(tampered))
    }

    @Test
    fun `expired token is rejected`() {
        val expiredProps = DayBrewProperties(
            jwt = DayBrewProperties.JwtProps(
                secret = "test-secret-key-minimum-32-characters-ok",
                expirationMs = -1_000,
            )
        )
        val expiredProvider = JwtTokenProvider(expiredProps)
        val token = expiredProvider.generate(1L, "user@example.com", UserRole.USER)
        assertFalse(expiredProvider.validate(token))
    }

    @Test
    fun `USER role claim is preserved in token`() {
        val token = provider.generate(5L, "user@example.com", UserRole.USER)
        assertEquals(UserRole.USER, provider.getClaims(token).role)
    }

    @Test
    fun `ADMIN role claim is preserved in token`() {
        val token = provider.generate(1L, "admin@daybrew.local", UserRole.ADMIN)
        assertEquals(UserRole.ADMIN, provider.getClaims(token).role)
    }
}
