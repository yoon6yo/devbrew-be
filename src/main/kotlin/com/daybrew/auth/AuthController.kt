package com.daybrew.auth

import com.daybrew.config.DayBrewProperties
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "Admin authentication")
class AuthController(
    private val jwtTokenProvider: JwtTokenProvider,
    private val props: DayBrewProperties,
    private val rateLimiter: LoginRateLimiter,
) {
    @PostMapping("/login")
    @Operation(summary = "Admin login — returns a Bearer JWT")
    fun login(@RequestBody req: LoginRequest, httpRequest: HttpServletRequest): ResponseEntity<LoginResponse> {
        val ip = httpRequest.getHeader("X-Forwarded-For")?.split(",")?.first()?.trim()
            ?: httpRequest.remoteAddr

        if (rateLimiter.isBlocked(ip)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build()
        }

        if (req.username != props.admin.username || req.password != props.admin.password) {
            rateLimiter.recordFailure(ip)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }

        rateLimiter.recordSuccess(ip)
        return ResponseEntity.ok(LoginResponse(jwtTokenProvider.generate(req.username)))
    }
}

data class LoginRequest(val username: String = "", val password: String = "")
data class LoginResponse(val token: String)
