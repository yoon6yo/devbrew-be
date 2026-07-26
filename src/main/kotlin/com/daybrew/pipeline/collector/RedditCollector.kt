package com.daybrew.pipeline.collector

import com.daybrew.idea.SourceTrack
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

@Component
class RedditCollector(
    @Value("\${daybrew.pipeline.reddit.subreddits}") private val subredditsRaw: String,
    @Value("\${daybrew.pipeline.reddit.user-agent}") private val userAgent: String,
) : IdeaCollector {

    private val log = LoggerFactory.getLogger(javaClass)
    private val subreddits = subredditsRaw.split(",").map { it.trim() }

    private val client = WebClient.builder()
        .baseUrl("https://www.reddit.com")
        .defaultHeader("User-Agent", userAgent)
        .build()

    override fun collect(): List<RawSignal> {
        return subreddits.flatMap { collectSubreddit(it) }
    }

    private fun collectSubreddit(subreddit: String): List<RawSignal> {
        return try {
            val response = client.get()
                .uri("/r/$subreddit/hot.json?limit=25")
                .retrieve()
                .bodyToMono<Map<String, Any>>()
                .block() ?: return emptyList()

            @Suppress("UNCHECKED_CAST")
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
                    body = selftext.take(2000),
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
