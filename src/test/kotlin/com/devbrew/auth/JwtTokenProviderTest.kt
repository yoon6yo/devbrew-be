package com.devbrew.auth

import com.devbrew.config.DevBrewProperties
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class JwtTokenProviderTest {

    private val props = DevBrewProperties(
        jwt = DevBrewProperties.JwtProps(
            secret = "test-secret-key-minimum-32-characters-ok",
            expirationMs = 3_600_000,
        )
    )
    private val provider = JwtTokenProvider(props)

    @Test
    fun `generated token is valid`() {
        val token = provider.generate("admin")
        assertTrue(provider.validate(token))
    }

    @Test
    fun `subject is extracted correctly`() {
        val token = provider.generate("admin")
        assertEquals("admin", provider.getSubject(token))
    }

    @Test
    fun `tampered token is rejected`() {
        val token = provider.generate("admin")
        val tampered = token.dropLast(5) + "XXXXX"
        assertFalse(provider.validate(tampered))
    }

    @Test
    fun `expired token is rejected`() {
        val expiredProps = DevBrewProperties(
            jwt = DevBrewProperties.JwtProps(
                secret = "test-secret-key-minimum-32-characters-ok",
                expirationMs = -1_000,
            )
        )
        val expiredProvider = JwtTokenProvider(expiredProps)
        val token = expiredProvider.generate("admin")
        assertFalse(expiredProvider.validate(token))
    }
}
