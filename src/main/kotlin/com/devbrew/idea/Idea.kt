package com.devbrew.idea

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

    @Column(name = "score_reason", columnDefinition = "TEXT")
    var scoreReason: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    var status: IdeaStatus = IdeaStatus.PENDING,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now(),
)

enum class SourceTrack { SAAS, GITHUB, VIRAL }

enum class IdeaStatus { PENDING, SCORED, NOTIFIED, REJECTED }
