package com.daybrew.idea

import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
@Table(name = "idea")
class Idea(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false)
    var title: String,

    @Column(nullable = false, columnDefinition = "TEXT")
    var description: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "source_track", nullable = false, length = 50)
    val sourceTrack: SourceTrack,

    @Column(name = "source_url", length = 1024)
    val sourceUrl: String? = null,

    @Column(name = "raw_signal", columnDefinition = "TEXT")
    val rawSignal: String? = null,

    var score: Short? = null,

    @Column(name = "score_market_fit")
    var scoreMarketFit: Short? = null,

    @Column(name = "score_novelty")
    var scoreNovelty: Short? = null,

    @Column(name = "score_feasibility")
    var scoreFeasibility: Short? = null,

    @Column(name = "score_monetization")
    var scoreMonetization: Short? = null,

    @Column(name = "score_trend")
    var scoreTrend: Short? = null,

    @Column(name = "score_reason", columnDefinition = "TEXT")
    var scoreReason: String? = null,

    @Column(columnDefinition = "TEXT")
    var purpose: String? = null,

    @Column(name = "how_it_works", columnDefinition = "TEXT")
    var howItWorks: String? = null,

    @Column(name = "suggested_stack", columnDefinition = "TEXT")
    var suggestedStack: String? = null,

    @Column(name = "implementation_guide", columnDefinition = "TEXT")
    var implementationGuide: String? = null,

    @Column(name = "one_liner", columnDefinition = "TEXT")
    var oneLiner: String? = null,

    @Column(name = "problems", columnDefinition = "TEXT")
    var problems: String? = null,

    @Column(name = "revenue_model", columnDefinition = "TEXT")
    var revenueModel: String? = null,

    @Column(name = "strengths", columnDefinition = "TEXT")
    var strengths: String? = null,

    @Column(name = "risks", columnDefinition = "TEXT")
    var risks: String? = null,

    @Column(name = "star_count", nullable = false)
    var starCount: Int = 0,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    var status: IdeaStatus = IdeaStatus.PENDING,

    @Column(name = "score_retry_count", nullable = false)
    var scoreRetryCount: Int = 0,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now(),
)

enum class SourceTrack { SAAS, GITHUB, VIRAL, HACKERNEWS }

enum class IdeaStatus { PENDING, SCORING, SCORED, NOTIFIED, FEATURED, REJECTED }
