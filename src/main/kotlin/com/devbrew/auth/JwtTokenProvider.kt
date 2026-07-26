package com.devbrew.auth

import com.devbrew.config.DevBrewProperties
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Component
import java.util.Date
import javax.crypto.SecretKey

@Component
class JwtTokenProvider(private val props: DevBrewProperties) {

    private val key: SecretKey by lazy {
        Keys.hmacShaKeyFor(props.jwt.secret.toByteArray(Charsets.UTF_8))
    }

    fun generate(subject: String): String = Jwts.builder()
        .subject(subject)
        .issuedAt(Date())
        .expiration(Date(System.currentTimeMillis() + props.jwt.expirationMs))
        .signWith(key)
        .compact()

    fun validate(token: String): Boolean = runCatching {
        Jwts.parser().verifyWith(key).build().parseSignedClaims(token)
        true
    }.getOrDefault(false)

    fun getSubject(token: String): String =
        Jwts.parser().verifyWith(key).build().parseSignedClaims(token).payload.subject
}
