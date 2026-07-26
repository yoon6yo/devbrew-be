package com.daybrew.admin

import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

@Service
class AdminStatsService(
    private val geminiUsageRepository: GeminiUsageRepository,
    private val pageViewsRepository: DailyPageViewsRepository,
) {

    @Transactional
    fun recordGeminiUsage(operation: String, promptTokens: Int, completionTokens: Int) {
        geminiUsageRepository.save(GeminiUsage(
            operation = operation,
            promptTokens = promptTokens,
            completionTokens = completionTokens,
            totalTokens = promptTokens + completionTokens,
        ))
    }

    @Async
    @Transactional
    fun incrementPageViews() {
        pageViewsRepository.upsertIncrement(LocalDate.now())
    }

    @Transactional(readOnly = true)
    fun getStats(): AdminStatsDto {
        val todayStart = OffsetDateTime.now().truncatedTo(ChronoUnit.DAYS)
        val monthStart = OffsetDateTime.now().withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS)

        val monthPrompt = geminiUsageRepository.sumPromptTokensSince(monthStart)
        val monthCompletion = geminiUsageRepository.sumCompletionTokensSince(monthStart)
        // Gemini 2.5 Flash Lite: input $0.10/1M, output $0.40/1M
        val estimatedCost = (monthPrompt * 0.10 + monthCompletion * 0.40) / 1_000_000.0

        val pageViews = pageViewsRepository.findTop7ByOrderByViewDateDesc()
            .reversed()
            .map { DailyViewsDto(it.viewDate.toString(), it.count) }

        return AdminStatsDto(
            gemini = GeminiStatsDto(
                todayTokens = geminiUsageRepository.sumTotalTokensSince(todayStart),
                monthTokens = geminiUsageRepository.sumTotalTokensSince(monthStart),
                estimatedMonthlyCostUsd = estimatedCost,
            ),
            pageViews = pageViews,
        )
    }
}
