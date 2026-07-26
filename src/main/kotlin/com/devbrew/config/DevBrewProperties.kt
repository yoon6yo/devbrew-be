package com.devbrew.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "devbrew")
data class DevBrewProperties(
    val slack: SlackProps = SlackProps(),
    val claude: ClaudeProps = ClaudeProps(),
    val gemini: GeminiProps = GeminiProps(),
    val jwt: JwtProps = JwtProps(),
    val admin: AdminProps = AdminProps(),
) {
    data class SlackProps(val webhookUrl: String = "")

    data class ClaudeProps(
        val apiKey: String = "",
        val baseUrl: String = "https://api.anthropic.com",
        val batchPollIntervalMs: Long = 30_000,
        val batchMaxPolls: Int = 20,
    )

    data class GeminiProps(
        val apiKey: String = "",
        val baseUrl: String = "https://generativelanguage.googleapis.com",
        val model: String = "gemini-2.5-flash-lite",
        val dailyBudgetKrw: Int = 1000,
        val usdToKrw: Int = 1400,
    )

    data class JwtProps(
        val secret: String = "devbrew-secret-key-change-in-production-32ch",
        val expirationMs: Long = 86_400_000,
    )

    data class AdminProps(
        val username: String = "admin",
        val password: String = "",
    )
}
