package com.daybrew.llm

import com.daybrew.admin.AdminStatsService
import com.daybrew.idea.Idea
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class GeminiIdeaRater(
    private val objectMapper: ObjectMapper,
    private val adminStatsService: AdminStatsService,
    private val batchClient: GeminiBatchClient,
) : IdeaRater {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun rateAll(ideas: List<Idea>): Map<Long, ScoreResult> {
        if (ideas.isEmpty()) return emptyMap()

        log.info("Rating ${ideas.size} ideas via Gemini Batch API")

        val requests = ideas.map { idea ->
            BatchRequest(
                key = "idea-${idea.id}",
                body = mapOf(
                    "contents" to listOf(mapOf("parts" to listOf(mapOf("text" to ratingPrompt(idea))))),
                    "generationConfig" to mapOf("responseMimeType" to "application/json", "maxOutputTokens" to 300),
                )
            )
        }

        val responses = batchClient.submitAndAwait(requests, "daybrew-rate")
        var totalPrompt = 0; var totalCompletion = 0

        val result = ideas.mapNotNull { idea ->
            val resp = responses["idea-${idea.id}"] ?: return@mapNotNull null
            (resp["usageMetadata"] as? Map<*, *>)?.let { u ->
                totalPrompt += (u["promptTokenCount"] as? Number)?.toInt() ?: 0
                totalCompletion += (u["candidatesTokenCount"] as? Number)?.toInt() ?: 0
            }
            runCatching { parseRating(idea, resp) }
                .onFailure { log.warn("Rating parse failed for idea ${idea.id}", it) }
                .getOrNull()
        }.toMap()

        runCatching { adminStatsService.recordGeminiUsage("rate-batch", totalPrompt, totalCompletion) }
        return result
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseRating(idea: Idea, response: Map<String, Any>): Pair<Long, ScoreResult> {
        val candidates = response["candidates"] as List<Map<*, *>>
        val parts = (candidates[0]["content"] as Map<*, *>)["parts"] as List<Map<*, *>>
        val text = parts[0]["text"] as String
        val parsed = objectMapper.readValue(text, Map::class.java)

        fun sc(key: String) = (parsed[key] as Number).toInt().coerceIn(1, 10).toShort()
        val mf = sc("market_fit"); val nv = sc("novelty")
        val fe = sc("feasibility"); val mn = sc("monetization"); val tr = sc("trend")
        val weighted = ((mf * 30 + nv * 20 + fe * 20 + mn * 20 + tr * 10) / 100).coerceIn(1, 10).toShort()

        return idea.id to ScoreResult(weighted, mf, nv, fe, mn, tr, parsed["reason"] as String)
    }

    private fun ratingPrompt(idea: Idea): String =
        """You are a startup idea evaluator. Rate the following idea on five axes, each 1–10.

Title: ${idea.title}
Description: ${idea.description}
Source: ${idea.sourceTrack}

Axes (score each 1–10):
- market_fit: Is there a real, painful problem? Is there a clearly defined audience willing to pay?
- novelty: How differentiated is this from existing solutions?
- feasibility: Can a small team (1-3 devs) realistically build and ship an MVP within 3 months?
- monetization: Is there a clear, near-term revenue path (SaaS, marketplace fee, API pricing, etc.)?
- trend: Does this ride a current wave (AI, no-code, developer tooling, etc.)?

Scoring calibration — apply strictly:
- 1–3: Weak. Major structural problem (no clear customer, copycat with no edge, impossible to build, no revenue path).
- 4–6: Average. Viable but undifferentiated or with significant risks. Most ideas fall here.
- 7: Genuinely strong. Clear pain, real differentiation, plausible path to revenue. Award sparingly — fewer than 20% of ideas.
- 8: Exceptional. Evident competitive moat or breakout potential. Fewer than 5% of ideas.
- 9–10: Extremely rare. Reserve only for once-in-a-generation concepts with near-certain demand.

Respond with JSON only:
{"market_fit":<1-10>,"novelty":<1-10>,"feasibility":<1-10>,"monetization":<1-10>,"trend":<1-10>,"reason":"<under 120 words summarising strengths and key risk>"}"""
}
