package com.daybrew.slack

import com.daybrew.config.DayBrewProperties
import com.daybrew.idea.Idea
import com.daybrew.idea.IdeaStatus
import com.daybrew.idea.SourceTrack
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient

class SlackNotifierTest {

    private lateinit var wireMock: WireMockServer

    @BeforeEach
    fun setUp() {
        wireMock = WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort())
        wireMock.start()
    }

    @AfterEach
    fun tearDown() = wireMock.stop()

    @Test
    fun `sends Block Kit payload to Slack webhook`() {
        wireMock.stubFor(post(urlEqualTo("/services/webhook"))
            .willReturn(aResponse().withStatus(200).withBody("ok")))

        val notifier = notifierWithUrl("http://localhost:${wireMock.port()}/services/webhook")
        val idea = Idea(
            id = 1L, title = "AI Scheduler", description = "Auto-schedule meetings with LLM",
            sourceTrack = SourceTrack.SAAS, score = 9, scoreReason = "High demand",
            sourceUrl = "https://reddit.com/r/SaaS/1",
        )

        notifier.notifyIdea(idea)

        wireMock.verify(postRequestedFor(urlEqualTo("/services/webhook")))
    }

    @Test
    fun `skips HTTP call when webhook URL is blank`() {
        val notifier = notifierWithUrl("")
        val idea = Idea(id = 2L, title = "No-op", description = "Desc", sourceTrack = SourceTrack.GITHUB)

        notifier.notifyIdea(idea)

        wireMock.verify(0, postRequestedFor(anyUrl()))
    }

    @Test
    fun `sends notification without source URL button when sourceUrl is null`() {
        wireMock.stubFor(post(urlEqualTo("/services/webhook"))
            .willReturn(aResponse().withStatus(200).withBody("ok")))

        val notifier = notifierWithUrl("http://localhost:${wireMock.port()}/services/webhook")
        val idea = Idea(
            id = 3L, title = "No-link Idea", description = "Desc",
            sourceTrack = SourceTrack.VIRAL, score = 7, scoreReason = "Viral potential",
            sourceUrl = null,
        )

        notifier.notifyIdea(idea)

        wireMock.verify(postRequestedFor(urlEqualTo("/services/webhook")))
    }

    private fun notifierWithUrl(url: String) = SlackNotifier(
        WebClient.builder().build(),
        DayBrewProperties(slack = DayBrewProperties.SlackProps(webhookUrl = url))
    )
}
