package com.daybrew.idea

import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime

@Schema(description = "아이디어 응답 DTO")
data class IdeaDto(
    @Schema(description = "아이디어 고유 ID") val id: Long,
    @Schema(description = "아이디어 제목") val title: String,
    @Schema(description = "아이디어 상세 설명") val description: String,
    @Schema(description = "수집 출처 트랙 (SAAS, OPEN_SOURCE 등)") val sourceTrack: SourceTrack,
    @Schema(description = "원본 URL (없을 수 있음)") val sourceUrl: String?,
    @Schema(description = "가중 합산 최종 점수 (1~10). 채점 전이면 null", minimum = "1", maximum = "10") val score: Short?,
    @Schema(description = "시장 적합성 (1~10)") val scoreMarketFit: Short?,
    @Schema(description = "참신성/차별성 (1~10)") val scoreNovelty: Short?,
    @Schema(description = "실현 가능성 (1~10)") val scoreFeasibility: Short?,
    @Schema(description = "수익화 가능성 (1~10)") val scoreMonetization: Short?,
    @Schema(description = "트렌드 정합성 (1~10)") val scoreTrend: Short?,
    @Schema(description = "채점 근거 요약. 채점 전이면 null") val scoreReason: String?,
    @Schema(description = "누적 스타 수") val starCount: Int,
    @Schema(description = "아이디어 처리 상태 (PENDING → SCORED → NOTIFIED / REJECTED)") val status: IdeaStatus,
    @Schema(description = "생성 시각 (ISO 8601)") val createdAt: OffsetDateTime,
    @Schema(description = "사용 목적 — 어떤 문제를 해결하는가") val purpose: String?,
    @Schema(description = "어떻게 동작하나요 — 단계별 동작 방식") val howItWorks: String?,
    @Schema(description = "추천 기술 스택") val suggestedStack: String?,
    @Schema(description = "구현 방법 가이드") val implementationGuide: String?,
)

fun Idea.toDto() = IdeaDto(
    id = id,
    title = title,
    description = description,
    sourceTrack = sourceTrack,
    sourceUrl = sourceUrl,
    score = score,
    scoreMarketFit = scoreMarketFit,
    scoreNovelty = scoreNovelty,
    scoreFeasibility = scoreFeasibility,
    scoreMonetization = scoreMonetization,
    scoreTrend = scoreTrend,
    scoreReason = scoreReason,
    starCount = starCount,
    status = status,
    createdAt = createdAt,
    purpose = purpose,
    howItWorks = howItWorks,
    suggestedStack = suggestedStack,
    implementationGuide = implementationGuide,
)
