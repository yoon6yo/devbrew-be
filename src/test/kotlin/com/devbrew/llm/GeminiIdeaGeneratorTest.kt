package com.devbrew.llm

import com.devbrew.config.DevBrewProperties
import com.devbrew.idea.SourceTrack
import com.devbrew.pipeline.collector.RawSignal
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient

class GeminiIdeaGeneratorTest {

    private lateinit var wireMock: WireMockServer
    private lateinit var generator: GeminiIdeaGenerator
    private val geminiDailyBudget = mockk<GeminiDailyBudget>(relaxed = true)

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
        every { geminiDailyBudget.isDailyBudgetExceeded() } returns false
        generator = GeminiIdeaGenerator(WebClient.builder().build(), props, ObjectMapper(), geminiDailyBudget)
    }

    @AfterEach
    fun tearDown() = wireMock.stop()

    @Test
    fun `generates idea from SAAS signal with valid JSON response`() {
        wireMock.stubFor(
            post(urlPathMatching("/v1beta/models/.*:generateContent"))
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
            post(urlPathMatching("/v1beta/models/.*:generateContent"))
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
            post(urlPathMatching("/v1beta/models/.*:generateContent"))
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

    @Test
    fun `normalize strips HTML tags before sending to Gemini`() {
        wireMock.stubFor(
            post(urlPathMatching("/v1beta/models/.*:generateContent"))
                .willReturn(aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(geminiResponse("""{"title":"Clean","description":"Stripped"}""")))
        )

        generator.generate(RawSignal(
            title = "<h1>HTML Title</h1>",
            body = "<p>This is <b>bold</b> content with <a href='x'>links</a></p>",
            url = null,
            track = SourceTrack.SAAS,
        ))

        wireMock.verify(
            com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor(urlPathMatching("/v1beta/models/.*:generateContent"))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.notContaining("<h1>"))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.notContaining("<p>"))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.notContaining("<b>"))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.containing("HTML Title"))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.containing("bold"))
        )
    }

    @Test
    fun `normalize strips URLs before sending to Gemini`() {
        wireMock.stubFor(
            post(urlPathMatching("/v1beta/models/.*:generateContent"))
                .willReturn(aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(geminiResponse("""{"title":"No URL","description":"Clean"}""")))
        )

        generator.generate(RawSignal(
            title = "Check this out https://reddit.com/r/SaaS/123456",
            body = "Great post https://example.com/very-long-url here",
            url = null,
            track = SourceTrack.SAAS,
        ))

        wireMock.verify(
            com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor(urlPathMatching("/v1beta/models/.*:generateContent"))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.notContaining("https://reddit.com"))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.notContaining("https://example.com"))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.containing("Check this out"))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.containing("Great post"))
        )
    }

    @Test
    fun `normalize truncates body at 2000 characters`() {
        wireMock.stubFor(
            post(urlPathMatching("/v1beta/models/.*:generateContent"))
                .willReturn(aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(geminiResponse("""{"title":"Long","description":"Truncated"}""")))
        )

        val longBody = "a".repeat(3000)
        generator.generate(RawSignal(title = "Test", body = longBody, url = null, track = SourceTrack.SAAS))

        wireMock.verify(
            com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor(urlPathMatching("/v1beta/models/.*:generateContent"))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.notContaining("a".repeat(2001)))
        )
    }

    @Test
    fun `generate throws when Gemini returns HTTP 500`() {
        wireMock.stubFor(
            post(urlPathMatching("/v1beta/models/.*:generateContent"))
                .willReturn(aResponse().withStatus(500).withBody("Internal Server Error"))
        )

        org.assertj.core.api.Assertions.assertThatThrownBy {
            generator.generate(RawSignal(title = "T", body = "B", url = null, track = SourceTrack.SAAS))
        }.isInstanceOf(Exception::class.java)
    }

    @Test
    fun `generate falls back to signal when Gemini response has no candidates`() {
        wireMock.stubFor(
            post(urlPathMatching("/v1beta/models/.*:generateContent"))
                .willReturn(aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("""{"candidates":[]}"""))
        )

        val idea = generator.generate(RawSignal(
            title = "Fallback Signal",
            body = "Fallback body",
            url = "https://example.com",
            track = SourceTrack.VIRAL,
        ))

        assertThat(idea.title).isEqualTo("Fallback Signal")
        assertThat(idea.description).isEqualTo("Fallback body")
        assertThat(idea.sourceUrl).isEqualTo("https://example.com")
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
