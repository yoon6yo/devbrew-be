package com.daybrew.pipeline

import com.daybrew.idea.Idea
import com.daybrew.idea.IdeaRepository
import com.daybrew.idea.IdeaService
import com.daybrew.idea.IdeaStatus
import com.daybrew.idea.SourceTrack
import com.daybrew.llm.GeneratedResult
import com.daybrew.llm.IdeaGenerator
import com.daybrew.llm.IdeaRater
import com.daybrew.llm.ScoreResult
import com.daybrew.pipeline.collector.IdeaCollector
import com.daybrew.pipeline.collector.RawSignal
import com.daybrew.slack.SlackNotifier
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PipelineSchedulerTest {

    private val collector = mockk<IdeaCollector>()
    private val ideaGenerator = mockk<IdeaGenerator>()
    private val ideaRater = mockk<IdeaRater>()
    private val ideaService = mockk<IdeaService>()
    private val ideaRepository = mockk<IdeaRepository>()
    private val slackNotifier = mockk<SlackNotifier>()

    private lateinit var scheduler: PipelineScheduler

    private val stubIdea = Idea(id = 0L, title = "stub", description = "stub", sourceTrack = SourceTrack.SAAS)

    @BeforeEach
    fun setUp() {
        scheduler = PipelineScheduler(
            listOf(collector), ideaGenerator, ideaRater, ideaService, ideaRepository,
            slackNotifier, PipelineStatusTracker(),
        )
        // Default stubs so tests only override what they specifically test
        every { ideaRepository.findTitlesByCreatedAtAfter(any()) } returns emptyList()
        every { ideaService.getPending() } returns emptyList()
        every { ideaService.markScoring(any()) } returns stubIdea
        every { ideaService.revertScoringToPending(any()) } returns stubIdea
    }

    private fun score(s: Int, reason: String) = ScoreResult(
        score = s.toShort(), marketFit = s.toShort(), novelty = s.toShort(),
        feasibility = s.toShort(), monetization = s.toShort(), trend = s.toShort(),
        reason = reason,
    )

    // ── runPipeline ───────────────────────────────────────────────────────────

    @Test
    fun `saves and scores idea through full pipeline`() {
        val signal = RawSignal(title = "Signal", body = "Body", url = "https://example.com", track = SourceTrack.SAAS)
        val rawIdea = Idea(id = 0L, title = "Raw Idea", description = "Desc", sourceTrack = SourceTrack.SAAS, sourceUrl = "https://example.com")
        val savedIdea = Idea(id = 1L, title = "Raw Idea", description = "Desc", sourceTrack = SourceTrack.SAAS)
        val scoredIdea = Idea(id = 1L, title = "Raw Idea", description = "Desc", sourceTrack = SourceTrack.SAAS, score = 8, status = IdeaStatus.SCORED)

        every { collector.collect() } returns listOf(signal)
        every { ideaService.isDuplicate("https://example.com", "Body", SourceTrack.SAAS) } returns false
        every { ideaGenerator.generateBatch(listOf(signal)) } returns listOf(GeneratedResult(rawIdea, null))
        every { ideaService.save(rawIdea) } returns savedIdea
        every { ideaService.getPending() } returns listOf(savedIdea)
        every { ideaService.markScoring(1L) } returns savedIdea
        every { ideaRater.rateAll(listOf(savedIdea)) } returns mapOf(1L to score(8, "Good"))
        every { ideaService.updateScore(1L, score(8, "Good")) } returns scoredIdea

        scheduler.runPipeline()

        verify(exactly = 1) { ideaService.save(rawIdea) }
        verify(exactly = 1) { ideaService.markScoring(1L) }
        verify(exactly = 1) { ideaRater.rateAll(listOf(savedIdea)) }
        verify(exactly = 1) { ideaService.updateScore(1L, score(8, "Good")) }
    }

    @Test
    fun `ideas with no Gemini rating revert to PENDING`() {
        val signal = RawSignal(title = "S", body = "B", url = null, track = SourceTrack.SAAS)
        val rawIdea = Idea(id = 0L, title = "I", description = "D", sourceTrack = SourceTrack.SAAS)
        val savedIdea = Idea(id = 1L, title = "I", description = "D", sourceTrack = SourceTrack.SAAS)

        every { collector.collect() } returns listOf(signal)
        every { ideaService.isDuplicate(null, "B", SourceTrack.SAAS) } returns false
        every { ideaGenerator.generateBatch(listOf(signal)) } returns listOf(GeneratedResult(rawIdea, null))
        every { ideaService.save(rawIdea) } returns savedIdea
        every { ideaService.getPending() } returns listOf(savedIdea)
        every { ideaService.markScoring(1L) } returns savedIdea
        every { ideaRater.rateAll(listOf(savedIdea)) } returns emptyMap()
        every { ideaService.revertScoringToPending(1L) } returns savedIdea

        scheduler.runPipeline()

        verify(exactly = 0) { ideaService.updateScore(any(), any()) }
        verify(exactly = 1) { ideaService.revertScoringToPending(1L) }
    }

    @Test
    fun `skips duplicate signals before generation`() {
        val signal = RawSignal(title = "Dup Signal", body = "Body", url = "https://dup.com", track = SourceTrack.SAAS)

        every { collector.collect() } returns listOf(signal)
        every { ideaService.isDuplicate("https://dup.com", "Body", SourceTrack.SAAS) } returns true

        scheduler.runPipeline()

        verify(exactly = 0) { ideaService.save(any()) }
        verify(exactly = 0) { ideaRater.rateAll(any()) }
    }

    @Test
    fun `falls back to individual generation when batch fails`() {
        val goodSignal = RawSignal(title = "Good", body = "Body", url = null, track = SourceTrack.SAAS)
        val badSignal = RawSignal(title = "Bad", body = "Body", url = null, track = SourceTrack.VIRAL)
        val goodIdea = Idea(id = 0L, title = "Good Idea", description = "Desc", sourceTrack = SourceTrack.SAAS)
        val savedIdea = Idea(id = 3L, title = "Good Idea", description = "Desc", sourceTrack = SourceTrack.SAAS)

        every { collector.collect() } returns listOf(goodSignal, badSignal)
        every { ideaService.isDuplicate(null, "Body", SourceTrack.SAAS) } returns false
        every { ideaService.isDuplicate(null, "Body", SourceTrack.VIRAL) } returns false
        every { ideaGenerator.generateBatch(any()) } throws RuntimeException("batch failed")
        every { ideaGenerator.generate(goodSignal) } returns GeneratedResult(goodIdea, null)
        every { ideaGenerator.generate(badSignal) } throws RuntimeException("Gemini error")
        every { ideaService.save(goodIdea) } returns savedIdea
        every { ideaService.getPending() } returns listOf(savedIdea)
        every { ideaService.markScoring(3L) } returns savedIdea
        every { ideaRater.rateAll(listOf(savedIdea)) } returns emptyMap()
        every { ideaService.revertScoringToPending(3L) } returns savedIdea

        scheduler.runPipeline()

        verify(exactly = 1) { ideaService.save(goodIdea) }
    }

    @Test
    fun `pipeline completes gracefully when rating fails`() {
        val signal = RawSignal(title = "S", body = "B", url = null, track = SourceTrack.SAAS)
        val rawIdea = Idea(id = 0L, title = "I", description = "D", sourceTrack = SourceTrack.SAAS)
        val savedIdea = Idea(id = 5L, title = "I", description = "D", sourceTrack = SourceTrack.SAAS)

        every { collector.collect() } returns listOf(signal)
        every { ideaService.isDuplicate(null, "B", SourceTrack.SAAS) } returns false
        every { ideaGenerator.generateBatch(listOf(signal)) } returns listOf(GeneratedResult(rawIdea, null))
        every { ideaService.save(rawIdea) } returns savedIdea
        every { ideaService.getPending() } returns listOf(savedIdea)
        every { ideaService.markScoring(5L) } returns savedIdea
        every { ideaRater.rateAll(listOf(savedIdea)) } throws RuntimeException("API down")
        every { ideaService.revertScoringToPending(5L) } returns savedIdea

        scheduler.runPipeline()

        verify(exactly = 0) { ideaService.updateScore(any(), any()) }
    }

    @Test
    fun `empty signal list skips all pipeline steps`() {
        every { collector.collect() } returns emptyList()

        scheduler.runPipeline()

        verify(exactly = 0) { ideaService.save(any()) }
        verify(exactly = 0) { ideaRater.rateAll(any()) }
        verify(exactly = 0) { slackNotifier.notifyIdea(any()) }
    }

    // ── publishTopIdeas ───────────────────────────────────────────────────────

    @Test
    fun `publishTopIdeas notifies scored ideas at or above threshold`() {
        val idea = Idea(id = 1L, title = "I", description = "D", sourceTrack = SourceTrack.SAAS, score = 7, status = IdeaStatus.SCORED)
        val notified = Idea(id = 1L, title = "I", description = "D", sourceTrack = SourceTrack.SAAS, score = 7, status = IdeaStatus.NOTIFIED)

        every { ideaService.getScored() } returns listOf(idea)
        every { ideaService.markNotified(1L) } returns notified
        every { slackNotifier.notifyIdea(idea) } just Runs

        scheduler.publishTopIdeas()

        verify(exactly = 1) { ideaService.markNotified(1L) }
        verify(exactly = 1) { slackNotifier.notifyIdea(idea) }
    }

    @Test
    fun `publishTopIdeas does not notify ideas below score threshold`() {
        val lowIdea = Idea(id = 2L, title = "I", description = "D", sourceTrack = SourceTrack.SAAS, score = 5, status = IdeaStatus.SCORED)

        every { ideaService.getScored() } returns listOf(lowIdea)

        scheduler.publishTopIdeas()

        verify(exactly = 0) { slackNotifier.notifyIdea(any()) }
        verify(exactly = 0) { ideaService.markNotified(any()) }
    }

    @Test
    fun `publishTopIdeas publishes at most 3 top-scored ideas`() {
        val ideas = (1..5).map { i ->
            Idea(id = i.toLong(), title = "I$i", description = "D", sourceTrack = SourceTrack.SAAS, score = (i + 5).toShort(), status = IdeaStatus.SCORED)
        }
        every { ideaService.getScored() } returns ideas
        every { ideaService.markNotified(any()) } returns ideas[0]
        every { slackNotifier.notifyIdea(any()) } just Runs

        scheduler.publishTopIdeas()

        verify(exactly = 3) { ideaService.markNotified(any()) }
        verify(exactly = 3) { slackNotifier.notifyIdea(any()) }
    }

    @Test
    fun `publishTopIdeas does nothing when no scored ideas`() {
        every { ideaService.getScored() } returns emptyList()

        scheduler.publishTopIdeas()

        verify(exactly = 0) { slackNotifier.notifyIdea(any()) }
        verify(exactly = 0) { ideaService.markNotified(any()) }
    }

    @Test
    fun `publishTopIdeas marks notified even when slack fails`() {
        val idea = Idea(id = 6L, title = "I", description = "D", sourceTrack = SourceTrack.SAAS, score = 9, status = IdeaStatus.SCORED)
        val notified = Idea(id = 6L, title = "I", description = "D", sourceTrack = SourceTrack.SAAS, score = 9, status = IdeaStatus.NOTIFIED)

        every { ideaService.getScored() } returns listOf(idea)
        every { ideaService.markNotified(6L) } returns notified
        every { slackNotifier.notifyIdea(idea) } throws RuntimeException("Slack unreachable")

        scheduler.publishTopIdeas()

        verify(exactly = 1) { ideaService.markNotified(6L) }
    }
}
