package com.daybrew.admin

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.OffsetDateTime

interface GeminiUsageRepository : JpaRepository<GeminiUsage, Long> {

    @Query("SELECT COALESCE(SUM(g.promptTokens), 0) FROM GeminiUsage g WHERE g.usedAt >= :from")
    fun sumPromptTokensSince(from: OffsetDateTime): Long

    @Query("SELECT COALESCE(SUM(g.completionTokens), 0) FROM GeminiUsage g WHERE g.usedAt >= :from")
    fun sumCompletionTokensSince(from: OffsetDateTime): Long

    @Query("SELECT COALESCE(SUM(g.totalTokens), 0) FROM GeminiUsage g WHERE g.usedAt >= :from")
    fun sumTotalTokensSince(from: OffsetDateTime): Long
}
