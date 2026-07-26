package com.devbrew.auth

import com.devbrew.config.DevBrewProperties
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
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
    private val props: DevBrewProperties,
) {
    @PostMapping("/login")
    @Operation(summary = "Admin login — returns a Bearer JWT")
    fun login(@RequestBody req: LoginRequest): ResponseEntity<LoginResponse> {
        if (req.username != props.admin.username || req.password != props.admin.password) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }
        return ResponseEntity.ok(LoginResponse(jwtTokenProvider.generate(req.username)))
    }
}

data class LoginRequest(val username: String = "", val password: String = "")
data class LoginResponse(val token: String)
