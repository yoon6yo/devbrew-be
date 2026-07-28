package com.daybrew.pipeline

import org.springframework.stereotype.Component
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicReference

private data class Snapshot(
    val running: Boolean = false,
    val step: String? = null,
    val stepIndex: Int = 0,
    val totalSteps: Int = 4,
    val detail: String? = null,
    val startedAt: OffsetDateTime? = null,
    val finishedAt: OffsetDateTime? = null,
    val result: String? = null,
    val error: String? = null,
    val lastCollectAt: OffsetDateTime? = null,
    val lastCollectResult: String? = null,
    val lastScoreAt: OffsetDateTime? = null,
    val lastScoreResult: String? = null,
)

data class PipelineStatusDto(
    val running: Boolean,
    val step: String?,
    val stepIndex: Int,
    val totalSteps: Int,
    val detail: String?,
    val startedAt: String?,
    val finishedAt: String?,
    val result: String?,
    val error: String?,
    val lastCollectAt: String?,
    val lastCollectResult: String?,
    val nextCollectAt: String,
    val lastScoreAt: String?,
    val lastScoreResult: String?,
    val nextScoreAt: String,
)

private fun nextDailyUtc(hour: Int, minute: Int): OffsetDateTime {
    val now = OffsetDateTime.now(ZoneOffset.UTC)
    var candidate = now.toLocalDate().atTime(hour, minute).atOffset(ZoneOffset.UTC)
    if (!candidate.isAfter(now)) candidate = candidate.plusDays(1)
    return candidate
}

@Component
class PipelineStatusTracker {
    private val ref = AtomicReference(Snapshot())

    fun get(): PipelineStatusDto = ref.get().let { s ->
        PipelineStatusDto(
            running = s.running,
            step = s.step,
            stepIndex = s.stepIndex,
            totalSteps = s.totalSteps,
            detail = s.detail,
            startedAt = s.startedAt?.toString(),
            finishedAt = s.finishedAt?.toString(),
            result = s.result,
            error = s.error,
            lastCollectAt = s.lastCollectAt?.toString(),
            lastCollectResult = s.lastCollectResult,
            nextCollectAt = nextDailyUtc(0, 0).toString(),
            lastScoreAt = s.lastScoreAt?.toString(),
            lastScoreResult = s.lastScoreResult,
            nextScoreAt = nextDailyUtc(0, 30).toString(),
        )
    }

    fun start(totalSteps: Int = 4): Boolean {
        val fresh = Snapshot(running = true, startedAt = OffsetDateTime.now(), totalSteps = totalSteps)
        while (true) {
            val current = ref.get()
            if (current.running) return false
            if (ref.compareAndSet(current, fresh)) return true
        }
    }

    fun update(step: String, stepIndex: Int, detail: String? = null) {
        ref.updateAndGet { it.copy(step = step, stepIndex = stepIndex, detail = detail) }
    }

    fun finish(result: String? = null, error: String? = null) {
        ref.updateAndGet { it.copy(
            running = false,
            step = if (error == null) "완료" else "오류",
            stepIndex = if (error == null) it.totalSteps else it.stepIndex,
            finishedAt = OffsetDateTime.now(),
            result = result,
            error = error,
        )}
    }

    fun recordCollect(result: String?) {
        ref.updateAndGet { it.copy(lastCollectAt = OffsetDateTime.now(), lastCollectResult = result) }
    }

    fun recordScore(result: String?) {
        ref.updateAndGet { it.copy(lastScoreAt = OffsetDateTime.now(), lastScoreResult = result) }
    }
}
