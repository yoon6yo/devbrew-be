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
    @Value("\${daybrew.pipeline.reddit.viral-subreddits:}") private val viralSubredditsRaw: String,
    @Value("\${daybrew.pipeline.reddit.user-agent}") private val userAgent: String,
) : IdeaCollector {

    private val log = LoggerFactory.getLogger(javaClass)
    private val subreddits = subredditsRaw.split(",").map { it.trim() }.filter { it.isNotBlank() }
    private val viralSubreddits = viralSubredditsRaw.split(",").map { it.trim() }.filter { it.isNotBlank() }

    private val client = WebClient.builder()
        .baseUrl("https://www.reddit.com")
        .defaultHeader("User-Agent", userAgent)
        .build()

    override fun collect(): List<RawSignal> {
        val results = mutableListOf<RawSignal>()
        subreddits.forEachIndexed { i, sub ->
            if (i > 0) Thread.sleep(1_000)
            results += collectSubreddit(sub)
            Thread.sleep(1_000)
            results += collectSubredditNew(sub)
        }
        viralSubreddits.forEach { sub ->
            Thread.sleep(1_000)
            results += collectViralSubreddit(sub)
        }
        if (results.isNotEmpty()) Thread.sleep(1_000)
        results += collectPainPoints()
        return results
    }

    private val painPointQueries = listOf(
        "\"I wish there was an app\"",
        "\"why isn't there a tool\"",
        "\"someone should build\"",
        "\"I can't believe there's no\"",
        "\"why doesn't exist\"",
        "\"desperately need a way to\"",
    )

    private fun collectPainPoints(): List<RawSignal> {
        return painPointQueries.flatMapIndexed { i, query ->
            if (i > 0) Thread.sleep(1_000)
            try {
                val encoded = java.net.URLEncoder.encode(query, Charsets.UTF_8)
                @Suppress("UNCHECKED_CAST")
                val response = client.get()
                    .uri("/search.json?q=$encoded&sort=relevance&t=week&limit=10&type=link")
                    .retrieve()
                    .bodyToMono<Map<String, Any>>()
                    .block() ?: return@flatMapIndexed emptyList()

                val children = ((response["data"] as? Map<*, *>)?.get("children") as? List<*>)
                    ?: return@flatMapIndexed emptyList()

                children.mapNotNull { child ->
                    @Suppress("UNCHECKED_CAST")
                    val data = (child as? Map<*, *>)?.get("data") as? Map<String, Any>
                        ?: return@mapNotNull null

                    val title     = data["title"] as? String ?: return@mapNotNull null
                    val selftext  = data["selftext"] as? String ?: ""
                    val upvotes   = (data["score"] as? Number)?.toInt() ?: 0
                    val permalink = data["permalink"] as? String

                    if (selftext.length < 50) return@mapNotNull null
                    if (upvotes < 20) return@mapNotNull null

                    RawSignal(
                        title = title,
                        body = selftext.take(800),
                        url = permalink?.let { "https://www.reddit.com$it" },
                        track = SourceTrack.SAAS,
                        engagementScore = upvotes,
                    )
                }
            } catch (e: Exception) {
                log.warn("Reddit pain point search failed for query '$query': ${e.message}")
                emptyList()
            }
        }
    }

    private fun collectSubreddit(subreddit: String): List<RawSignal> {
        return try {
            @Suppress("UNCHECKED_CAST")
            val response = client.get()
                .uri("/r/$subreddit/hot.json?limit=15")
                .retrieve()
                .bodyToMono<Map<String, Any>>()
                .block() ?: return emptyList()

            val children = ((response["data"] as? Map<*, *>)?.get("children") as? List<*>)
                ?: return emptyList()

            children.mapNotNull { child ->
                @Suppress("UNCHECKED_CAST")
                val data = (child as? Map<*, *>)?.get("data") as? Map<String, Any>
                    ?: return@mapNotNull null

                val title     = data["title"] as? String ?: return@mapNotNull null
                val selftext  = data["selftext"] as? String ?: ""
                val permalink = data["permalink"] as? String

                if (selftext.length < 100) return@mapNotNull null

                val upvotes = (data["score"] as? Number)?.toInt() ?: 0
                if (upvotes < 50) return@mapNotNull null

                RawSignal(
                    title = title,
                    body = selftext.take(800),
                    url = permalink?.let { "https://www.reddit.com$it" },
                    track = SourceTrack.SAAS,
                    engagementScore = upvotes,
                )
            }
        } catch (e: Exception) {
            log.warn("Reddit collection failed for r/$subreddit: ${e.message}")
            emptyList()
        }
    }

    private fun collectSubredditNew(subreddit: String): List<RawSignal> {
        return try {
            @Suppress("UNCHECKED_CAST")
            val response = client.get()
                .uri("/r/$subreddit/new.json?limit=25")
                .retrieve()
                .bodyToMono<Map<String, Any>>()
                .block() ?: return emptyList()

            val children = ((response["data"] as? Map<*, *>)?.get("children") as? List<*>)
                ?: return emptyList()

            children.mapNotNull { child ->
                @Suppress("UNCHECKED_CAST")
                val data = (child as? Map<*, *>)?.get("data") as? Map<String, Any>
                    ?: return@mapNotNull null

                val title    = data["title"] as? String ?: return@mapNotNull null
                val selftext = data["selftext"] as? String ?: ""
                val permalink = data["permalink"] as? String

                if (selftext.length < 100) return@mapNotNull null

                val upvotes = (data["score"] as? Number)?.toInt() ?: 0
                if (upvotes < 10) return@mapNotNull null

                RawSignal(
                    title = title,
                    body = selftext.take(800),
                    url = permalink?.let { "https://www.reddit.com$it" },
                    track = SourceTrack.SAAS,
                    engagementScore = upvotes,
                )
            }
        } catch (e: Exception) {
            log.warn("Reddit new collection failed for r/$subreddit: ${e.message}")
            emptyList()
        }
    }

    private fun collectViralSubreddit(subreddit: String): List<RawSignal> {
        return try {
            @Suppress("UNCHECKED_CAST")
            val response = client.get()
                .uri("/r/$subreddit/top.json?t=week&limit=20")
                .retrieve()
                .bodyToMono<Map<String, Any>>()
                .block() ?: return emptyList()

            val children = ((response["data"] as? Map<*, *>)?.get("children") as? List<*>)
                ?: return emptyList()

            children.mapNotNull { child ->
                @Suppress("UNCHECKED_CAST")
                val data = (child as? Map<*, *>)?.get("data") as? Map<String, Any>
                    ?: return@mapNotNull null

                val title     = data["title"] as? String ?: return@mapNotNull null
                if (title.length < 25) return@mapNotNull null

                val upvotes   = (data["score"] as? Number)?.toInt() ?: 0
                if (upvotes < 100) return@mapNotNull null

                val selftext  = data["selftext"] as? String ?: ""
                val permalink = data["permalink"] as? String

                val body = buildString {
                    append(title)
                    if (selftext.length > 30) { append("\n\n"); append(selftext.take(700)) }
                }

                RawSignal(
                    title = title,
                    body = body,
                    url = permalink?.let { "https://www.reddit.com$it" },
                    track = SourceTrack.VIRAL,
                    engagementScore = upvotes,
                )
            }
        } catch (e: Exception) {
            log.warn("Reddit viral collection failed for r/$subreddit: ${e.message}")
            emptyList()
        }
    }
}
