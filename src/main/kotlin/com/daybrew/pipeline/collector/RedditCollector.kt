package com.daybrew.pipeline.collector

import com.daybrew.config.DayBrewProperties
import com.daybrew.idea.SourceTrack
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import java.util.Base64

@Component
class RedditCollector(
    @Value("\${daybrew.pipeline.reddit.subreddits}") private val subredditsRaw: String,
    @Value("\${daybrew.pipeline.reddit.user-agent}") private val userAgent: String,
    private val props: DayBrewProperties,
) : IdeaCollector {

    private val log = LoggerFactory.getLogger(javaClass)
    private val subreddits = subredditsRaw.split(",").map { it.trim() }

    private val authClient = WebClient.builder()
        .baseUrl("https://www.reddit.com")
        .defaultHeader("User-Agent", userAgent)
        .build()

    private val apiClient = WebClient.builder()
        .baseUrl("https://oauth.reddit.com")
        .defaultHeader("User-Agent", userAgent)
        .build()

    override fun collect(): List<RawSignal> {
        val clientId = props.pipeline.reddit.clientId
        val clientSecret = props.pipeline.reddit.clientSecret
        if (clientId.isBlank() || clientSecret.isBlank()) {
            log.warn("REDDIT_CLIENT_ID/SECRET not configured — skipping Reddit collection")
            return emptyList()
        }
        val token = fetchAccessToken(clientId, clientSecret) ?: return emptyList()
        return subreddits.flatMap { collectSubreddit(it, token) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun fetchAccessToken(clientId: String, clientSecret: String): String? {
        return try {
            val credentials = Base64.getEncoder().encodeToString("$clientId:$clientSecret".toByteArray())
            val body = LinkedMultiValueMap<String, String>().apply {
                add("grant_type", "client_credentials")
            }
            val response = authClient.post()
                .uri("/api/v1/access_token")
                .header("Authorization", "Basic $credentials")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(body))
                .retrieve()
                .bodyToMono<Map<String, Any>>()
                .block()
            (response?.get("access_token") as? String).also {
                if (it == null) log.warn("Reddit token response missing access_token: $response")
            }
        } catch (e: Exception) {
            log.warn("Reddit OAuth2 token fetch failed: ${e.message}")
            null
        }
    }

    private fun collectSubreddit(subreddit: String, token: String): List<RawSignal> {
        return try {
            @Suppress("UNCHECKED_CAST")
            val response = apiClient.get()
                .uri("/r/$subreddit/hot?limit=15")
                .header("Authorization", "Bearer $token")
                .retrieve()
                .bodyToMono<Map<String, Any>>()
                .block() ?: return emptyList()

            val children = ((response["data"] as? Map<*, *>)?.get("children") as? List<*>)
                ?: return emptyList()

            children.mapNotNull { child ->
                @Suppress("UNCHECKED_CAST")
                val data = (child as? Map<*, *>)?.get("data") as? Map<String, Any>
                    ?: return@mapNotNull null

                val title = data["title"] as? String ?: return@mapNotNull null
                val selftext = data["selftext"] as? String ?: ""
                val permalink = data["permalink"] as? String

                if (selftext.length < 100) return@mapNotNull null

                RawSignal(
                    title = title,
                    body = selftext.take(800),
                    url = permalink?.let { "https://www.reddit.com$it" },
                    track = SourceTrack.SAAS,
                )
            }
        } catch (e: Exception) {
            log.warn("Reddit collection failed for r/$subreddit: ${e.message}")
            emptyList()
        }
    }
}
