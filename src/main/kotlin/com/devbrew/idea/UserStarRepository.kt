package com.devbrew.idea

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UserStarRepository : JpaRepository<UserStar, Long> {
    fun existsByIdeaIdAndFingerprint(ideaId: Long, fingerprint: String): Boolean
    fun deleteByIdeaIdAndFingerprint(ideaId: Long, fingerprint: String)
}
