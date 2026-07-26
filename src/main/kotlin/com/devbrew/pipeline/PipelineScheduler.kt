package com.devbrew.pipeline

import com.devbrew.idea.Idea
import com.devbrew.idea.IdeaService
import com.devbrew.idea.IdeaStatus
import com.devbrew.llm.IdeaGenerator
import com.devbrew.llm.IdeaRater
import com.devbrew.pipeline.collector.IdeaCollector
import com.devbrew.slack.SlackNotifier
import org.slf4j.LoggerFactory
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
    @Scheduled(cron = "0 0 0 * * *")
    fun runPipeline() {
        log.info("Pipeline started")
        val signals = collectors.flatMap { it.collect() }
        log.info("Collected ${signals.size} raw signals")

        val newIdeas = signals
            .filter { signal ->
                !ideaService.isDuplicate(signal.url, signal.body, signal.track)
            }
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

        val topIdeas = scored.filter { it.score != null && it.score!! >= 7 && it.status == IdeaStatus.SCORED }
        if (topIdeas.isNotEmpty()) {
            notifyTopIdeas(topIdeas)
        }

        log.info("Pipeline complete — scored=${scored.size}, notified=${topIdeas.size}")
    }

    private fun notifyTopIdeas(ideas: List<Idea>) {
        ideas.forEach { idea ->
            val sent = runCatching { slackNotifier.notifyIdea(idea) }
                .onFailure { log.warn("Slack notification failed for idea: ${idea.id}", it) }
                .isSuccess
            if (sent) {
                runCatching { ideaService.markNotified(idea.id) }
                    .onFailure { log.warn("Failed to mark idea ${idea.id} as notified", it) }
            }
        }
    }
}
