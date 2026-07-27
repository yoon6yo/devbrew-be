package com.daybrew.llm

import com.daybrew.admin.AdminStatsService
import com.daybrew.config.DayBrewProperties
import com.daybrew.idea.Idea
import com.daybrew.idea.SourceTrack
import com.daybrew.pipeline.collector.RawSignal
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
class GeminiIdeaGenerator(
    private val webClient: WebClient,
    private val props: DayBrewProperties,
    private val objectMapper: ObjectMapper,
    private val adminStatsService: AdminStatsService,
) : IdeaGenerator {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun generate(signal: RawSignal): Idea {
        val uri = "${props.gemini.baseUrl}/v1/models/${props.gemini.model}:generateContent"

        @Suppress("UNCHECKED_CAST")
        val response = webClient.post()
            .uri(uri)
            .header("x-goog-api-key", props.gemini.apiKey)
            .bodyValue(
                mapOf(
                    "system_instruction" to mapOf("parts" to listOf(mapOf("text" to systemInstruction(signal.track)))),
                    "contents" to listOf(mapOf("parts" to listOf(mapOf("text" to buildPrompt(signal))))),
                    "generationConfig" to mapOf("responseMimeType" to "application/json"),
                )
            )
            .retrieve()
            .bodyToMono(Map::class.java)
            .block() as Map<String, Any>? ?: throw RuntimeException("Empty response from Gemini")

        (response["usageMetadata"] as? Map<*, *>)?.let { usage ->
            val prompt = (usage["promptTokenCount"] as? Number)?.toInt() ?: 0
            val completion = (usage["candidatesTokenCount"] as? Number)?.toInt() ?: 0
            runCatching { adminStatsService.recordGeminiUsage("generate", prompt, completion) }
        }

        return parseResponse(response, signal)
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseResponse(response: Map<*, *>, signal: RawSignal): Idea {
        return try {
            val candidates = response["candidates"] as List<Map<*, *>>
            val parts = (candidates[0]["content"] as Map<*, *>)["parts"] as List<Map<*, *>>
            val text = parts[0]["text"] as String
            val parsed = objectMapper.readValue(text, Map::class.java)
            Idea(
                title = parsed["title"] as String,
                description = parsed["description"] as String,
                purpose = parsed["purpose"] as? String,
                howItWorks = parsed["howItWorks"] as? String,
                suggestedStack = parsed["suggestedStack"] as? String,
                sourceTrack = signal.track,
                sourceUrl = signal.url,
                rawSignal = signal.body,
            )
        } catch (e: Exception) {
            log.warn("Failed to parse Gemini JSON response, using signal as fallback", e)
            Idea(
                title = signal.title,
                description = signal.body,
                sourceTrack = signal.track,
                sourceUrl = signal.url,
                rawSignal = signal.body,
            )
        }
    }

    private fun systemInstruction(track: SourceTrack): String = when (track) {
        SourceTrack.SAAS -> """You are a senior product strategist and startup advisor with 15 years of experience building and investing in B2B SaaS companies.
Your job is to extract the sharpest possible startup idea from a given market signal.
Rules:
- The idea must be specific, not generic. Name the exact target customer (e.g. "indie hackers running solo SaaS" not "businesses").
- The problem must be a real pain, not a nice-to-have.
- The description must be concrete enough that a developer could start building tomorrow.
- Write in a confident, direct tone. No corporate jargon."""
        SourceTrack.GITHUB -> """You are a veteran developer-turned-product manager who spots product opportunities hidden in open-source trends.
Your job is to look at a GitHub project or trend and extract the product gap it reveals — what would non-developers pay for if this technology were productized?
Rules:
- Identify who is underserved by the current open-source tooling.
- The product idea must add real value on top of, or around, the raw technology.
- Focus on distribution: who would buy this and how would they find it?
- Be specific about the integration points and automation opportunities."""
        SourceTrack.VIRAL -> """You are a consumer product designer who has launched three apps with over 1M downloads each.
Your job is to take a viral concept or trend and design a concrete, buildable consumer product around it.
Rules:
- The product must tap into a genuine human emotion or social behavior.
- Name the exact moment in someone's day when they would use this.
- The hook (why people share it) must be built into the core experience.
- Suggest a specific viral loop or growth mechanic."""
    }

    private fun normalize(text: String): String = text
        .replace(Regex("<[^>]++>"), "")
        .replace(Regex("https?://\\S+"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(2000)

    private fun buildPrompt(signal: RawSignal): String {
        val title = normalize(signal.title)
        val body = normalize(signal.body)
        return """## Market Signal

Title: $title

Context: $body

## Your Task

Analyze this signal deeply and generate a sharp, actionable startup idea. Be specific — avoid vague generalities.

Respond with JSON only (no markdown, no explanation outside the JSON):
{
  "title": "Short, memorable product name (2-4 words, no generic words like 'Pro' or 'AI')",
  "description": "3-4 sentence pitch. Lead with the specific problem, then the solution, then why now. Write like you're pitching to a skeptical investor.",
  "purpose": "정확히 누가, 어떤 상황에서, 어떤 고통을 겪는지 — 한국어로 구체적으로 서술. 막연한 표현 금지. 실제 사용자 시나리오로 설명 (3-4문장).",
  "howItWorks": "어떻게 동작하나요 — 사용자 관점에서 단계별로 설명. 각 단계는 번호로 시작. 핵심 기술/자동화 포인트를 명시. 최소 4단계.",
  "suggestedStack": "구체적인 기술 스택: 프론트엔드, 백엔드, DB, 핵심 라이브러리/API를 각각 명시. 이유도 한 줄씩."
}"""
    }
}
