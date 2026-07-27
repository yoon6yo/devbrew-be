package com.daybrew.pipeline

import com.daybrew.idea.*
import com.daybrew.llm.ScoreResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageRequest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("integration")
class PipelineIntegrationTest {

    @Autowired lateinit var ideaService: IdeaService
    @Autowired lateinit var ideaRepository: IdeaRepository
    @Autowired lateinit var userStarRepository: UserStarRepository

    @BeforeEach
    fun cleanup() {
        userStarRepository.deleteAll()
        ideaRepository.deleteAll()
    }

    private fun makeIdea(
        title: String = "Test Idea",
        sourceUrl: String? = null,
        rawSignal: String? = null,
        track: SourceTrack = SourceTrack.GITHUB,
    ) = Idea(title = title, description = "Test description", sourceTrack = track, sourceUrl = sourceUrl, rawSignal = rawSignal)

    private fun scoreResult() = ScoreResult(
        score = 75.toShort(),
        marketFit = 80.toShort(),
        novelty = 70.toShort(),
        feasibility = 75.toShort(),
        monetization = 72.toShort(),
        trend = 78.toShort(),
        reason = "Strong market fit with clear monetisation path",
    )

    // ── Save & retrieve ───────────────────────────────────────────────────────

    @Test
    fun `save persists idea and assigns generated id`() {
        val saved = ideaService.save(makeIdea("Persisted Idea"))
        assertThat(saved.id).isGreaterThan(0)
        assertThat(ideaRepository.findById(saved.id)).isPresent
    }

    @Test
    fun `getById returns saved idea`() {
        val saved = ideaService.save(makeIdea("Found Idea"))
        val found = ideaService.getById(saved.id)
        assertThat(found.title).isEqualTo("Found Idea")
        assertThat(found.status).isEqualTo(IdeaStatus.PENDING)
    }

    // ── Status transitions ────────────────────────────────────────────────────

    @Test
    fun `updateScore sets status to SCORED and persists all score fields`() {
        val saved = ideaService.save(makeIdea())
        val updated = ideaService.updateScore(saved.id, scoreResult())

        assertThat(updated.status).isEqualTo(IdeaStatus.SCORED)
        assertThat(updated.score).isEqualTo(75.toShort())
        assertThat(updated.scoreMarketFit).isEqualTo(80.toShort())
        assertThat(updated.scoreNovelty).isEqualTo(70.toShort())
        assertThat(updated.scoreFeasibility).isEqualTo(75.toShort())
        assertThat(updated.scoreMonetization).isEqualTo(72.toShort())
        assertThat(updated.scoreTrend).isEqualTo(78.toShort())
        assertThat(updated.scoreReason).isEqualTo("Strong market fit with clear monetisation path")
    }

    @Test
    fun `markNotified sets status to NOTIFIED`() {
        val saved = ideaService.save(makeIdea())
        val notified = ideaService.markNotified(saved.id)
        assertThat(notified.status).isEqualTo(IdeaStatus.NOTIFIED)
    }

    @Test
    fun `reject sets status to REJECTED`() {
        val saved = ideaService.save(makeIdea())
        val rejected = ideaService.reject(saved.id)
        assertThat(rejected.status).isEqualTo(IdeaStatus.REJECTED)
    }

    // ── Duplicate detection ───────────────────────────────────────────────────

    @Test
    fun `isDuplicate returns true when sourceUrl matches same track`() {
        ideaService.save(makeIdea(sourceUrl = "https://github.com/owner/repo"))
        assertThat(
            ideaService.isDuplicate("https://github.com/owner/repo", null, SourceTrack.GITHUB)
        ).isTrue
    }

    @Test
    fun `isDuplicate returns false when sourceUrl matches different track`() {
        ideaService.save(makeIdea(sourceUrl = "https://github.com/owner/repo", track = SourceTrack.GITHUB))
        assertThat(
            ideaService.isDuplicate("https://github.com/owner/repo", null, SourceTrack.SAAS)
        ).isFalse
    }

    @Test
    fun `isDuplicate returns true when rawSignal matches same track`() {
        ideaService.save(makeIdea(rawSignal = "unique signal text for testing"))
        assertThat(
            ideaService.isDuplicate(null, "unique signal text for testing", SourceTrack.GITHUB)
        ).isTrue
    }

    @Test
    fun `isDuplicate returns false when both sourceUrl and rawSignal are null`() {
        assertThat(ideaService.isDuplicate(null, null, SourceTrack.GITHUB)).isFalse
    }

    // ── Pagination and filtering ──────────────────────────────────────────────

    @Test
    fun `getPage with status filter returns only matching ideas`() {
        ideaService.save(makeIdea("Pending"))
        val toNotify = ideaService.save(makeIdea("Notified"))
        ideaService.markNotified(toNotify.id)

        val pendingPage = ideaService.getPage(IdeaStatus.PENDING, null, null, PageRequest.of(0, 10))
        val notifiedPage = ideaService.getPage(IdeaStatus.NOTIFIED, null, null, PageRequest.of(0, 10))

        assertThat(pendingPage.totalElements).isEqualTo(1)
        assertThat(pendingPage.content[0].title).isEqualTo("Pending")
        assertThat(notifiedPage.totalElements).isEqualTo(1)
        assertThat(notifiedPage.content[0].title).isEqualTo("Notified")
    }

    @Test
    fun `getPage without status filter returns all ideas`() {
        ideaService.save(makeIdea("A"))
        ideaService.save(makeIdea("B"))

        val page = ideaService.getPage(null, null, null, PageRequest.of(0, 10))
        assertThat(page.totalElements).isEqualTo(2)
    }

    // ── Star / unstar ─────────────────────────────────────────────────────────

    @Test
    fun `starIdea increments starCount and returns true on first star`() {
        val saved = ideaService.save(makeIdea())
        val (updated, starred) = ideaService.starIdea(saved.id, "fp-integration-001")
        assertThat(starred).isTrue
        assertThat(updated.starCount).isEqualTo(1)
    }

    @Test
    fun `starIdea returns false without incrementing count on duplicate`() {
        val saved = ideaService.save(makeIdea())
        ideaService.starIdea(saved.id, "fp-integration-001")
        val (updated, starred) = ideaService.starIdea(saved.id, "fp-integration-001")
        assertThat(starred).isFalse
        assertThat(updated.starCount).isEqualTo(1)
    }

    @Test
    fun `two different fingerprints can both star the same idea`() {
        val saved = ideaService.save(makeIdea())
        ideaService.starIdea(saved.id, "fp-integration-001")
        val (updated, starred) = ideaService.starIdea(saved.id, "fp-integration-002")
        assertThat(starred).isTrue
        assertThat(updated.starCount).isEqualTo(2)
    }

    @Test
    fun `unstarIdea decrements starCount and returns true`() {
        val saved = ideaService.save(makeIdea())
        ideaService.starIdea(saved.id, "fp-integration-001")
        val (updated, unstarred) = ideaService.unstarIdea(saved.id, "fp-integration-001")
        assertThat(unstarred).isTrue
        assertThat(updated.starCount).isEqualTo(0)
    }

    @Test
    fun `unstarIdea returns false when fingerprint has not starred`() {
        val saved = ideaService.save(makeIdea())
        val (_, unstarred) = ideaService.unstarIdea(saved.id, "fp-integration-001")
        assertThat(unstarred).isFalse
    }

    @Test
    fun `starCount does not go below zero after multiple unstars`() {
        val saved = ideaService.save(makeIdea())
        val (updated, _) = ideaService.unstarIdea(saved.id, "fp-integration-001")
        assertThat(updated.starCount).isEqualTo(0)
    }
}
