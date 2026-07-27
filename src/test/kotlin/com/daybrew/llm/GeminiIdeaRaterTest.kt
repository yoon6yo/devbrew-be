package com.daybrew.llm

import com.daybrew.admin.AdminStatsService
import com.daybrew.idea.Idea
import com.daybrew.idea.SourceTrack
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GeminiIdeaRaterTest {

    private val batchClient = mockk<GeminiBatchClient>()
    private val adminStatsService = mockk<AdminStatsService>(relaxed = true)
    private lateinit var rater: GeminiIdeaRater

    @BeforeEach
    fun setUp() {
        rater = GeminiIdeaRater(ObjectMapper(), adminStatsService, batchClient)
    }

    private fun batchResponse(ideaId: Long, subScore: Int, reason: String): Map<String, Map<String, Any>> {
        val text = """{"market_fit":$subScore,"novelty":$subScore,"feasibility":$subScore,"monetization":$subScore,"trend":$subScore,"reason":"$reason"}"""
        val resp: Map<String, Any> = mapOf(
            "candidates" to listOf(mapOf(
                "content" to mapOf("parts" to listOf(mapOf("text" to text)))
            )),
            "usageMetadata" to mapOf("promptTokenCount" to 100, "candidatesTokenCount" to 50),
        )
        return mapOf("idea-$ideaId" to resp)
    }

    @Test
    fun `rates single idea via batch`() {
        val idea = Idea(id = 1L, title = "Test Idea", description = "A great idea", sourceTrack = SourceTrack.SAAS)
        every { batchClient.submitAndAwait(any(), any()) } returns batchResponse(1L, 8, "Strong market fit")

        val result = rater.rateAll(listOf(idea))

        assertThat(result).containsKey(1L)
        assertThat(result[1L]!!.score).isEqualTo(8.toShort())
        assertThat(result[1L]!!.reason).isEqualTo("Strong market fit")
    }

    @Test
    fun `rates multiple ideas`() {
        val ideas = listOf(
            Idea(id = 1L, title = "Idea A", description = "Desc A", sourceTrack = SourceTrack.SAAS),
            Idea(id = 2L, title = "Idea B", description = "Desc B", sourceTrack = SourceTrack.GITHUB),
        )
        val text7 = """{"market_fit":7,"novelty":7,"feasibility":7,"monetization":7,"trend":7,"reason":"Good potential"}"""
        val resp: Map<String, Any> = mapOf(
            "candidates" to listOf(mapOf("content" to mapOf("parts" to listOf(mapOf("text" to text7)))))
        )
        every { batchClient.submitAndAwait(any(), any()) } returns mapOf("idea-1" to resp, "idea-2" to resp)

        val result = rater.rateAll(ideas)

        assertThat(result).hasSize(2)
        assertThat(result[1L]!!.score).isEqualTo(7.toShort())
        assertThat(result[2L]!!.score).isEqualTo(7.toShort())
    }

    @Test
    fun `returns empty map when ideas list is empty`() {
        val result = rater.rateAll(emptyList())
        assertThat(result).isEmpty()
    }

    @Test
    fun `clamps score to valid range 1-10`() {
        val idea = Idea(id = 1L, title = "Idea", description = "Desc", sourceTrack = SourceTrack.VIRAL)
        every { batchClient.submitAndAwait(any(), any()) } returns batchResponse(1L, 15, "Out of range")

        val result = rater.rateAll(listOf(idea))

        assertThat(result[1L]!!.score).isEqualTo(10.toShort())
    }

    @Test
    fun `skips idea when batch response key is missing`() {
        val idea = Idea(id = 1L, title = "Idea", description = "Desc", sourceTrack = SourceTrack.SAAS)
        every { batchClient.submitAndAwait(any(), any()) } returns emptyMap()

        val result = rater.rateAll(listOf(idea))

        assertThat(result).isEmpty()
    }

    @Test
    fun `propagates exception when batch client fails`() {
        val idea = Idea(id = 1L, title = "Idea", description = "Desc", sourceTrack = SourceTrack.SAAS)
        every { batchClient.submitAndAwait(any(), any()) } throws RuntimeException("API down")

        assertThatThrownBy { rater.rateAll(listOf(idea)) }
            .isInstanceOf(RuntimeException::class.java)
    }
}
