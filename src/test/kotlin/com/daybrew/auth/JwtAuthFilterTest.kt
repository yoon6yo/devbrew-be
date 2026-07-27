package com.daybrew.auth

import com.daybrew.config.DayBrewProperties
import io.mockk.mockk
import io.mockk.verify
import jakarta.servlet.FilterChain
import jakarta.servlet.http.Cookie
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder

class JwtAuthFilterTest {

    private val jwtTokenProvider = JwtTokenProvider(
        DayBrewProperties(
            jwt = DayBrewProperties.JwtProps(
                secret = "test-secret-key-minimum-32-characters-ok",
                expirationMs = 3_600_000,
            )
        )
    )
    private lateinit var filter: JwtAuthFilter
    private val chain = mockk<FilterChain>(relaxed = true)

    @BeforeEach
    fun setUp() {
        filter = JwtAuthFilter(jwtTokenProvider)
        SecurityContextHolder.clearContext()
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    private fun token(email: String = "user@example.com", role: UserRole = UserRole.USER) =
        jwtTokenProvider.generate(1L, email, role)

    // ── Authorization header ──────────────────────────────────────────────────

    @Test
    fun `sets authentication for valid Bearer token`() {
        val req = MockHttpServletRequest().apply { addHeader("Authorization", "Bearer ${token()}") }

        filter.doFilter(req, MockHttpServletResponse(), chain)

        val auth = SecurityContextHolder.getContext().authentication
        assertThat(auth).isNotNull
        assertThat(auth!!.name).isEqualTo("user@example.com")
    }

    @Test
    fun `sets ROLE_ADMIN authority for admin token`() {
        val req = MockHttpServletRequest().apply {
            addHeader("Authorization", "Bearer ${token(role = UserRole.ADMIN)}")
        }

        filter.doFilter(req, MockHttpServletResponse(), chain)

        val auth = SecurityContextHolder.getContext().authentication
        assertThat(auth!!.authorities.map { it.authority }).containsExactly("ROLE_ADMIN")
    }

    @Test
    fun `does not authenticate with lowercase bearer prefix`() {
        val req = MockHttpServletRequest().apply { addHeader("Authorization", "bearer ${token()}") }

        filter.doFilter(req, MockHttpServletResponse(), chain)

        assertThat(SecurityContextHolder.getContext().authentication).isNull()
    }

    @Test
    fun `does not authenticate with malformed token`() {
        val req = MockHttpServletRequest().apply { addHeader("Authorization", "Bearer not.a.jwt") }

        filter.doFilter(req, MockHttpServletResponse(), chain)

        assertThat(SecurityContextHolder.getContext().authentication).isNull()
    }

    // ── Cookie fallback ───────────────────────────────────────────────────────

    @Test
    fun `sets authentication from access_token cookie`() {
        val req = MockHttpServletRequest().apply { setCookies(Cookie("access_token", token())) }

        filter.doFilter(req, MockHttpServletResponse(), chain)

        assertThat(SecurityContextHolder.getContext().authentication).isNotNull
        assertThat(SecurityContextHolder.getContext().authentication!!.name).isEqualTo("user@example.com")
    }

    // ── No token ──────────────────────────────────────────────────────────────

    @Test
    fun `does not set authentication when no token present`() {
        filter.doFilter(MockHttpServletRequest(), MockHttpServletResponse(), chain)

        assertThat(SecurityContextHolder.getContext().authentication).isNull()
    }

    // ── Filter chain continuity ───────────────────────────────────────────────

    @Test
    fun `always calls filter chain with valid token`() {
        val req = MockHttpServletRequest().apply { addHeader("Authorization", "Bearer ${token()}") }

        filter.doFilter(req, MockHttpServletResponse(), chain)

        verify(exactly = 1) { chain.doFilter(any(), any()) }
    }

    @Test
    fun `always calls filter chain without token`() {
        filter.doFilter(MockHttpServletRequest(), MockHttpServletResponse(), chain)

        verify(exactly = 1) { chain.doFilter(any(), any()) }
    }
}
