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
        val secret = props.jwt.secret
        require(secret.length >= 32 && secret != "daybrew-secret-key-change-in-production-32ch") {
            "JWT_SECRET must be a strong non-default value (>=32 chars). Set it via environment variable."
        }
        Keys.hmacShaKeyFor(secret.toByteArray(Charsets.UTF_8))
    }

    fun generate(userId: Long, email: String, role: UserRole): String = Jwts.builder()
        .issuer(props.jwt.issuer)
        .audience().add(props.jwt.audience).and()
        .subject(email)
        .claim("userId", userId)
        .claim("role", role.name)
        .issuedAt(Date())
        .expiration(Date(System.currentTimeMillis() + props.jwt.expirationMs))
        .signWith(key)
        .compact()

    fun validate(token: String): Boolean = runCatching {
        Jwts.parser()
            .verifyWith(key)
            .requireIssuer(props.jwt.issuer)
            .build()
            .parseSignedClaims(token)
            .payload
            .audience
            .contains(props.jwt.audience)
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
