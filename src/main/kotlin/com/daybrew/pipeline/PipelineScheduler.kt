package com.daybrew.pipeline

import com.daybrew.idea.IdeaService
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
    private val slackNotifier: SlackNotifier,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    // Run daily at 09:00 KST (00:00 UTC)
    @Async
    @Scheduled(cron = "0 0 0 * * *")
    fun runPipeline() = runPipeline(sources = null)

    // Publish top scored ideas daily at midnight KST (15:00 UTC)
    @Async
    @Scheduled(cron = "0 0 15 * * *")
    fun publishTopIdeas() {
        val top = ideaService.getScored()
            .filter { it.score != null }
            .sortedByDescending { it.score }
            .take(3)

        if (top.isEmpty()) {
            log.info("Publish job: no scored ideas to publish")
            return
        }

        top.forEachIndexed { i, idea ->
            if (i > 0) Thread.sleep(1_000)
            runCatching { ideaService.markNotified(idea.id) }
                .onFailure { log.warn("Failed to publish idea ${idea.id}", it) }
            runCatching { slackNotifier.notifyIdea(idea) }
                .onFailure { log.warn("Slack ping failed for idea ${idea.id}", it) }
        }

        log.info("Publish job complete — published ${top.size} ideas")
    }

    fun runPipeline(sources: Set<SourceTrack>?) {
        log.info("Pipeline started — sources={}", sources ?: "ALL")
        val signals = collectors.flatMap { it.collect() }
            .let { all -> if (sources != null) all.filter { it.track in sources } else all }
        log.info("Collected ${signals.size} raw signals")

        val newIdeas = signals
            .filter { signal -> !ideaService.isDuplicate(signal.url, signal.body, signal.track) }
            .mapNotNull { signal ->
                runCatching { ideaGenerator.generate(signal) }
                    .onFailure { log.warn("Generation failed for signal: ${signal.title}", it) }
                    .getOrNull()
            }
            .map { ideaService.save(it) }

        log.info("Saved ${newIdeas.size} new ideas")

        if (newIdeas.isEmpty()) {
            log.info("Pipeline complete — no new ideas to rate")
            return
        }

        val ratings = runCatching { ideaRater.rateAll(newIdeas) }
            .onFailure { log.warn("Batch rating failed", it) }
            .getOrDefault(emptyMap())

        val scored = newIdeas.mapNotNull { idea ->
            val result = ratings[idea.id] ?: return@mapNotNull null
            runCatching { ideaService.updateScore(idea.id, result) }
                .onFailure { log.warn("Score update failed for idea: ${idea.id}", it) }
                .getOrNull()
        }

        log.info("Pipeline complete — scored=${scored.size}")
    }
}
