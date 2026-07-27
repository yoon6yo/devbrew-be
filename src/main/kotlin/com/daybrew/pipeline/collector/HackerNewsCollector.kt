package com.daybrew.pipeline.collector

import com.daybrew.idea.SourceTrack
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
class HackerNewsCollector(
    private val webClient: WebClient,
) : IdeaCollector {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun collect(): List<RawSignal> = runCatching {
        @Suppress("UNCHECKED_CAST")
        val result = webClient.get()
            .uri("https://hn.algolia.com/api/v1/search?tags=show_hn&hitsPerPage=30&numericFilters=points%3E50")
            .retrieve()
            .bodyToMono(Map::class.java)
            .block() as? Map<String, Any> ?: return emptyList()

        val hits = result["hits"] as? List<Map<String, Any>> ?: return emptyList()

        hits.mapNotNull { hit ->
            val title = hit["title"] as? String ?: return@mapNotNull null
            val text = (hit["story_text"] as? String ?: "").trim()
            val url = hit["url"] as? String ?: hit["objectID"]?.let { "https://news.ycombinator.com/item?id=$it" }
            val points = (hit["points"] as? Number)?.toInt() ?: 0

            if (text.length < 80 && (hit["url"] as? String).isNullOrBlank()) return@mapNotNull null

            RawSignal(
                title = title,
                body = if (text.isNotBlank()) text.take(800) else "Show HN project: $title",
                url = url,
                track = SourceTrack.HACKERNEWS,
                engagementScore = points,
            )
        }
    }.onFailure { log.warn("HackerNews collection failed: ${it.message}") }
        .getOrDefault(emptyList())
}
