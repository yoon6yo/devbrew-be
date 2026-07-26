package com.devbrew.slack

import com.devbrew.config.DevBrewProperties
import com.devbrew.idea.Idea
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
class SlackNotifier(
    private val webClient: WebClient,
    private val props: DevBrewProperties,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun notifyIdea(idea: Idea) {
        val url = props.slack.webhookUrl
        if (url.isBlank()) {
            log.info("Slack webhook not configured — skipping notification for idea ${idea.id}")
            return
        }

        webClient.post()
            .uri(url)
            .bodyValue(buildPayload(idea))
            .retrieve()
            .bodyToMono(String::class.java)
            .block()

        log.info("Slack notification sent for idea ${idea.id}: ${idea.title}")
    }

    private fun buildPayload(idea: Idea): Map<String, Any> {
        val trackLabel = when (idea.sourceTrack.name) {
            "SAAS" -> ":bar_chart: SaaS"
            "GITHUB" -> ":octocat: GitHub"
            "VIRAL" -> ":fire: Viral"
            else -> ":bulb: ${idea.sourceTrack.name}"
        }

        val blocks = mutableListOf<Map<String, Any>>(
            mapOf("type" to "header", "text" to mapOf("type" to "plain_text", "text" to "New Idea — Score ${idea.score}/10")),
            mapOf(
                "type" to "section",
                "fields" to listOf(
                    mapOf("type" to "mrkdwn", "text" to "*Title:*\n${idea.title}"),
                    mapOf("type" to "mrkdwn", "text" to "*Track:*\n$trackLabel"),
                )
            ),
            mapOf("type" to "section", "text" to mapOf("type" to "mrkdwn", "text" to "*Description:*\n${idea.description}")),
            mapOf("type" to "section", "text" to mapOf("type" to "mrkdwn", "text" to "*Why:*\n${idea.scoreReason ?: "N/A"}")),
        )

        if (!idea.sourceUrl.isNullOrBlank()) {
            blocks += mapOf(
                "type" to "actions",
                "elements" to listOf(
                    mapOf("type" to "button", "text" to mapOf("type" to "plain_text", "text" to "View Source"), "url" to idea.sourceUrl)
                )
            )
        }

        return mapOf("blocks" to blocks)
    }
}
