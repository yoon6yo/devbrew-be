package com.daybrew.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest

class HttpUtilsTest {

    // ── Public address — no header lookup ────────────────────────────────────

    @Test
    fun `returns remoteAddr for public IP directly`() {
        val req = MockHttpServletRequest().apply { remoteAddr = "203.0.113.5" }
        assertThat(resolveClientIp(req)).isEqualTo("203.0.113.5")
    }

    @Test
    fun `ignores X-Real-IP header when direct peer is a public IP`() {
        val req = MockHttpServletRequest().apply {
            remoteAddr = "203.0.113.5"
            addHeader("X-Real-IP", "1.2.3.4")
        }
        assertThat(resolveClientIp(req)).isEqualTo("203.0.113.5")
    }

    // ── Private address — falls back to X-Real-IP ────────────────────────────

    @Test
    fun `returns X-Real-IP when direct peer is localhost`() {
        val req = MockHttpServletRequest().apply {
            remoteAddr = "127.0.0.1"
            addHeader("X-Real-IP", "1.2.3.4")
        }
        assertThat(resolveClientIp(req)).isEqualTo("1.2.3.4")
    }

    @Test
    fun `returns X-Real-IP when direct peer is IPv6 loopback`() {
        val req = MockHttpServletRequest().apply {
            remoteAddr = "::1"
            addHeader("X-Real-IP", "1.2.3.4")
        }
        assertThat(resolveClientIp(req)).isEqualTo("1.2.3.4")
    }

    @Test
    fun `returns X-Real-IP when direct peer is 10-dot private range`() {
        val req = MockHttpServletRequest().apply {
            remoteAddr = "10.244.0.1"
            addHeader("X-Real-IP", "5.6.7.8")
        }
        assertThat(resolveClientIp(req)).isEqualTo("5.6.7.8")
    }

    @Test
    fun `returns X-Real-IP when direct peer is 192-168 private range`() {
        val req = MockHttpServletRequest().apply {
            remoteAddr = "192.168.1.100"
            addHeader("X-Real-IP", "9.10.11.12")
        }
        assertThat(resolveClientIp(req)).isEqualTo("9.10.11.12")
    }

    @Test
    fun `returns X-Real-IP when direct peer is 172-16 through 172-31 private range`() {
        val req = MockHttpServletRequest().apply {
            remoteAddr = "172.20.0.5"
            addHeader("X-Real-IP", "13.14.15.16")
        }
        assertThat(resolveClientIp(req)).isEqualTo("13.14.15.16")
    }

    // ── Private peer but no X-Real-IP header ─────────────────────────────────

    @Test
    fun `falls back to remoteAddr when private peer has no X-Real-IP header`() {
        val req = MockHttpServletRequest().apply { remoteAddr = "127.0.0.1" }
        assertThat(resolveClientIp(req)).isEqualTo("127.0.0.1")
    }

    @Test
    fun `falls back to remoteAddr when X-Real-IP header is blank`() {
        val req = MockHttpServletRequest().apply {
            remoteAddr = "10.0.0.1"
            addHeader("X-Real-IP", "   ")
        }
        assertThat(resolveClientIp(req)).isEqualTo("10.0.0.1")
    }
}
