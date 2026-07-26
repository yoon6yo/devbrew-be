package com.daybrew.admin

import com.daybrew.config.DayBrewProperties
import com.daybrew.auth.JwtTokenProvider
import com.daybrew.auth.UserRole
import com.daybrew.pipeline.PipelineScheduler
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

class AdminStatsControllerTest {

    private val adminStatsService = mockk<AdminStatsService>()
    private val pipelineScheduler = mockk<PipelineScheduler>()
    private lateinit var controller: AdminStatsController

    private val jwtTokenProvider = JwtTokenProvider(
        DayBrewProperties(
            jwt = DayBrewProperties.JwtProps(
                secret = "test-secret-key-minimum-32-characters-ok",
                expirationMs = 3_600_000,
            )
        )
    )

    @BeforeEach
    fun setUp() {
        controller = AdminStatsController(adminStatsService, pipelineScheduler)
    }

    private fun adminToken(): String =
        jwtTokenProvider.generate(1L, "admin@daybrew.local", UserRole.ADMIN)

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
        justRun { pipelineScheduler.runPipeline() }

        val response = controller.triggerPipeline()

        assertThat(response.statusCode).isEqualTo(HttpStatus.ACCEPTED)
    }

    @Test
    fun `triggerPipeline returns Pipeline started message`() {
        justRun { pipelineScheduler.runPipeline() }

        val response = controller.triggerPipeline()

        assertThat(response.body).isNotNull
        assertThat(response.body!!["message"]).isEqualTo("Pipeline started")
    }

    @Test
    fun `triggerPipeline responds immediately without waiting for pipeline completion`() {
        // Pipeline runs asynchronously via CompletableFuture.runAsync — the controller
        // must return before the pipeline finishes. We verify the response is returned
        // regardless of pipeline execution timing.
        every { pipelineScheduler.runPipeline() } answers {
            Thread.sleep(200) // simulate slow pipeline
        }

        val start = System.currentTimeMillis()
        val response = controller.triggerPipeline()
        val elapsed = System.currentTimeMillis() - start

        assertThat(response.statusCode).isEqualTo(HttpStatus.ACCEPTED)
        // Controller returns immediately (well under 200ms pipeline delay)
        assertThat(elapsed).isLessThan(200L)
    }

    @Test
    fun `triggerPipeline response body contains only message key`() {
        justRun { pipelineScheduler.runPipeline() }

        val response = controller.triggerPipeline()

        assertThat(response.body!!.keys).containsExactly("message")
    }
}
