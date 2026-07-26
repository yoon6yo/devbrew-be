package com.devbrew.llm

import com.devbrew.config.DevBrewProperties
import com.devbrew.idea.Idea
import com.devbrew.idea.IdeaStatus
import com.devbrew.idea.SourceTrack
import com.devbrew.pipeline.collector.RawSignal
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
class GeminiIdeaGenerator(
    private val webClient: WebClient,
    private val props: DevBrewProperties,
    private val objectMapper: ObjectMapper,
    private val geminiDailyBudget: GeminiDailyBudget,
) : IdeaGenerator {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun generate(signal: RawSignal): Idea {
        if (geminiDailyBudget.isDailyBudgetExceeded()) {
            log.warn("Daily Gemini budget ({}KRW) exceeded — using signal fallback", props.gemini.dailyBudgetKrw)
            return Idea(
                title = signal.title,
                description = signal.body,
                sourceTrack = signal.track,
                sourceUrl = signal.url,
                rawSignal = signal.body,
            )
        }

        val uri = "${props.gemini.baseUrl}/v1beta/models/${props.gemini.model}:generateContent"

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
            runCatching { geminiDailyBudget.recordUsage(prompt, completion) }
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
        return "Signal: $title\n\n$body\n\nGenerate a startup idea. Respond with JSON only: {\"title\": \"...\", \"description\": \"...\"}"
    }
}
