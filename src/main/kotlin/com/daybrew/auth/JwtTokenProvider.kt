package com.daybrew.auth

import com.daybrew.config.DayBrewProperties
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Component
import java.util.Date
import javax.crypto.SecretKey

data class JwtClaims(val userId: Long, val email: String, val role: UserRole)

@Component
class JwtTokenProvider(private val props: DayBrewProperties) {

    private val key: SecretKey by lazy {
        Keys.hmacShaKeyFor(props.jwt.secret.toByteArray(Charsets.UTF_8))
    }

    fun generate(userId: Long, email: String, role: UserRole): String = Jwts.builder()
        .subject(email)
        .claim("userId", userId)
        .claim("role", role.name)
        .issuedAt(Date())
        .expiration(Date(System.currentTimeMillis() + props.jwt.expirationMs))
        .signWith(key)
        .compact()

    fun validate(token: String): Boolean = runCatching {
        Jwts.parser().verifyWith(key).build().parseSignedClaims(token)
        true
    }.getOrDefault(false)

    fun getClaims(token: String): JwtClaims {
        val payload = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).payload
        return JwtClaims(
            userId = (payload["userId"] as Number).toLong(),
            email = payload.subject,
            role = UserRole.valueOf(payload["role"] as String),
        )
    }
}
