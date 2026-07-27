package com.daybrew.auth

import com.daybrew.config.resolveClientIp
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "User authentication")
class AuthController(
    private val userRepository: UserRepository,
    private val jwtTokenProvider: JwtTokenProvider,
    private val passwordEncoder: PasswordEncoder,
    private val rateLimiter: LoginRateLimiter,
) {

    @PostMapping("/register")
    @Operation(summary = "Register with email and password")
    fun register(@RequestBody @Valid req: RegisterRequest): ResponseEntity<LoginResponse> {
        if (userRepository.existsByEmail(req.email)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build()
        }
        val user = userRepository.save(
            User(
                email = req.email,
                passwordHash = passwordEncoder.encode(req.password),
                provider = Provider.LOCAL,
            )
        )
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(LoginResponse(jwtTokenProvider.generate(user.id, user.email, user.role)))
    }

    @PostMapping("/login")
    @Operation(summary = "Login with email and password — returns a Bearer JWT")
    fun login(@RequestBody req: LoginRequest, httpRequest: HttpServletRequest): ResponseEntity<LoginResponse> {
        val ip = resolveClientIp(httpRequest)

        val emailKey = "email:${req.email}"
        if (rateLimiter.isBlocked(ip) || rateLimiter.isBlocked(emailKey)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build()
        }

        val user = userRepository.findByEmail(req.email)
        if (user == null || user.passwordHash == null || !passwordEncoder.matches(req.password, user.passwordHash)) {
            rateLimiter.recordFailure(ip)
            rateLimiter.recordFailure(emailKey)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }

        rateLimiter.recordSuccess(ip)
        rateLimiter.recordSuccess(emailKey)
        return ResponseEntity.ok(LoginResponse(jwtTokenProvider.generate(user.id, user.email, user.role)))
    }

    @GetMapping("/me")
    fun me(): ResponseEntity<MeResponse> {
        val auth = SecurityContextHolder.getContext().authentication
        if (auth == null || !auth.isAuthenticated || auth.principal == "anonymousUser") {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }
        val email = auth.name
        val role = auth.authorities.firstOrNull()?.authority?.removePrefix("ROLE_") ?: "USER"
        return ResponseEntity.ok(MeResponse(email = email, role = role))
    }

    @PostMapping("/logout")
    @Operation(summary = "Clear the access_token cookie to log out")
    fun logout(response: HttpServletResponse): ResponseEntity<Void> {
        val cookie = ResponseCookie.from("access_token", "")
            .httpOnly(true)
            .secure(true)
            .path("/")
            .maxAge(0)
            .sameSite("Lax")
            .build()
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString())
        return ResponseEntity.noContent().build()
    }
}

data class RegisterRequest(
    @field:Email val email: String = "",
    @field:Size(min = 8) val password: String = "",
)

data class LoginRequest(val email: String = "", val password: String = "")
data class LoginResponse(val token: String)
data class MeResponse(val email: String, val role: String)
