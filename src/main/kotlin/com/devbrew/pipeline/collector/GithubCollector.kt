package com.devbrew.pipeline.collector

import com.devbrew.idea.SourceTrack
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

@Component
class GithubCollector(
    @Value("\${devbrew.pipeline.github.pat:}") private val pat: String,
    @Value("\${devbrew.pipeline.github.min-stars}") private val minStars: Int,
    @Value("\${devbrew.pipeline.github.max-stars}") private val maxStars: Int,
) : IdeaCollector {

    private val log = LoggerFactory.getLogger(javaClass)

    private val client = WebClient.builder()
        .baseUrl("https://api.github.com")
        .defaultHeader("Accept", "application/vnd.github+json")
        .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
        .apply { if (pat.isNotBlank()) it.defaultHeader("Authorization", "Bearer $pat") }
        .build()

    override fun collect(): List<RawSignal> {
        if (pat.isBlank()) {
            log.warn("GitHub PAT not configured, skipping GitHub collection")
            return emptyList()
        }

        return try {
            val response = client.get()
                .uri("/search/repositories?q=stars:$minStars..$maxStars+archived:false&sort=updated&order=desc&per_page=30")
                .retrieve()
                .bodyToMono<Map<String, Any>>()
                .block() ?: return emptyList()

            @Suppress("UNCHECKED_CAST")
            val items = response["items"] as? List<*> ?: return emptyList()

            items.mapNotNull { item ->
                @Suppress("UNCHECKED_CAST")
                val repo = item as? Map<String, Any> ?: return@mapNotNull null

                val name = repo["full_name"] as? String ?: return@mapNotNull null
                val description = repo["description"] as? String ?: return@mapNotNull null
                val url = repo["html_url"] as? String
                val stars = (repo["stargazers_count"] as? Int) ?: 0
                val topics = @Suppress("UNCHECKED_CAST") (repo["topics"] as? List<String>)?.joinToString(", ") ?: ""

                if (description.isBlank()) return@mapNotNull null

                RawSignal(
                    title = name,
                    body = "Description: $description | Stars: $stars | Topics: $topics",
                    url = url,
                    track = SourceTrack.GITHUB,
                )
            }
        } catch (e: Exception) {
            log.warn("GitHub collection failed: ${e.message}")
            emptyList()
        }
    }
}
