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
        SourceTrack.SAAS -> """당신은 B2B SaaS 분야에서 15년 경력을 가진 시니어 프로덕트 전략가이자 스타트업 어드바이저입니다.
주어진 시장 신호에서 가장 날카로운 스타트업 아이디어를 도출하는 것이 당신의 임무입니다.
규칙:
- 아이디어는 구체적이어야 합니다. 정확한 타겟 고객을 명시하세요 (예: "혼자 운영하는 인디 해커" / "스타트업 초기 단계 CTO" 등).
- 문제는 실제 고통이어야 합니다. 있으면 좋은 수준이 아닌, 없으면 안 되는 문제.
- 개발자가 내일 당장 만들기 시작할 수 있을 만큼 구체적으로 서술하세요.
- 모든 응답은 반드시 한국어로 작성하세요. 영어 사용 금지."""
        SourceTrack.GITHUB -> """당신은 개발자 출신 프로덕트 매니저로, 오픈소스 트렌드에 숨겨진 사업 기회를 발굴하는 전문가입니다.
GitHub 프로젝트나 트렌드를 보고, 기술이 제품화되었을 때 비개발자가 돈을 낼 만한 제품 기회를 찾아내는 것이 임무입니다.
규칙:
- 현재 오픈소스 툴에서 소외된 사용자층을 식별하세요.
- 원시 기술 위에 실질적인 가치를 더하는 제품이어야 합니다.
- 누가 살 것이며, 어떻게 그들에게 도달할지 중심으로 생각하세요.
- 통합 포인트와 자동화 기회를 구체적으로 제시하세요.
- 모든 응답은 반드시 한국어로 작성하세요. 영어 사용 금지."""
        SourceTrack.VIRAL -> """당신은 100만 다운로드 이상의 앱을 세 개 출시한 경험 있는 컨슈머 프로덕트 디자이너입니다.
바이럴 개념이나 트렌드를 바탕으로 실제 만들 수 있는 구체적인 소비자 제품을 설계하는 것이 임무입니다.
규칙:
- 제품은 진짜 인간적인 감정이나 사회적 행동을 건드려야 합니다.
- 사용자의 하루 중 정확히 어떤 순간에 이 제품을 쓰는지 명시하세요.
- 공유 욕구(훅)가 핵심 경험 자체에 내장되어야 합니다.
- 구체적인 바이럴 루프나 성장 메커니즘을 제안하세요.
- 모든 응답은 반드시 한국어로 작성하세요. 영어 사용 금지."""
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
        return """## 시장 신호

제목: $title

본문: $body

## 작업 지시

위 신호를 깊이 분석하여 날카롭고 실행 가능한 스타트업 아이디어를 도출하세요. 막연한 일반론은 금지입니다.

JSON만 응답하세요 (마크다운, JSON 외 설명 일절 금지). 모든 필드는 반드시 한국어로 작성하세요:
{
  "title": "짧고 기억에 남는 제품명 (2-4 단어, '프로'/'AI' 같은 진부한 단어 금지, 한국어)",
  "description": "3-4문장 투자자 피치. 구체적인 문제 → 해결책 → 지금 해야 하는 이유 순서. 회의적인 투자자를 설득하듯 자신 있게. 한국어.",
  "purpose": "정확히 누가, 어떤 상황에서, 어떤 고통을 겪는지 구체적으로 서술. 막연한 표현 금지. 실제 사용자 시나리오로 설명 (3-4문장). 한국어.",
  "howItWorks": "사용자 관점에서 단계별로 설명. 각 단계는 번호로 시작. 핵심 기술/자동화 포인트 명시. 최소 4단계. 한국어.",
  "suggestedStack": "구체적인 기술 스택: 프론트엔드, 백엔드, DB, 핵심 라이브러리/API를 각각 명시하고 이유도 한 줄씩. 한국어."
}"""
    }
}
