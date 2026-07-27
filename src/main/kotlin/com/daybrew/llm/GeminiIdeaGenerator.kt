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
    private val batchClient: GeminiBatchClient,
) : IdeaGenerator {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun generate(signal: RawSignal): GeneratedResult {
        val uri = "${props.gemini.baseUrl}/v1/models/${props.gemini.model}:generateContent"

        @Suppress("UNCHECKED_CAST")
        val response = webClient.post()
            .uri(uri)
            .header("x-goog-api-key", props.gemini.apiKey)
            .bodyValue(
                mapOf(
                    "system_instruction" to mapOf("parts" to listOf(mapOf("text" to systemInstruction(signal.track)))),
                    "contents" to listOf(mapOf("parts" to listOf(mapOf("text" to buildPrompt(signal))))),
                    "generationConfig" to mapOf("responseMimeType" to "application/json", "maxOutputTokens" to 900),
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

    override fun generateBatch(signals: List<RawSignal>): List<GeneratedResult> {
        if (signals.isEmpty()) return emptyList()
        val requests = signals.mapIndexed { i, signal ->
            BatchRequest(
                key = "signal-$i",
                body = mapOf(
                    "system_instruction" to mapOf("parts" to listOf(mapOf("text" to systemInstruction(signal.track)))),
                    "contents" to listOf(mapOf("parts" to listOf(mapOf("text" to buildPrompt(signal))))),
                    "generationConfig" to mapOf("responseMimeType" to "application/json", "maxOutputTokens" to 900),
                )
            )
        }
        val responses = batchClient.submitAndAwait(requests, "daybrew-generate")
        var totalPrompt = 0; var totalCompletion = 0
        val results = signals.mapIndexed { i, signal ->
            val resp = responses["signal-$i"]
            if (resp == null) {
                log.warn("No batch response for signal: ${signal.title}")
                GeneratedResult(Idea(title = signal.title, description = signal.body,
                    sourceTrack = signal.track, sourceUrl = signal.url, rawSignal = signal.body), null)
            } else {
                (resp["usageMetadata"] as? Map<*, *>)?.let { u ->
                    totalPrompt += (u["promptTokenCount"] as? Number)?.toInt() ?: 0
                    totalCompletion += (u["candidatesTokenCount"] as? Number)?.toInt() ?: 0
                }
                runCatching { parseResponse(resp, signal) }.getOrElse {
                    log.warn("Parse failed for signal: ${signal.title}", it)
                    GeneratedResult(Idea(title = signal.title, description = signal.body,
                        sourceTrack = signal.track, sourceUrl = signal.url, rawSignal = signal.body), null)
                }
            }
        }
        runCatching { adminStatsService.recordGeminiUsage("generate-batch", totalPrompt, totalCompletion) }
        return results
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseResponse(response: Map<*, *>, signal: RawSignal): GeneratedResult {
        return try {
            val candidates = response["candidates"] as List<Map<*, *>>
            val parts = (candidates[0]["content"] as Map<*, *>)["parts"] as List<Map<*, *>>
            val text = parts[0]["text"] as String
            val parsed = objectMapper.readValue(text, Map::class.java)

            val idea = Idea(
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

            val score = runCatching {
                val s = parsed["score"] as Map<*, *>
                fun sc(key: String) = (s[key] as Number).toInt().coerceIn(1, 10).toShort()
                val mf = sc("market_fit"); val nv = sc("novelty")
                val fe = sc("feasibility"); val mn = sc("monetization"); val tr = sc("trend")
                val weighted = ((mf * 30 + nv * 20 + fe * 20 + mn * 20 + tr * 10) / 100).coerceIn(1, 10).toShort()
                ScoreResult(weighted, mf, nv, fe, mn, tr, s["reason"] as String)
            }.getOrNull()

            GeneratedResult(idea, score)
        } catch (e: Exception) {
            log.warn("Failed to parse Gemini JSON response, using signal as fallback", e)
            GeneratedResult(
                Idea(title = signal.title, description = signal.body,
                    sourceTrack = signal.track, sourceUrl = signal.url, rawSignal = signal.body),
                null
            )
        }
    }

    private fun systemInstruction(track: SourceTrack): String = when (track) {
        SourceTrack.SAAS -> """B2B SaaS 프로덕트 전략가입니다. 시장 신호에서 날카로운 스타트업 아이디어를 도출하세요.
- 정확한 타겟 고객 명시 (예: "혼자 운영하는 인디 해커")
- 없으면 안 되는 문제, 구체적 해결책
- 모든 응답 한국어. 기술 용어·브랜드명은 영어 허용."""
        SourceTrack.GITHUB -> """GitHub 오픈소스 트렌드에서 사업 기회를 발굴하는 개발자 출신 PM입니다.
- 비개발자가 돈을 낼 만한 제품 기회 발굴
- 누가 살 것이며 어떻게 도달할지 중심
- 모든 응답 한국어. 기술 용어·브랜드명은 영어 허용."""
        SourceTrack.VIRAL -> """바이럴 트렌드를 실제 제품으로 설계하는 컨슈머 프로덕트 디자이너입니다.
- 진짜 인간적 감정이나 사회적 행동을 건드릴 것
- 공유 욕구가 핵심 경험에 내장되어야 함
- Instagram, Notion, Duolingo, TikTok 등 이미 세상에 존재하는 유명 서비스와 유사하거나 단순 모방한 아이디어 절대 금지. 반드시 새로운 틈새 또는 기존 카테고리의 전혀 다른 결합을 제안할 것.
- 모든 응답 한국어. 기술 용어·브랜드명은 영어 허용."""
        SourceTrack.HACKERNEWS -> """Hacker News Show HN 게시물에서 빌더·인디 해커를 위한 사업 기회를 발굴하는 개발자 출신 PM입니다.
- 이미 작동하는 프로토타입이나 오픈소스에서 상업화 가능한 구체적 틈새를 찾을 것
- 1인 창업자 또는 소규모 팀이 6개월 내 유료 사용자를 확보할 수 있는 제품 중심
- 개발자·빌더가 직접 쓰거나 자신의 워크플로에 도입할 법한 도구를 우선
- 모든 응답 한국어. 기술 용어·브랜드명은 영어 허용."""
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

JSON만 응답 (마크다운 금지). 아이디어 생성 후 즉시 채점하라. 모든 텍스트 필드 한국어:
{
  "title": "'누가+무엇을+어떻게' 설명형 제목. 브랜드명 금지. 20자 이내.",
  "description": "2-3문장 핵심 피치. 문제→해결책→지금 해야 하는 이유 순서. purpose와 겹치지 않게 제품 가치 중심.",
  "purpose": "description과 다른 내용. 타겟 사용자가 일상에서 겪는 구체적 고통 시나리오. 2-3문장.",
  "howItWorks": "4단계 사용 흐름. 형식: '① 단계\\n② 단계\\n③ 단계\\n④ 단계'. 각 단계 30자 이내.",
  "suggestedStack": "핵심 기술명만 콤마 구분. 예: 'React, FastAPI, PostgreSQL'",
  "implementationGuide": "3개 핵심 구현 기술. 형식: '1. 기술명\n- 사용 목적: 1문장\n- 구현: 1-2문장\n2. ...\n3. ...'",
  "score": {
    "market_fit": <1-10>,
    "novelty": <1-10>,
    "feasibility": <1-10>,
    "monetization": <1-10>,
    "trend": <1-10>,
    "reason": "<강점과 핵심 리스크 120자 이내 한국어 요약>"
  }
}

채점 기준 — 반드시 엄격하게 적용:
1-3: 구조적 결함 (고객 없음, 순수 모방, 구현 불가, 수익 모델 없음)
4-6: 평범 (실현 가능하나 차별점 약함). 대부분의 아이디어는 여기 해당.
7: 진짜 강함 — 명확한 페인포인트 + 차별점 + 수익 경로 동시 충족. 전체의 20% 미만.
8: 탁월 — 명확한 경쟁 우위 또는 폭발적 성장 가능성. 전체의 5% 미만.
9-10: 극히 드묾 — 세대에 한 번 나올 법한 개념. 거의 부여 금지."""
    }
}
