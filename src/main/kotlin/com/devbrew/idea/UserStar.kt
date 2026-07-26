package com.devbrew.idea

import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
@Table(
    name = "user_stars",
    uniqueConstraints = [UniqueConstraint(columnNames = ["idea_id", "fingerprint"])],
)
class UserStar(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "idea_id", nullable = false)
    val ideaId: Long,

    @Column(nullable = false, length = 64)
    val fingerprint: String,

    @Column(name = "starred_at", nullable = false, updatable = false)
    val starredAt: OffsetDateTime = OffsetDateTime.now(),
)
