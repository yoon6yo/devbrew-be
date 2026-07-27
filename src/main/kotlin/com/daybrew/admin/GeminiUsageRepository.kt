package com.daybrew.admin

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.OffsetDateTime

data class TokenTotals(val promptTokens: Long, val completionTokens: Long, val totalTokens: Long)

interface GeminiUsageRepository : JpaRepository<GeminiUsage, Long> {

    @Query("""
        SELECT new com.daybrew.admin.TokenTotals(
            COALESCE(SUM(g.promptTokens), 0),
            COALESCE(SUM(g.completionTokens), 0),
            COALESCE(SUM(g.totalTokens), 0)
        ) FROM GeminiUsage g WHERE g.usedAt >= :from
    """)
    fun sumTokensSince(from: OffsetDateTime): TokenTotals

    @Query("SELECT COALESCE(SUM(g.totalTokens), 0) FROM GeminiUsage g WHERE g.usedAt >= :from")
    fun sumTotalTokensSince(from: OffsetDateTime): Long
}
