package com.devbrew.idea

import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime

@Schema(description = "아이디어 응답 DTO")
data class IdeaDto(
    @Schema(description = "아이디어 고유 ID") val id: Long,
    @Schema(description = "아이디어 제목") val title: String,
    @Schema(description = "아이디어 상세 설명") val description: String,
    @Schema(description = "수집 출처 트랙 (SAAS, OPEN_SOURCE 등)") val sourceTrack: SourceTrack,
    @Schema(description = "원본 URL (없을 수 있음)") val sourceUrl: String?,
    @Schema(description = "LLM 채점 점수 (1~10). 채점 전이면 null", minimum = "1", maximum = "10") val score: Short?,
    @Schema(description = "채점 근거 요약. 채점 전이면 null") val scoreReason: String?,
    @Schema(description = "누적 스타 수") val starCount: Int,
    @Schema(description = "아이디어 처리 상태 (PENDING → SCORED → NOTIFIED / REJECTED)") val status: IdeaStatus,
    @Schema(description = "생성 시각 (ISO 8601)") val createdAt: OffsetDateTime,
)

fun Idea.toDto() = IdeaDto(
    id = id,
    title = title,
    description = description,
    sourceTrack = sourceTrack,
    sourceUrl = sourceUrl,
    score = score,
    scoreReason = scoreReason,
    starCount = starCount,
    status = status,
    createdAt = createdAt,
)
