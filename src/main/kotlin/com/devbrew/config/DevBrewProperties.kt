package com.devbrew.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "devbrew")
data class DevBrewProperties(
    val slack: SlackProps = SlackProps(),
    val claude: ClaudeProps = ClaudeProps(),
    val gemini: GeminiProps = GeminiProps(),
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
    )
}
