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
        val today = LocalDate.now()
        val updated = pageViewsRepository.incrementCount(today)
        if (updated == 0) {
            // First view of the day — insert new record; swallow duplicate on concurrent first-hit
            runCatching { pageViewsRepository.save(DailyPageViews(viewDate = today, count = 1)) }
        }
    }

    @Transactional(readOnly = true)
    fun getStats(): AdminStatsDto {
        val todayStart = OffsetDateTime.now().truncatedTo(ChronoUnit.DAYS)
        val monthStart = OffsetDateTime.now().withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS)

        val todayTokens = geminiUsageRepository.sumTotalTokensSince(todayStart)
        val month = geminiUsageRepository.sumTokensSince(monthStart)
        // Gemini 2.5 Flash: input $0.15/1M, output $0.60/1M
        val estimatedCost = (month.promptTokens * 0.15 + month.completionTokens * 0.60) / 1_000_000.0

        val pageViews = pageViewsRepository.findTop7ByOrderByViewDateDesc()
            .reversed()
            .map { DailyViewsDto(it.viewDate.toString(), it.count) }

        return AdminStatsDto(
            gemini = GeminiStatsDto(
                todayTokens = todayTokens,
                monthTokens = month.totalTokens,
                estimatedMonthlyCostUsd = estimatedCost,
            ),
            pageViews = pageViews,
        )
    }
}
