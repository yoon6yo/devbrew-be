package com.devbrew.llm

import com.devbrew.config.DevBrewProperties
import com.devbrew.idea.Idea
import com.devbrew.idea.SourceTrack
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient

class ClaudeIdeaRaterTest {

    private lateinit var wireMock: WireMockServer
    private lateinit var rater: ClaudeIdeaRater

    @BeforeEach
    fun setUp() {
        wireMock = WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort())
        wireMock.start()

        val props = DevBrewProperties(
            claude = DevBrewProperties.ClaudeProps(
                apiKey = "test-key",
                baseUrl = "http://localhost:${wireMock.port()}",
                batchPollIntervalMs = 10,
                batchMaxPolls = 5,
            )
        )
        rater = ClaudeIdeaRater(WebClient.builder().build(), props, ObjectMapper())
    }

    @AfterEach
    fun tearDown() = wireMock.stop()

    @Test
    fun `rates ideas via batch API submit-poll-results flow`() {
        wireMock.stubFor(post(urlEqualTo("/v1/messages/batches"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("""{"id":"msgbatch_test01","processing_status":"in_progress"}""")))

        wireMock.stubFor(get(urlEqualTo("/v1/messages/batches/msgbatch_test01"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("""{"id":"msgbatch_test01","processing_status":"ended"}""")))

        wireMock.stubFor(get(urlEqualTo("/v1/messages/batches/msgbatch_test01/results"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/x-ndjson")
                .withBody(resultLine("1", 8, "Strong market fit with clear monetisation path"))))

        val idea = Idea(id = 1L, title = "Test Idea", description = "A great idea", sourceTrack = SourceTrack.SAAS)

        val result = rater.rateAll(listOf(idea))

        assertThat(result).containsKey(1L)
        val (score, reason) = result[1L]!!
        assertThat(score).isEqualTo(8.toShort())
        assertThat(reason).isEqualTo("Strong market fit with clear monetisation path")
    }

    @Test
    fun `rates multiple ideas in one batch`() {
        wireMock.stubFor(post(urlEqualTo("/v1/messages/batches"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("""{"id":"msgbatch_multi","processing_status":"in_progress"}""")))

        wireMock.stubFor(get(urlEqualTo("/v1/messages/batches/msgbatch_multi"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("""{"id":"msgbatch_multi","processing_status":"ended"}""")))

        val results = resultLine("1", 9, "Excellent") + "\n" + resultLine("2", 4, "Too niche")
        wireMock.stubFor(get(urlEqualTo("/v1/messages/batches/msgbatch_multi/results"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/x-ndjson")
                .withBody(results)))

        val ideas = listOf(
            Idea(id = 1L, title = "Idea A", description = "Desc A", sourceTrack = SourceTrack.SAAS),
            Idea(id = 2L, title = "Idea B", description = "Desc B", sourceTrack = SourceTrack.GITHUB),
        )

        val result = rater.rateAll(ideas)

        assertThat(result).hasSize(2)
        assertThat(result[1L]!!.first).isEqualTo(9.toShort())
        assertThat(result[2L]!!.first).isEqualTo(4.toShort())
    }

    @Test
    fun `returns empty map when ideas list is empty`() {
        val result = rater.rateAll(emptyList())
        assertThat(result).isEmpty()
    }

    @Test
    fun `clamps score to valid range 1-10`() {
        wireMock.stubFor(post(urlEqualTo("/v1/messages/batches"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("""{"id":"msgbatch_clamp","processing_status":"in_progress"}""")))

        wireMock.stubFor(get(urlEqualTo("/v1/messages/batches/msgbatch_clamp"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("""{"id":"msgbatch_clamp","processing_status":"ended"}""")))

        wireMock.stubFor(get(urlEqualTo("/v1/messages/batches/msgbatch_clamp/results"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/x-ndjson")
                .withBody(resultLine("1", 15, "Out of range score"))))

        val idea = Idea(id = 1L, title = "Idea", description = "Desc", sourceTrack = SourceTrack.VIRAL)
        val result = rater.rateAll(listOf(idea))

        assertThat(result[1L]!!.first).isEqualTo(10.toShort())
    }

    private fun resultLine(customId: String, score: Int, reason: String): String {
        val textJson = ObjectMapper().writeValueAsString("""{"score":$score,"reason":"$reason"}""")
        return """{"custom_id":"$customId","result":{"type":"succeeded","message":{"content":[{"text":$textJson}]}}}"""
    }
}
