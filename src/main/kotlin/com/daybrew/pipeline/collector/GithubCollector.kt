package com.daybrew.pipeline.collector

import com.daybrew.idea.SourceTrack
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import java.time.LocalDate

@Component
class GithubCollector(
    @Value("\${daybrew.pipeline.github.pat:}") private val pat: String,
    @Value("\${daybrew.pipeline.github.abandoned-min-stars:500}") private val minStars: Int,
    @Value("\${daybrew.pipeline.github.abandoned-years:3}") private val abandonedYears: Long,
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
        return collectAbandonedRepos()
    }

    @Suppress("UNCHECKED_CAST")
    private fun collectAbandonedRepos(): List<RawSignal> {
        val today = LocalDate.now()
        val cutoffDate = today.minusYears(abandonedYears).toString()
        val deepCutoffDate = today.minusYears(abandonedYears + 2).toString()
        val mediumStarMax = (minStars - 1).coerceAtLeast(100)

        // Three query variants to get diverse repos on each run instead of always the same top list
        val variants = listOf(
            Triple("stars:>$minStars+pushed:<$cutoffDate+archived:false+fork:false", "stars", "desc"),
            Triple("stars:50..$mediumStarMax+pushed:<$cutoffDate+archived:false+fork:false", "stars", "desc"),
            Triple("stars:>$minStars+pushed:<$deepCutoffDate+archived:false+fork:false", "updated", "desc"),
        )

        return variants.flatMap { (query, sort, order) ->
            fetchRepos(query, sort, order)
        }.distinctBy { it.url }
    }

    @Suppress("UNCHECKED_CAST")
    private fun fetchRepos(query: String, sort: String, order: String): List<RawSignal> {
        return try {
            val response = client.get()
                .uri("/search/repositories?q=$query&sort=$sort&order=$order&per_page=20")
                .retrieve()
                .bodyToMono<Map<String, Any>>()
                .block() ?: return emptyList()

            val items = response["items"] as? List<*> ?: return emptyList()
            log.info("GitHub: fetched ${items.size} repos (sort=$sort, query prefix=${query.take(40)}…)")

            items.mapNotNull { item ->
                val repo = item as? Map<String, Any> ?: return@mapNotNull null
                val name        = repo["full_name"] as? String ?: return@mapNotNull null
                val description = repo["description"] as? String ?: return@mapNotNull null
                val url         = repo["html_url"] as? String
                val stars       = (repo["stargazers_count"] as? Int) ?: 0
                val pushedAt    = repo["pushed_at"] as? String ?: "unknown"
                val language    = repo["language"] as? String ?: ""
                val topics      = (repo["topics"] as? List<String>)?.joinToString(", ") ?: ""

                if (description.isBlank()) return@mapNotNull null

                RawSignal(
                    title = name,
                    body = buildString {
                        append("Description: $description")
                        append(" | Stars: $stars")
                        append(" | Last commit: $pushedAt")
                        if (language.isNotBlank()) append(" | Language: $language")
                        if (topics.isNotBlank()) append(" | Topics: $topics")
                        append(" | Status: abandoned — no commits in $abandonedYears+ years")
                    },
                    url = url,
                    track = SourceTrack.GITHUB,
                    engagementScore = stars,
                )
            }
        } catch (e: Exception) {
            log.warn("GitHub repo collection failed (sort=$sort): ${e.message}")
            emptyList()
        }
    }
}
