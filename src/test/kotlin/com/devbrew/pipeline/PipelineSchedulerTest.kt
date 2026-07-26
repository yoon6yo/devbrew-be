package com.devbrew.pipeline

import com.devbrew.idea.Idea
import com.devbrew.idea.IdeaService
import com.devbrew.idea.IdeaStatus
import com.devbrew.idea.SourceTrack
import com.devbrew.llm.IdeaGenerator
import com.devbrew.llm.IdeaRater
import com.devbrew.pipeline.collector.IdeaCollector
import com.devbrew.pipeline.collector.RawSignal
import com.devbrew.slack.SlackNotifier
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
    private val slackNotifier = mockk<SlackNotifier>()

    private lateinit var scheduler: PipelineScheduler

    @BeforeEach
    fun setUp() {
        scheduler = PipelineScheduler(listOf(collector), ideaGenerator, ideaRater, ideaService, slackNotifier)
    }

    @Test
    fun `runs full pipeline and notifies top-scored ideas`() {
        val signal = RawSignal(title = "Signal", body = "Body", url = "https://example.com", track = SourceTrack.SAAS)
        val rawIdea = Idea(id = 0L, title = "Raw Idea", description = "Desc", sourceTrack = SourceTrack.SAAS, sourceUrl = "https://example.com")
        val savedIdea = Idea(id = 1L, title = "Raw Idea", description = "Desc", sourceTrack = SourceTrack.SAAS, sourceUrl = "https://example.com")
        val scoredIdea = Idea(id = 1L, title = "Raw Idea", description = "Desc", sourceTrack = SourceTrack.SAAS, score = 8, scoreReason = "Good", status = IdeaStatus.SCORED)
        val notifiedIdea = Idea(id = 1L, title = "Raw Idea", description = "Desc", sourceTrack = SourceTrack.SAAS, score = 8, status = IdeaStatus.NOTIFIED)

        every { collector.collect() } returns listOf(signal)
        every { ideaService.isDuplicate("https://example.com", "Body", SourceTrack.SAAS) } returns false
        every { ideaGenerator.generate(signal) } returns rawIdea
        every { ideaService.save(rawIdea) } returns savedIdea
        every { ideaRater.rateAll(listOf(savedIdea)) } returns mapOf(1L to (8.toShort() to "Good"))
        every { ideaService.updateScore(1L, 8.toShort(), "Good") } returns scoredIdea
        every { slackNotifier.notifyIdea(scoredIdea) } just Runs
        every { ideaService.markNotified(1L) } returns notifiedIdea

        scheduler.runPipeline()

        verify(exactly = 1) { ideaRater.rateAll(listOf(savedIdea)) }
        verify(exactly = 1) { slackNotifier.notifyIdea(scoredIdea) }
        verify(exactly = 1) { ideaService.markNotified(1L) }
    }

    @Test
    fun `skips duplicate signals before generation`() {
        val signal = RawSignal(title = "Dup Signal", body = "Body", url = "https://dup.com", track = SourceTrack.SAAS)

        every { collector.collect() } returns listOf(signal)
        every { ideaService.isDuplicate("https://dup.com", "Body", SourceTrack.SAAS) } returns true

        scheduler.runPipeline()

        verify(exactly = 0) { ideaGenerator.generate(any()) }
        verify(exactly = 0) { ideaRater.rateAll(any()) }
    }

    @Test
    fun `does not notify ideas scored below threshold of 7`() {
        val signal = RawSignal(title = "Low Signal", body = "Body", url = null, track = SourceTrack.GITHUB)
        val rawIdea = Idea(id = 0L, title = "Low Idea", description = "Desc", sourceTrack = SourceTrack.GITHUB)
        val savedIdea = Idea(id = 2L, title = "Low Idea", description = "Desc", sourceTrack = SourceTrack.GITHUB)
        val scoredIdea = Idea(id = 2L, title = "Low Idea", description = "Desc", sourceTrack = SourceTrack.GITHUB, score = 5, status = IdeaStatus.SCORED)

        every { collector.collect() } returns listOf(signal)
        every { ideaService.isDuplicate(null, "Body", SourceTrack.GITHUB) } returns false
        every { ideaGenerator.generate(signal) } returns rawIdea
        every { ideaService.save(rawIdea) } returns savedIdea
        every { ideaRater.rateAll(listOf(savedIdea)) } returns mapOf(2L to (5.toShort() to "Average"))
        every { ideaService.updateScore(2L, 5.toShort(), "Average") } returns scoredIdea

        scheduler.runPipeline()

        verify(exactly = 0) { slackNotifier.notifyIdea(any()) }
    }

    @Test
    fun `continues pipeline when generator fails for one signal`() {
        val goodSignal = RawSignal(title = "Good", body = "Body", url = null, track = SourceTrack.SAAS)
        val badSignal = RawSignal(title = "Bad", body = "Body", url = null, track = SourceTrack.VIRAL)
        val goodIdea = Idea(id = 0L, title = "Good Idea", description = "Desc", sourceTrack = SourceTrack.SAAS)
        val savedIdea = Idea(id = 3L, title = "Good Idea", description = "Desc", sourceTrack = SourceTrack.SAAS)

        every { collector.collect() } returns listOf(goodSignal, badSignal)
        every { ideaService.isDuplicate(null, "Body", SourceTrack.SAAS) } returns false
        every { ideaService.isDuplicate(null, "Body", SourceTrack.VIRAL) } returns false
        every { ideaGenerator.generate(goodSignal) } returns goodIdea
        every { ideaGenerator.generate(badSignal) } throws RuntimeException("Gemini error")
        every { ideaService.save(goodIdea) } returns savedIdea
        every { ideaRater.rateAll(listOf(savedIdea)) } returns emptyMap()

        scheduler.runPipeline()

        verify(exactly = 1) { ideaService.save(goodIdea) }
        verify(exactly = 0) { slackNotifier.notifyIdea(any()) }
    }

    @Test
    fun `does not notify when rating batch fails entirely`() {
        val signal = RawSignal(title = "S", body = "B", url = null, track = SourceTrack.SAAS)
        val rawIdea = Idea(id = 0L, title = "I", description = "D", sourceTrack = SourceTrack.SAAS)
        val savedIdea = Idea(id = 5L, title = "I", description = "D", sourceTrack = SourceTrack.SAAS)

        every { collector.collect() } returns listOf(signal)
        every { ideaService.isDuplicate(null, "B", SourceTrack.SAAS) } returns false
        every { ideaGenerator.generate(signal) } returns rawIdea
        every { ideaService.save(rawIdea) } returns savedIdea
        every { ideaRater.rateAll(listOf(savedIdea)) } throws RuntimeException("Claude API down")

        scheduler.runPipeline()

        verify(exactly = 0) { slackNotifier.notifyIdea(any()) }
        verify(exactly = 0) { ideaService.markNotified(any()) }
    }

    @Test
    fun `slack failure does not mark idea as notified`() {
        val signal = RawSignal(title = "S", body = "B", url = "https://ex.com", track = SourceTrack.SAAS)
        val rawIdea = Idea(id = 0L, title = "I", description = "D", sourceTrack = SourceTrack.SAAS)
        val savedIdea = Idea(id = 6L, title = "I", description = "D", sourceTrack = SourceTrack.SAAS)
        val scoredIdea = Idea(id = 6L, title = "I", description = "D", sourceTrack = SourceTrack.SAAS, score = 9, status = IdeaStatus.SCORED)

        every { collector.collect() } returns listOf(signal)
        every { ideaService.isDuplicate("https://ex.com", "B", SourceTrack.SAAS) } returns false
        every { ideaGenerator.generate(signal) } returns rawIdea
        every { ideaService.save(rawIdea) } returns savedIdea
        every { ideaRater.rateAll(listOf(savedIdea)) } returns mapOf(6L to (9.toShort() to "Excellent"))
        every { ideaService.updateScore(6L, 9.toShort(), "Excellent") } returns scoredIdea
        every { slackNotifier.notifyIdea(scoredIdea) } throws RuntimeException("Slack unreachable")

        scheduler.runPipeline()

        verify(exactly = 0) { ideaService.markNotified(any()) }
    }

    @Test
    fun `idea with score exactly 7 is notified`() {
        val signal = RawSignal(title = "S", body = "B", url = null, track = SourceTrack.GITHUB)
        val rawIdea = Idea(id = 0L, title = "I", description = "D", sourceTrack = SourceTrack.GITHUB)
        val savedIdea = Idea(id = 7L, title = "I", description = "D", sourceTrack = SourceTrack.GITHUB)
        val scoredIdea = Idea(id = 7L, title = "I", description = "D", sourceTrack = SourceTrack.GITHUB, score = 7, status = IdeaStatus.SCORED)
        val notifiedIdea = Idea(id = 7L, title = "I", description = "D", sourceTrack = SourceTrack.GITHUB, score = 7, status = IdeaStatus.NOTIFIED)

        every { collector.collect() } returns listOf(signal)
        every { ideaService.isDuplicate(null, "B", SourceTrack.GITHUB) } returns false
        every { ideaGenerator.generate(signal) } returns rawIdea
        every { ideaService.save(rawIdea) } returns savedIdea
        every { ideaRater.rateAll(listOf(savedIdea)) } returns mapOf(7L to (7.toShort() to "Threshold"))
        every { ideaService.updateScore(7L, 7.toShort(), "Threshold") } returns scoredIdea
        every { slackNotifier.notifyIdea(scoredIdea) } just Runs
        every { ideaService.markNotified(7L) } returns notifiedIdea

        scheduler.runPipeline()

        verify(exactly = 1) { slackNotifier.notifyIdea(scoredIdea) }
        verify(exactly = 1) { ideaService.markNotified(7L) }
    }

    @Test
    fun `idea with score 6 is not notified`() {
        val signal = RawSignal(title = "S", body = "B", url = null, track = SourceTrack.VIRAL)
        val rawIdea = Idea(id = 0L, title = "I", description = "D", sourceTrack = SourceTrack.VIRAL)
        val savedIdea = Idea(id = 8L, title = "I", description = "D", sourceTrack = SourceTrack.VIRAL)
        val scoredIdea = Idea(id = 8L, title = "I", description = "D", sourceTrack = SourceTrack.VIRAL, score = 6, status = IdeaStatus.SCORED)

        every { collector.collect() } returns listOf(signal)
        every { ideaService.isDuplicate(null, "B", SourceTrack.VIRAL) } returns false
        every { ideaGenerator.generate(signal) } returns rawIdea
        every { ideaService.save(rawIdea) } returns savedIdea
        every { ideaRater.rateAll(listOf(savedIdea)) } returns mapOf(8L to (6.toShort() to "Below threshold"))
        every { ideaService.updateScore(8L, 6.toShort(), "Below threshold") } returns scoredIdea

        scheduler.runPipeline()

        verify(exactly = 0) { slackNotifier.notifyIdea(any()) }
        verify(exactly = 0) { ideaService.markNotified(any()) }
    }

    @Test
    fun `empty signal list skips all pipeline steps`() {
        every { collector.collect() } returns emptyList()

        scheduler.runPipeline()

        verify(exactly = 0) { ideaGenerator.generate(any()) }
        verify(exactly = 0) { ideaRater.rateAll(any()) }
        verify(exactly = 0) { slackNotifier.notifyIdea(any()) }
    }

    @Test
    fun `NOTIFIED status guard prevents re-notification of already-notified ideas`() {
        val signal = RawSignal(title = "S", body = "B", url = null, track = SourceTrack.SAAS)
        val rawIdea = Idea(id = 0L, title = "I", description = "D", sourceTrack = SourceTrack.SAAS)
        val savedIdea = Idea(id = 9L, title = "I", description = "D", sourceTrack = SourceTrack.SAAS)
        // updateScore returns NOTIFIED (edge case — should not happen normally but guard must hold)
        val alreadyNotifiedIdea = Idea(id = 9L, title = "I", description = "D", sourceTrack = SourceTrack.SAAS, score = 9, status = IdeaStatus.NOTIFIED)

        every { collector.collect() } returns listOf(signal)
        every { ideaService.isDuplicate(null, "B", SourceTrack.SAAS) } returns false
        every { ideaGenerator.generate(signal) } returns rawIdea
        every { ideaService.save(rawIdea) } returns savedIdea
        every { ideaRater.rateAll(listOf(savedIdea)) } returns mapOf(9L to (9.toShort() to "High"))
        every { ideaService.updateScore(9L, 9.toShort(), "High") } returns alreadyNotifiedIdea

        scheduler.runPipeline()

        // status == NOTIFIED, not SCORED → excluded from topIdeas → not re-notified
        verify(exactly = 0) { slackNotifier.notifyIdea(any()) }
    }
}
