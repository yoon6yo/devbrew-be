package com.daybrew.auth

import com.daybrew.config.DayBrewProperties
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.Date
import javax.crypto.SecretKey

data class JwtClaims(val userId: Long, val email: String, val role: UserRole)

@Component
class JwtTokenProvider(private val props: DayBrewProperties) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val key: SecretKey by lazy {
        val secret = props.jwt.secret
        require(secret.length >= 32 && secret != "daybrew-secret-key-change-in-production-32ch") {
            "JWT_SECRET must be a strong non-default value (>=32 chars). Set it via environment variable."
        }
        Keys.hmacShaKeyFor(secret.toByteArray(Charsets.UTF_8))
    }

    @PostConstruct
    fun warnIfDefaultSecret() {
        val secret = props.jwt.secret
        if (secret == "daybrew-secret-key-change-in-production-32ch") {
            log.warn("=================================================================")
            log.warn("JWT_SECRET is set to the default sentinel value.")
            log.warn("OAuth2 login WILL fail until you set a strong JWT_SECRET.")
            log.warn("Update the 'jwt-secret' key in your daybrew-secrets k8s secret.")
            log.warn("=================================================================")
        }
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
        val payload = Jwts.parser()
            .verifyWith(key)
            .requireIssuer(props.jwt.issuer)
            .build()
            .parseSignedClaims(token)
            .payload
        if (!payload.audience.contains(props.jwt.audience))
            throw io.jsonwebtoken.JwtException("Invalid audience")
        return JwtClaims(
            userId = (payload["userId"] as? Number)?.toLong()
                ?: throw io.jsonwebtoken.JwtException("Missing or invalid userId claim"),
            email = payload.subject
                ?: throw io.jsonwebtoken.JwtException("Missing subject"),
            role = runCatching { UserRole.valueOf(payload["role"] as? String ?: "") }
                .getOrElse { throw io.jsonwebtoken.JwtException("Invalid role claim") },
        )
    }
}
