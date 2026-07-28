package com.daybrew.pipeline

import com.daybrew.idea.IdeaRepository
import com.daybrew.idea.IdeaService
import com.daybrew.idea.IdeaStatus
import com.daybrew.idea.SourceTrack
import com.daybrew.llm.GeneratedResult
import com.daybrew.llm.IdeaGenerator
import com.daybrew.llm.IdeaRater
import com.daybrew.pipeline.collector.IdeaCollector
import com.daybrew.pipeline.collector.RawSignal
import com.daybrew.slack.SlackNotifier
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.OffsetDateTime

@Component
class PipelineScheduler(
    private val collectors: List<IdeaCollector>,
    private val ideaGenerator: IdeaGenerator,
    private val ideaRater: IdeaRater,
    private val ideaService: IdeaService,
    private val ideaRepository: IdeaRepository,
    private val slackNotifier: SlackNotifier,
    private val statusTracker: PipelineStatusTracker,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    // Collect + generate at 09:00 KST (00:00 UTC)
    @Async
    @Scheduled(cron = "0 0 0 * * *")
    fun scheduledCollect() = runCollect(sources = null)

    // Score all PENDING at 09:30 KST (00:30 UTC) — runs after collect completes
    @Async
    @Scheduled(cron = "0 30 0 * * *")
    fun scheduledScore() = runScore()

    // Publish top scored ideas at midnight KST (15:00 UTC): SCORED → NOTIFIED → FEATURED
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

        val featured = runCatching { ideaService.selectDailyTopFeatured() }
            .onFailure { log.warn("Feature selection failed", it) }
            .getOrDefault(emptyList())
        log.info("Feature job complete — featured ${featured.size} ideas")
    }

    // Recover ideas stuck in SCORING for > 12 h — 06:00 UTC (15:00 KST), retry up to 3 times
    @Async
    @Scheduled(cron = "0 0 6 * * *")
    fun recoverStuckScoringIdeas() = doRecoverStuck()

    @PostConstruct
    fun recoverOnStartup() {
        log.info("Startup recovery: checking for stuck SCORING ideas")
        doRecoverStuck()
    }

    private fun doRecoverStuck() {
        val cutoff = OffsetDateTime.now().minusHours(12)
        val stuck = ideaRepository.findByStatusAndUpdatedAtBefore(IdeaStatus.SCORING, cutoff)
        if (stuck.isEmpty()) return
        log.warn("Recovery: found ${stuck.size} stuck SCORING ideas (updatedAt < $cutoff)")
        stuck.forEach { idea ->
            runCatching {
                if (idea.scoreRetryCount >= 3) {
                    ideaService.rejectStuckIdea(idea.id)
                    log.warn("Recovery: idea ${idea.id} rejected after ${idea.scoreRetryCount} retries")
                } else {
                    ideaService.requeueStuckIdea(idea.id)
                    log.info("Recovery: idea ${idea.id} re-queued (retry #${idea.scoreRetryCount + 1})")
                }
            }.onFailure { log.error("Recovery failed for idea ${idea.id}", it) }
        }
    }

    // Hard-delete REJECTED ideas older than 1 year — 03:30 UTC (12:30 KST)
    @Async
    @Scheduled(cron = "0 30 3 * * *")
    fun hardDeleteRejected() {
        val cutoff = OffsetDateTime.now().minusYears(1)
        val deleted = ideaRepository.deleteByStatusAndUpdatedAtBefore(IdeaStatus.REJECTED, cutoff)
        log.info("Hard-delete job: removed $deleted REJECTED ideas older than 1 year")
    }

    // ── Manual triggers ──────────────────────────────────────────────────────

    /** Full pipeline (collect + score) for backward compat with existing /trigger endpoint */
    @Async
    fun triggerAsync(sources: Set<SourceTrack>?) = runPipeline(sources)

    @Async
    fun triggerCollectAsync(sources: Set<SourceTrack>?) = runCollect(sources)

    @Async
    fun triggerScoreAsync() = runScore()

    // ── Public step runners ──────────────────────────────────────────────────

    fun runCollect(sources: Set<SourceTrack>?) {
        if (!statusTracker.start(totalSteps = 3)) {
            log.warn("Pipeline already running — skipping collect trigger (sources={})", sources ?: "ALL")
            return
        }
        try {
            val saved = executeCollect(sources)
            val result = if (saved > 0) "신규 ${saved}개 생성 — 채점 대기중" else "신규 아이디어 없음"
            statusTracker.finish(result = result)
            statusTracker.recordCollect(result)
        } catch (e: Exception) {
            log.error("Collect step failed", e)
            val errMsg = e.message ?: "알 수 없는 오류"
            statusTracker.finish(error = errMsg)
            statusTracker.recordCollect("오류: $errMsg")
        }
    }

    fun runScore() {
        if (!statusTracker.start(totalSteps = 1)) {
            log.warn("Pipeline already running — skipping score trigger")
            return
        }
        try {
            val scored = executeScore()
            val result = if (scored > 0) "채점 완료 ${scored}개" else "채점할 아이디어 없음"
            statusTracker.finish(result = result)
            statusTracker.recordScore(result)
        } catch (e: Exception) {
            log.error("Score step failed", e)
            val errMsg = e.message ?: "알 수 없는 오류"
            statusTracker.finish(error = errMsg)
            statusTracker.recordScore("오류: $errMsg")
        }
    }

    fun runPipeline(sources: Set<SourceTrack>?) {
        if (!statusTracker.start(totalSteps = 4)) {
            log.warn("Pipeline already running — skipping trigger (sources={})", sources ?: "ALL")
            return
        }
        try {
            val saved = executeCollect(sources)
            if (saved == 0) {
                statusTracker.finish(result = "신규 아이디어 없음")
                return
            }
            val scored = executeScore()
            statusTracker.finish(result = "신규 ${saved}개, 채점 ${scored}개")
        } catch (e: Exception) {
            log.error("Pipeline failed", e)
            statusTracker.finish(error = e.message ?: "알 수 없는 오류")
        }
    }

    // ── Private step implementations ─────────────────────────────────────────

    private fun executeCollect(sources: Set<SourceTrack>?): Int {
        log.info("Collect started — sources={}", sources ?: "ALL")

        statusTracker.update("신호 수집", 1, "소스 ${collectors.size}개 수집 시작…")
        val allSignals = mutableListOf<RawSignal>()
        collectors.forEach { collector ->
            val name = collector.javaClass.simpleName.removeSuffix("Collector")
            runCatching { collector.collect() }
                .onSuccess { fetched ->
                    allSignals += fetched
                    statusTracker.update("신호 수집", 1, "$name ${fetched.size}개 → 누적 ${allSignals.size}개")
                    log.info("$name: collected ${fetched.size} signals")
                }
                .onFailure { e ->
                    log.warn("Collector $name failed", e)
                    statusTracker.update("신호 수집", 1, "$name 실패 — 누적 ${allSignals.size}개")
                }
        }
        val signals = allSignals
            .let { all -> if (sources != null) all.filter { it.track in sources } else all }
        log.info("Collected ${signals.size} raw signals")

        statusTracker.update("중복 제거", 2, "${signals.size}개 신호 중복 제거 중…")
        val uniqueSignals = signals.filter { signal ->
            !ideaService.isDuplicate(signal.url, signal.body, signal.track)
        }
        log.info("Unique signals after URL/body dedup: ${uniqueSignals.size}")

        if (uniqueSignals.isEmpty()) {
            log.info("No unique signals — collect complete with 0 new ideas")
            return 0
        }

        statusTracker.update("아이디어 생성", 3, "${uniqueSignals.size}개 신호 → Gemini 생성 중…")
        val results = runCatching { ideaGenerator.generateBatch(uniqueSignals) }
            .onFailure { log.warn("Batch generation failed, retrying individually", it) }
            .getOrElse {
                uniqueSignals.mapNotNull { signal ->
                    runCatching { ideaGenerator.generate(signal) }
                        .onFailure { log.warn("Generation failed for signal: ${signal.title}", it) }
                        .getOrNull()
                }
            }

        val recentTitles = ideaRepository.findTitlesByCreatedAtAfter(OffsetDateTime.now().minusDays(90))
        val conceptDeduped = deduplicateByTitle(results, recentTitles)
        log.info("Concept dedup: ${conceptDeduped.size}/${results.size} kept")

        var savedCount = 0
        conceptDeduped.forEach { result ->
            runCatching { ideaService.save(result.idea) }
                .onSuccess { savedCount++ }
                .onFailure { log.warn("Save failed for idea: ${result.idea.title}", it) }
        }
        log.info("Saved $savedCount new PENDING ideas")
        return savedCount
    }

    private fun executeScore(): Int {
        val pending = ideaService.getPending()
        log.info("Score started — ${pending.size} PENDING ideas")
        if (pending.isEmpty()) return 0

        // Mark all as SCORING before we call Gemini — enables progress tracking and restart recovery
        pending.forEach { idea ->
            runCatching { ideaService.markScoring(idea.id) }
                .onFailure { log.warn("markScoring failed for idea ${idea.id}", it) }
        }

        statusTracker.update("채점", 1, "${pending.size}개 아이디어 채점 중…")
        val ratings = runCatching { ideaRater.rateAll(pending) }
            .onFailure { log.warn("Batch rating failed", it) }
            .getOrDefault(emptyMap())

        var scoredCount = 0
        pending.forEach { idea ->
            val result = ratings[idea.id]
            if (result == null) {
                // Gemini returned no result for this idea — revert to PENDING for next cycle
                runCatching { ideaService.revertScoringToPending(idea.id) }
                    .onFailure { log.warn("revertScoringToPending failed for idea ${idea.id}", it) }
                return@forEach
            }
            runCatching { ideaService.updateScore(idea.id, result) }
                .onSuccess { scoredCount++ }
                .onFailure {
                    log.warn("Score update failed for idea: ${idea.id}", it)
                    runCatching { ideaService.revertScoringToPending(idea.id) }
                }
        }
        log.info("Scored $scoredCount/${pending.size} ideas")
        return scoredCount
    }

    // ── Concept dedup ─────────────────────────────────────────────────────────

    private fun titleJaccard(a: String, b: String): Double {
        val re = Regex("[\\s·,.!?()\\[\\]/]+")
        val tokA = a.lowercase().split(re).filter { it.length >= 2 }.toSet()
        val tokB = b.lowercase().split(re).filter { it.length >= 2 }.toSet()
        if (tokA.isEmpty() || tokB.isEmpty()) return 0.0
        val inter = tokA.intersect(tokB).size.toDouble()
        return inter / tokA.union(tokB).size.toDouble()
    }

    private fun deduplicateByTitle(
        results: List<GeneratedResult>,
        existingTitles: List<String>,
        threshold: Double = 0.45,
    ): List<GeneratedResult> {
        val kept = mutableListOf<GeneratedResult>()
        val keptTitles = mutableListOf<String>()
        for (result in results) {
            val title = result.idea.title
            val isDup = existingTitles.any { titleJaccard(it, title) >= threshold }
                || keptTitles.any { titleJaccard(it, title) >= threshold }
            if (isDup) {
                log.info("Concept duplicate skipped: \"$title\"")
            } else {
                kept += result
                keptTitles += title
            }
        }
        return kept
    }
}
