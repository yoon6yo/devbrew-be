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
                    "generationConfig" to mapOf("responseMimeType" to "application/json", "maxOutputTokens" to 800),
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
                implementationGuide = parsed["implementationGuide"] as? String,
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
        SourceTrack.SAAS -> """B2B SaaS 프로덕트 전략가입니다. 시장 신호에서 날카로운 스타트업 아이디어를 도출하세요.
- 정확한 타겟 고객 명시 (예: "혼자 운영하는 인디 해커")
- 없으면 안 되는 문제, 구체적 해결책
- 모든 응답 한국어. 영어 금지."""
        SourceTrack.GITHUB -> """GitHub 오픈소스 트렌드에서 사업 기회를 발굴하는 개발자 출신 PM입니다.
- 비개발자가 돈을 낼 만한 제품 기회 발굴
- 누가 살 것이며 어떻게 도달할지 중심
- 모든 응답 한국어. 영어 금지."""
        SourceTrack.VIRAL -> """바이럴 트렌드를 실제 제품으로 설계하는 컨슈머 프로덕트 디자이너입니다.
- 진짜 인간적 감정이나 사회적 행동을 건드릴 것
- 공유 욕구가 핵심 경험에 내장되어야 함
- 모든 응답 한국어. 영어 금지."""
    }

    private fun normalize(text: String): String = text
        .replace(Regex("<[^>]++>"), "")
        .replace(Regex("https?://\\S+"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(800)

    private fun buildPrompt(signal: RawSignal): String {
        val title = normalize(signal.title)
        val body = normalize(signal.body)
        return """신호: $title / $body

JSON만 응답 (마크다운 금지). 모든 필드 한국어:
{
  "title": "'누가+무엇을+어떻게' 설명형 제목. 브랜드명 금지. 20자 이내.",
  "description": "2-3문장 핵심 피치. 문제→해결책→지금 해야 하는 이유 순서. purpose와 겹치지 않게 제품 가치 중심. 한국어.",
  "purpose": "description과 다른 내용. 타겟 사용자가 일상에서 겪는 구체적 고통 시나리오. 2-3문장. 한국어.",
  "howItWorks": "4단계 흐름. 반드시 줄바꿈(\\n)으로 구분. 형식: '① 단계\\n② 단계\\n③ 단계\\n④ 단계'. 각 단계 30자 이내.",
  "suggestedStack": "핵심 기술명만 콤마 구분. 설명 없이. 예: 'React, FastAPI, PostgreSQL, Gemini API'",
  "implementationGuide": "3개 핵심 구현 기술. 각 항목은 줄바꿈으로 구분. 형식 엄수: '1. 기술명\n- 사용 목적: 1문장\n- 구현: 1-2문장\n2. 기술명\n- 사용 목적: ...\n- 구현: ...\n3. 기술명\n- 사용 목적: ...\n- 구현: ...'. 한국어."
}"""
    }
}
