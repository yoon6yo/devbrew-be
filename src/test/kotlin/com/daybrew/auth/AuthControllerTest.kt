package com.daybrew.auth

import com.daybrew.config.DayBrewProperties
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

class AuthControllerTest {

    private val userRepository = mockk<UserRepository>()
    private val jwtTokenProvider = JwtTokenProvider(
        DayBrewProperties(jwt = DayBrewProperties.JwtProps(
            secret = "test-secret-key-minimum-32-characters-ok",
            expirationMs = 3_600_000,
        ))
    )
    private val passwordEncoder = BCryptPasswordEncoder()
    private val rateLimiter = LoginRateLimiter()
    private lateinit var controller: AuthController

    @BeforeEach
    fun setUp() {
        controller = AuthController(userRepository, jwtTokenProvider, passwordEncoder, rateLimiter)
    }

    // ── /register ─────────────────────────────────────────────────────────────

    @Test
    fun `register creates user and returns 201 with token`() {
        val savedUser = User(id = 1L, email = "new@example.com", role = UserRole.USER)
        every { userRepository.existsByEmail("new@example.com") } returns false
        every { userRepository.save(any()) } returns savedUser

        val response = controller.register(RegisterRequest("new@example.com", "password123"))

        assertThat(response.statusCode).isEqualTo(HttpStatus.CREATED)
        assertThat(response.body?.token).isNotBlank()
    }

    @Test
    fun `register returns 409 when email already exists`() {
        every { userRepository.existsByEmail("dup@example.com") } returns true

        val response = controller.register(RegisterRequest("dup@example.com", "password123"))

        assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
        verify(exactly = 0) { userRepository.save(any()) }
    }

    @Test
    fun `register stores BCrypt-hashed password`() {
        val savedSlot = slot<User>()
        every { userRepository.existsByEmail(any()) } returns false
        every { userRepository.save(capture(savedSlot)) } answers { savedSlot.captured }

        controller.register(RegisterRequest("user@example.com", "mySecret8"))

        assertThat(savedSlot.captured.passwordHash).isNotEqualTo("mySecret8")
        assertThat(passwordEncoder.matches("mySecret8", savedSlot.captured.passwordHash)).isTrue()
    }

    @Test
    fun `register sets provider to LOCAL`() {
        val savedSlot = slot<User>()
        every { userRepository.existsByEmail(any()) } returns false
        every { userRepository.save(capture(savedSlot)) } answers { savedSlot.captured }

        controller.register(RegisterRequest("user@example.com", "mySecret8"))

        assertThat(savedSlot.captured.provider).isEqualTo(Provider.LOCAL)
        assertThat(savedSlot.captured.role).isEqualTo(UserRole.USER)
    }

    // ── /login ─────────────────────────────────────────────────────────────────

    @Test
    fun `login returns JWT for valid credentials`() {
        val hash = passwordEncoder.encode("correctPwd")
        val user = User(id = 1L, email = "user@example.com", passwordHash = hash, role = UserRole.USER)
        every { userRepository.findByEmail("user@example.com") } returns user

        val response = controller.login(LoginRequest("user@example.com", "correctPwd"), MockHttpServletRequest())

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body?.token).isNotBlank()
        val claims = jwtTokenProvider.getClaims(response.body!!.token)
        assertThat(claims.email).isEqualTo("user@example.com")
        assertThat(claims.role).isEqualTo(UserRole.USER)
    }

    @Test
    fun `login returns 401 for wrong password`() {
        val hash = passwordEncoder.encode("correctPwd")
        val user = User(id = 1L, email = "user@example.com", passwordHash = hash, role = UserRole.USER)
        every { userRepository.findByEmail("user@example.com") } returns user

        val response = controller.login(LoginRequest("user@example.com", "wrongPwd"), MockHttpServletRequest())

        assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
    }

    @Test
    fun `login returns 429 with Retry-After header when IP is rate-limited`() {
        val hash = passwordEncoder.encode("correctPwd")
        val user = User(id = 1L, email = "user@example.com", passwordHash = hash, role = UserRole.USER)
        every { userRepository.findByEmail("user@example.com") } returns user

        val request = MockHttpServletRequest()
        repeat(LoginRateLimiter.MAX_FAILURES) {
            controller.login(LoginRequest("user@example.com", "wrongPwd"), request)
        }

        val response = controller.login(LoginRequest("user@example.com", "correctPwd"), request)

        assertThat(response.statusCode).isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
        assertThat(response.headers[HttpHeaders.RETRY_AFTER]).isNotNull
        assertThat(response.headers.getFirst(HttpHeaders.RETRY_AFTER)!!.toLong()).isPositive()
    }

    @Test
    fun `login returns 401 for unknown email`() {
        every { userRepository.findByEmail("nobody@example.com") } returns null

        val response = controller.login(LoginRequest("nobody@example.com", "any"), MockHttpServletRequest())

        assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
    }

    @Test
    fun `login JWT contains ADMIN role for admin user`() {
        val hash = passwordEncoder.encode("adminPwd")
        val admin = User(id = 1L, email = "admin@daybrew.local", passwordHash = hash, role = UserRole.ADMIN)
        every { userRepository.findByEmail("admin@daybrew.local") } returns admin

        val response = controller.login(LoginRequest("admin@daybrew.local", "adminPwd"), MockHttpServletRequest())

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(jwtTokenProvider.getClaims(response.body!!.token).role).isEqualTo(UserRole.ADMIN)
    }

    // ── /logout ────────────────────────────────────────────────────────────────

    @Test
    fun `logout returns 204 and sets expired access_token cookie`() {
        val mockResponse = MockHttpServletResponse()

        val response = controller.logout(mockResponse)

        assertThat(response.statusCode).isEqualTo(HttpStatus.NO_CONTENT)
        val setCookieHeader = mockResponse.getHeader(HttpHeaders.SET_COOKIE)
        assertThat(setCookieHeader).contains("access_token=")
        assertThat(setCookieHeader).contains("Max-Age=0")
        assertThat(setCookieHeader).contains("HttpOnly")
        assertThat(setCookieHeader).contains("SameSite=Lax")
    }

    // ── /me ────────────────────────────────────────────────────────────────────

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `me returns 200 with email and role when authenticated`() {
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
            "user@example.com", null, listOf(SimpleGrantedAuthority("ROLE_USER"))
        )

        val response = controller.me()

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body?.email).isEqualTo("user@example.com")
        assertThat(response.body?.role).isEqualTo("USER")
    }

    @Test
    fun `me returns ADMIN role when user is admin`() {
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
            "admin@daybrew.local", null, listOf(SimpleGrantedAuthority("ROLE_ADMIN"))
        )

        val response = controller.me()

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body?.role).isEqualTo("ADMIN")
    }

    @Test
    fun `me returns 401 when not authenticated`() {
        SecurityContextHolder.clearContext()

        val response = controller.me()

        assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
    }
}
