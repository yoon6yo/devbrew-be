package com.devbrew.llm

import com.devbrew.config.DevBrewProperties
import com.devbrew.idea.Idea
import com.devbrew.idea.SourceTrack
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient

class GeminiIdeaRaterTest {

    private lateinit var wireMock: WireMockServer
    private lateinit var rater: GeminiIdeaRater

    @BeforeEach
    fun setUp() {
        wireMock = WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort())
        wireMock.start()

        val props = DevBrewProperties(
            gemini = DevBrewProperties.GeminiProps(
                apiKey = "test-key",
                baseUrl = "http://localhost:${wireMock.port()}",
                model = "gemini-test",
            )
        )
        rater = GeminiIdeaRater(WebClient.builder().build(), props, ObjectMapper())
    }

    @AfterEach
    fun tearDown() = wireMock.stop()

    private fun geminiResponse(score: Int, reason: String): String {
        val text = "{\\\"score\\\":$score,\\\"reason\\\":\\\"$reason\\\"}"
        return """{"candidates":[{"content":{"parts":[{"text":"$text"}]}}]}"""
    }

    @Test
    fun `rates single idea via generateContent`() {
        wireMock.stubFor(post(urlEqualTo("/v1beta/models/gemini-test:generateContent"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody(geminiResponse(8, "Strong market fit"))))

        val idea = Idea(id = 1L, title = "Test Idea", description = "A great idea", sourceTrack = SourceTrack.SAAS)
        val result = rater.rateAll(listOf(idea))

        assertThat(result).containsKey(1L)
        assertThat(result[1L]!!.first).isEqualTo(8.toShort())
        assertThat(result[1L]!!.second).isEqualTo("Strong market fit")
    }

    @Test
    fun `rates multiple ideas concurrently`() {
        wireMock.stubFor(post(urlEqualTo("/v1beta/models/gemini-test:generateContent"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody(geminiResponse(7, "Good potential"))))

        val ideas = listOf(
            Idea(id = 1L, title = "Idea A", description = "Desc A", sourceTrack = SourceTrack.SAAS),
            Idea(id = 2L, title = "Idea B", description = "Desc B", sourceTrack = SourceTrack.GITHUB),
        )
        val result = rater.rateAll(ideas)

        assertThat(result).hasSize(2)
        assertThat(result[1L]!!.first).isEqualTo(7.toShort())
        assertThat(result[2L]!!.first).isEqualTo(7.toShort())
    }

    @Test
    fun `returns empty map when ideas list is empty`() {
        val result = rater.rateAll(emptyList())
        assertThat(result).isEmpty()
    }

    @Test
    fun `clamps score to valid range 1-10`() {
        wireMock.stubFor(post(urlEqualTo("/v1beta/models/gemini-test:generateContent"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody(geminiResponse(15, "Out of range"))))

        val idea = Idea(id = 1L, title = "Idea", description = "Desc", sourceTrack = SourceTrack.VIRAL)
        val result = rater.rateAll(listOf(idea))

        assertThat(result[1L]!!.first).isEqualTo(10.toShort())
    }

    @Test
    fun `skips idea when rating response is malformed`() {
        wireMock.stubFor(post(urlEqualTo("/v1beta/models/gemini-test:generateContent"))
            .willReturn(aResponse()
                .withStatus(500)))

        val idea = Idea(id = 1L, title = "Idea", description = "Desc", sourceTrack = SourceTrack.SAAS)
        val result = rater.rateAll(listOf(idea))

        assertThat(result).isEmpty()
    }
}
