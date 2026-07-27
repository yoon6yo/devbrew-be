package com.daybrew.admin

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.offset
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.OffsetDateTime

class AdminStatsServiceTest {

    private val geminiUsageRepository = mockk<GeminiUsageRepository>()
    private val pageViewsRepository = mockk<DailyPageViewsRepository>()
    private lateinit var service: AdminStatsService

    @BeforeEach
    fun setUp() {
        service = AdminStatsService(geminiUsageRepository, pageViewsRepository)
    }

    // ── recordGeminiUsage ─────────────────────────────────────────────────────

    @Test
    fun `recordGeminiUsage saves entity with correct totals`() {
        val saved = slot<GeminiUsage>()
        every { geminiUsageRepository.save(capture(saved)) } answers { saved.captured }

        service.recordGeminiUsage("generate", 400, 100)

        assertThat(saved.captured.operation).isEqualTo("generate")
        assertThat(saved.captured.promptTokens).isEqualTo(400)
        assertThat(saved.captured.completionTokens).isEqualTo(100)
        assertThat(saved.captured.totalTokens).isEqualTo(500)
    }

    @Test
    fun `recordGeminiUsage persists via repository`() {
        every { geminiUsageRepository.save(any()) } answers { firstArg() }

        service.recordGeminiUsage("rate", 200, 50)

        verify(exactly = 1) { geminiUsageRepository.save(any()) }
    }

    // ── incrementPageViews ────────────────────────────────────────────────────

    @Test
    fun `incrementPageViews saves new record when no row exists for today`() {
        val saved = slot<DailyPageViews>()
        every { pageViewsRepository.incrementCount(any()) } returns 0
        every { pageViewsRepository.save(capture(saved)) } answers { saved.captured }

        service.incrementPageViews()

        assertThat(saved.captured.count).isEqualTo(1)
        verify(exactly = 1) { pageViewsRepository.save(any()) }
    }

    @Test
    fun `incrementPageViews uses atomic UPDATE when row already exists`() {
        every { pageViewsRepository.incrementCount(any()) } returns 1

        service.incrementPageViews()

        verify(exactly = 0) { pageViewsRepository.save(any()) }
    }

    // ── getStats ──────────────────────────────────────────────────────────────

    @Test
    fun `getStats returns today and month token totals`() {
        every { geminiUsageRepository.sumTotalTokensSince(any()) } returnsMany listOf(120L, 5000L)
        every { geminiUsageRepository.sumPromptTokensSince(any()) } returns 4000L
        every { geminiUsageRepository.sumCompletionTokensSince(any()) } returns 1000L
        every { pageViewsRepository.findTop7ByOrderByViewDateDesc() } returns emptyList()

        val stats = service.getStats()

        assertThat(stats.gemini.todayTokens).isEqualTo(120L)
        assertThat(stats.gemini.monthTokens).isEqualTo(5000L)
    }

    @Test
    fun `getStats calculates estimated cost using Gemini Flash Lite pricing`() {
        // 2M prompt tokens @ $0.10/1M = $0.20, 500K completion @ $0.40/1M = $0.20 → total $0.40
        every { geminiUsageRepository.sumTotalTokensSince(any()) } returns 0L
        every { geminiUsageRepository.sumPromptTokensSince(any()) } returns 2_000_000L
        every { geminiUsageRepository.sumCompletionTokensSince(any()) } returns 500_000L
        every { pageViewsRepository.findTop7ByOrderByViewDateDesc() } returns emptyList()

        val stats = service.getStats()

        assertThat(stats.gemini.estimatedMonthlyCostUsd).isCloseTo(0.40, offset(0.0001))
    }

    @Test
    fun `getStats returns zero cost when no usage`() {
        every { geminiUsageRepository.sumTotalTokensSince(any()) } returns 0L
        every { geminiUsageRepository.sumPromptTokensSince(any()) } returns 0L
        every { geminiUsageRepository.sumCompletionTokensSince(any()) } returns 0L
        every { pageViewsRepository.findTop7ByOrderByViewDateDesc() } returns emptyList()

        val stats = service.getStats()

        assertThat(stats.gemini.estimatedMonthlyCostUsd).isEqualTo(0.0)
    }

    @Test
    fun `getStats maps page views in chronological order`() {
        every { geminiUsageRepository.sumTotalTokensSince(any()) } returns 0L
        every { geminiUsageRepository.sumPromptTokensSince(any()) } returns 0L
        every { geminiUsageRepository.sumCompletionTokensSince(any()) } returns 0L
        every { pageViewsRepository.findTop7ByOrderByViewDateDesc() } returns listOf(
            DailyPageViews(viewDate = LocalDate.of(2026, 7, 27), count = 10),
            DailyPageViews(viewDate = LocalDate.of(2026, 7, 26), count = 5),
            DailyPageViews(viewDate = LocalDate.of(2026, 7, 25), count = 8),
        )

        val stats = service.getStats()

        assertThat(stats.pageViews).hasSize(3)
        assertThat(stats.pageViews[0].date).isEqualTo("2026-07-25")
        assertThat(stats.pageViews[2].date).isEqualTo("2026-07-27")
        assertThat(stats.pageViews[2].count).isEqualTo(10)
    }
}
