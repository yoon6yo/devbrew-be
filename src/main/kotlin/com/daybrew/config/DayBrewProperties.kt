package com.daybrew.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "daybrew")
data class DayBrewProperties(
    val slack: SlackProps = SlackProps(),
    val gemini: GeminiProps = GeminiProps(),
    val jwt: JwtProps = JwtProps(),
    val admin: AdminProps = AdminProps(),
    val frontend: FrontendProps = FrontendProps(),
) {
    data class SlackProps(val webhookUrl: String = "")

    data class GeminiProps(
        val apiKey: String = "",
        val baseUrl: String = "https://generativelanguage.googleapis.com",
        val model: String = "gemini-2.5-flash-lite",
    )

    data class JwtProps(
        val secret: String = "daybrew-secret-key-change-in-production-32ch",
        val expirationMs: Long = 86_400_000,
        val issuer: String = "daybrew",
        val audience: String = "daybrew-users",
    )

    data class AdminProps(
        val username: String = "admin",
        val password: String = "",
        val email: String = "admin@daybrew.local",
    )

    data class FrontendProps(
        val url: String = "http://localhost:5173",
    )
}
