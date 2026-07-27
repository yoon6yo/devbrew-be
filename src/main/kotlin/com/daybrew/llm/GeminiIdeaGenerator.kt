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
        SourceTrack.SAAS -> "You are a startup idea generator. Extract and refine a clear SaaS product idea from the given signal. Focus on the core value proposition and target market."
        SourceTrack.GITHUB -> "You are a tech trend analyst. Identify the underlying technology trend and the product opportunity behind this GitHub signal."
        SourceTrack.VIRAL -> "You are a viral product designer. Identify what makes this concept appealing and suggest a concrete consumer product idea."
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
        return """Signal: $title

$body

Generate a startup idea. Respond with JSON only:
{
  "title": "concise product name",
  "description": "1-2 sentence overview of the product",
  "purpose": "사용 목적 — what specific problem this solves and who experiences it (2-3 sentences)",
  "howItWorks": "어떻게 동작하나요 — step-by-step explanation of how the product works (2-4 steps, each on a new line starting with a number)",
  "suggestedStack": "recommended tech stack and key libraries (concise list)"
}"""
    }
}
