package com.devbrew.llm

import com.devbrew.config.DevBrewProperties
import com.devbrew.idea.SourceTrack
import com.devbrew.pipeline.collector.RawSignal
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient

class GeminiIdeaGeneratorTest {

    private lateinit var wireMock: WireMockServer
    private lateinit var generator: GeminiIdeaGenerator

    @BeforeEach
    fun setUp() {
        wireMock = WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort())
        wireMock.start()

        val props = DevBrewProperties(
            gemini = DevBrewProperties.GeminiProps(
                apiKey = "test-key",
                baseUrl = "http://localhost:${wireMock.port()}",
            )
        )
        generator = GeminiIdeaGenerator(WebClient.builder().build(), props, ObjectMapper())
    }

    @AfterEach
    fun tearDown() = wireMock.stop()

    @Test
    fun `generates idea from SAAS signal with valid JSON response`() {
        wireMock.stubFor(
            post(urlPathEqualTo("/v1beta/models/gemini-2.0-flash:generateContent"))
                .willReturn(aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(geminiResponse("""{"title":"Notion Alternative","description":"Affordable project management SaaS"}""")))
        )

        val idea = generator.generate(RawSignal(
            title = "Notion is too expensive",
            body = "Users want a cheaper alternative",
            url = "https://reddit.com/r/SaaS/1",
            track = SourceTrack.SAAS,
        ))

        assertThat(idea.title).isEqualTo("Notion Alternative")
        assertThat(idea.description).isEqualTo("Affordable project management SaaS")
        assertThat(idea.sourceTrack).isEqualTo(SourceTrack.SAAS)
        assertThat(idea.sourceUrl).isEqualTo("https://reddit.com/r/SaaS/1")
    }

    @Test
    fun `generates idea from GITHUB signal`() {
        wireMock.stubFor(
            post(urlPathEqualTo("/v1beta/models/gemini-2.0-flash:generateContent"))
                .willReturn(aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(geminiResponse("""{"title":"AI Code Review Tool","description":"Automated PR review via LLM"}""")))
        )

        val idea = generator.generate(RawSignal(
            title = "trending-llm-reviewer",
            body = "1500 stars, automated code reviews",
            url = "https://github.com/user/repo",
            track = SourceTrack.GITHUB,
        ))

        assertThat(idea.title).isEqualTo("AI Code Review Tool")
        assertThat(idea.sourceTrack).isEqualTo(SourceTrack.GITHUB)
    }

    @Test
    fun `falls back to signal data when Gemini returns invalid JSON`() {
        wireMock.stubFor(
            post(urlPathEqualTo("/v1beta/models/gemini-2.0-flash:generateContent"))
                .willReturn(aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(geminiResponse("This is not JSON")))
        )

        val idea = generator.generate(RawSignal(
            title = "Fallback Title",
            body = "Fallback body",
            url = null,
            track = SourceTrack.VIRAL,
        ))

        assertThat(idea.title).isEqualTo("Fallback Title")
        assertThat(idea.description).isEqualTo("Fallback body")
    }

    private fun geminiResponse(text: String): String = """
        {
          "candidates": [{
            "content": {
              "parts": [{"text": ${ObjectMapper().writeValueAsString(text)}}]
            }
          }]
        }
    """.trimIndent()
}
