package com.daybrew.auth

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
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
        val ip = httpRequest.getHeader("X-Forwarded-For")?.split(",")?.first()?.trim()
            ?: httpRequest.remoteAddr

        if (rateLimiter.isBlocked(ip)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build()
        }

        val user = userRepository.findByEmail(req.email)
        if (user == null || user.passwordHash == null || !passwordEncoder.matches(req.password, user.passwordHash)) {
            rateLimiter.recordFailure(ip)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }

        rateLimiter.recordSuccess(ip)
        return ResponseEntity.ok(LoginResponse(jwtTokenProvider.generate(user.id, user.email, user.role)))
    }
}

data class RegisterRequest(
    @field:Email val email: String = "",
    @field:Size(min = 8) val password: String = "",
)

data class LoginRequest(val email: String = "", val password: String = "")
data class LoginResponse(val token: String)
