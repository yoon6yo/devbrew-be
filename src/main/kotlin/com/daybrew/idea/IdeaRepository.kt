package com.daybrew.idea

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

data class StatusCount(val status: IdeaStatus, val count: Long)

@Repository
interface IdeaRepository : JpaRepository<Idea, Long> {

    fun findByStatus(status: IdeaStatus): List<Idea>

    fun existsBySourceUrlAndSourceTrack(sourceUrl: String, sourceTrack: SourceTrack): Boolean

    fun existsByRawSignalAndSourceTrack(rawSignal: String, sourceTrack: SourceTrack): Boolean

    @Query("SELECT i FROM Idea i ORDER BY i.score DESC NULLS LAST, i.createdAt DESC")
    fun findAllOrderedByScore(): List<Idea>

    fun findByStatus(status: IdeaStatus, pageable: Pageable): Page<Idea>

    @Query("SELECT new com.daybrew.idea.StatusCount(i.status, COUNT(i)) FROM Idea i GROUP BY i.status")
    fun countAllGroupedByStatus(): List<StatusCount>
}
