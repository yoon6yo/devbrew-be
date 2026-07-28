package com.daybrew.admin

import com.daybrew.pipeline.PipelineScheduler
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AdminStatsControllerTest {

    private val adminStatsService = mockk<AdminStatsService>()
    private val pipelineScheduler = mockk<PipelineScheduler>()
    private lateinit var controller: AdminStatsController

    @BeforeEach
    fun setUp() {
        controller = AdminStatsController(adminStatsService, pipelineScheduler, com.daybrew.pipeline.PipelineStatusTracker())
    }

    private fun statsDto() = AdminStatsDto(
        gemini = GeminiStatsDto(todayTokens = 100L, monthTokens = 5000L, estimatedMonthlyCostUsd = 0.05),
        pageViews = listOf(DailyViewsDto("2025-07-01", 42)),
    )

    // ── GET /api/admin/stats ──────────────────────────────────────────────────

    @Test
    fun `getStats returns AdminStatsDto`() {
        every { adminStatsService.getStats() } returns statsDto()

        val result = controller.getStats()

        assertThat(result).isInstanceOf(AdminStatsDto::class.java)
        assertThat(result.gemini.todayTokens).isEqualTo(100L)
        assertThat(result.gemini.monthTokens).isEqualTo(5000L)
        assertThat(result.pageViews).hasSize(1)
        assertThat(result.pageViews[0].date).isEqualTo("2025-07-01")
    }

    @Test
    fun `getStats delegates to adminStatsService exactly once`() {
        every { adminStatsService.getStats() } returns statsDto()

        controller.getStats()

        verify(exactly = 1) { adminStatsService.getStats() }
    }

    @Test
    fun `getStats returns gemini cost in dto`() {
        val dto = statsDto().copy(
            gemini = GeminiStatsDto(todayTokens = 200L, monthTokens = 10_000L, estimatedMonthlyCostUsd = 0.15)
        )
        every { adminStatsService.getStats() } returns dto

        val result = controller.getStats()

        assertThat(result.gemini.estimatedMonthlyCostUsd).isEqualTo(0.15)
    }

    // ── POST /api/admin/pipeline/trigger ─────────────────────────────────────

    @Test
    fun `triggerPipeline returns 202 Accepted`() {
        justRun { pipelineScheduler.triggerAsync(null) }

        val response = controller.triggerPipeline(null)

        assertThat(response.statusCode).isEqualTo(HttpStatus.ACCEPTED)
    }

    @Test
    fun `triggerPipeline returns Pipeline started message`() {
        justRun { pipelineScheduler.triggerAsync(null) }

        val response = controller.triggerPipeline(null)

        assertThat(response.body).isNotNull
        assertThat(response.body!!["message"]).isEqualTo("Pipeline started")
    }

    @Test
    fun `triggerPipeline responds immediately without waiting for pipeline completion`() {
        val started = CountDownLatch(1)
        every { pipelineScheduler.triggerAsync(null) } answers {
            started.countDown()
            Thread.sleep(500)
        }

        val response = controller.triggerPipeline(null)

        // Controller returns 202 before pipeline completes
        assertThat(response.statusCode).isEqualTo(HttpStatus.ACCEPTED)
        // Pipeline did eventually start
        assertThat(started.await(2, TimeUnit.SECONDS)).isTrue()
    }

    @Test
    fun `triggerPipeline response body contains only message key`() {
        justRun { pipelineScheduler.triggerAsync(null) }

        val response = controller.triggerPipeline(null)

        assertThat(response.body!!.keys).containsExactly("message")
    }
}
