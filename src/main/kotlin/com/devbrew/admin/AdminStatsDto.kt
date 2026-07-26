package com.devbrew.admin

data class AdminStatsDto(
    val gemini: GeminiStatsDto,
    val pageViews: List<DailyViewsDto>,
)

data class GeminiStatsDto(
    val todayTokens: Long,
    val monthTokens: Long,
    val estimatedMonthlyCostUsd: Double,
)

data class DailyViewsDto(
    val date: String,
    val count: Int,
)
