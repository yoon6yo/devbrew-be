package com.devbrew.idea

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface IdeaRepository : JpaRepository<Idea, Long> {

    fun findByStatus(status: IdeaStatus): List<Idea>

    fun existsBySourceUrlAndSourceTrack(sourceUrl: String, sourceTrack: SourceTrack): Boolean

    @Query("SELECT i FROM Idea i ORDER BY i.score DESC NULLS LAST, i.createdAt DESC")
    fun findAllOrderedByScore(): List<Idea>
}
