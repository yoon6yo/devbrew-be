package com.daybrew.admin

import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
@Table(name = "gemini_usage")
class GeminiUsage(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    val usedAt: OffsetDateTime = OffsetDateTime.now(),
    val operation: String,
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int,
)
