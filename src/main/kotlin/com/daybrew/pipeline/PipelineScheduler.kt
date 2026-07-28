package com.daybrew.pipeline

import com.daybrew.idea.IdeaRepository
import com.daybrew.idea.IdeaService
import com.daybrew.idea.IdeaStatus
import com.daybrew.idea.SourceTrack
import com.daybrew.llm.IdeaGenerator
import com.daybrew.llm.IdeaRater
import com.daybrew.pipeline.collector.IdeaCollector
import com.daybrew.slack.SlackNotifier
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class PipelineScheduler(
    private val collectors: List<IdeaCollector>,
    private val ideaGenerator: IdeaGenerator,
    private val ideaRater: IdeaRater,
    private val ideaService: IdeaService,
    private val ideaRepository: IdeaRepository,
    private val slackNotifier: SlackNotifier,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    // Run daily at 09:00 KST (00:00 UTC)
    @Async
    @Scheduled(cron = "0 0 0 * * *")
    fun runPipeline() = runPipeline(sources = null)

    // Publish top scored ideas daily at midnight KST (15:00 UTC): SCORED → NOTIFIED
    @Async
    @Scheduled(cron = "0 0 15 * * *")
    fun publishTopIdeas() {
        val top = ideaService.getScored()
            .filter { (it.score ?: 0) >= 6 }
            .sortedByDescending { it.score }
            .take(3)

        if (top.isEmpty()) {
            log.info("Publish job: no scored ideas to publish")
        } else {
            top.forEachIndexed { i, idea ->
                if (i > 0) Thread.sleep(1_000)
                runCatching { ideaService.markNotified(idea.id) }
                    .onFailure { log.warn("Failed to notify idea ${idea.id}", it) }
                runCatching { slackNotifier.notifyIdea(idea) }
                    .onFailure { log.warn("Slack ping failed for idea ${idea.id}", it) }
            }
            log.info("Publish job complete — notified ${top.size} ideas")
        }

        // NOTIFIED → FEATURED: select today's top 5 for main page
        val featured = runCatching { ideaService.selectDailyTopFive() }
            .onFailure { log.warn("Feature selection failed", it) }
            .getOrDefault(emptyList())
        log.info("Feature job complete — featured ${featured.size} ideas")
    }

    // Hard-delete REJECTED ideas older than 1 year — runs nightly at 03:30 UTC (12:30 KST)
    @Async
    @Scheduled(cron = "0 30 3 * * *")
    fun hardDeleteRejected() {
        val cutoff = java.time.OffsetDateTime.now().minusYears(1)
        val deleted = ideaRepository.deleteByStatusAndUpdatedAtBefore(IdeaStatus.REJECTED, cutoff)
        log.info("Hard-delete job: removed $deleted REJECTED ideas older than 1 year")
    }

    fun runPipeline(sources: Set<SourceTrack>?) {
        log.info("Pipeline started — sources={}", sources ?: "ALL")
        val signals = collectors.flatMap { it.collect() }
            .let { all -> if (sources != null) all.filter { it.track in sources } else all }
        log.info("Collected ${signals.size} raw signals (pre-filtered by engagement)")

        val uniqueSignals = signals.filter { signal ->
            !ideaService.isDuplicate(signal.url, signal.body, signal.track)
        }
        log.info("Unique signals after dedup: ${uniqueSignals.size}")

        val results = runCatching { ideaGenerator.generateBatch(uniqueSignals) }
            .onFailure { log.warn("Batch generation failed, retrying individually", it) }
            .getOrElse {
                uniqueSignals.mapNotNull { signal ->
                    runCatching { ideaGenerator.generate(signal) }
                        .onFailure { log.warn("Generation failed for signal: ${signal.title}", it) }
                        .getOrNull()
                }
            }

        val savedWithScore = results.map { result ->
            val saved = ideaService.save(result.idea)
            saved to result.score
        }

        log.info("Saved ${savedWithScore.size} new ideas")
        if (savedWithScore.isEmpty()) {
            log.info("Pipeline complete — no new ideas")
            return
        }

        // apply embedded scores; fall back to rateAll only for parse failures
        val needsRating = mutableListOf<com.daybrew.idea.Idea>()
        var scoredCount = 0

        savedWithScore.forEach { (idea, score) ->
            if (score != null) {
                runCatching { ideaService.updateScore(idea.id, score) }
                    .onSuccess { scoredCount++ }
                    .onFailure { log.warn("Score update failed for idea: ${idea.id}", it) }
            } else {
                needsRating += idea
            }
        }

        if (needsRating.isNotEmpty()) {
            log.info("Fallback rating for ${needsRating.size} ideas without embedded score")
            val ratings = runCatching { ideaRater.rateAll(needsRating) }
                .onFailure { log.warn("Fallback batch rating failed", it) }
                .getOrDefault(emptyMap())
            needsRating.forEach { idea ->
                val result = ratings[idea.id] ?: return@forEach
                runCatching { ideaService.updateScore(idea.id, result) }
                    .onSuccess { scoredCount++ }
                    .onFailure { log.warn("Score update failed for idea: ${idea.id}", it) }
            }
        }

        log.info("Pipeline complete — scored=$scoredCount, fallback=${needsRating.size}")
    }
}
