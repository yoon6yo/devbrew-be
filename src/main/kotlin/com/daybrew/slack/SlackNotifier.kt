package com.daybrew.slack

import com.daybrew.config.DayBrewProperties
import com.daybrew.idea.Idea
import com.daybrew.idea.SourceTrack
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.time.Duration

@Component
open class SlackNotifier(
    private val webClient: WebClient,
    private val props: DayBrewProperties,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private val WEBHOOK_PATTERN =
            Regex("""^https://hooks\.slack\.com/services/[A-Za-z0-9]+/[A-Za-z0-9]+/[A-Za-z0-9]+$""")
    }

    protected open fun isValidWebhookUrl(url: String): Boolean = WEBHOOK_PATTERN.matches(url)

    fun notifyIdea(idea: Idea) {
        val url = props.slack.webhookUrl
        if (url.isBlank()) {
            log.info("Slack webhook not configured — skipping notification for idea ${idea.id}")
            return
        }
        if (!isValidWebhookUrl(url)) {
            log.warn("Slack webhook URL failed validation — refusing to POST for idea ${idea.id}")
            return
        }

        webClient.post()
            .uri(url)
            .bodyValue(buildPayload(idea))
            .retrieve()
            .bodyToMono(String::class.java)
            .timeout(Duration.ofSeconds(5))
            .onErrorResume { ex ->
                log.warn("Slack notification failed for idea ${idea.id}: ${ex.message}")
                Mono.empty()
            }
            .block()

        log.info("Slack notification sent for idea ${idea.id}: ${idea.title}")
    }

    private fun buildPayload(idea: Idea): Map<String, Any> {
        val trackLabel = when (idea.sourceTrack) {
            SourceTrack.SAAS -> ":bar_chart: SaaS"
            SourceTrack.GITHUB -> ":octocat: GitHub"
            SourceTrack.VIRAL -> ":fire: Viral"
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
