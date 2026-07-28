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

    fun existsBySourceUrlAndSourceTrackAndCreatedAtAfter(sourceUrl: String, sourceTrack: SourceTrack, createdAt: java.time.OffsetDateTime): Boolean

    fun existsByRawSignalAndSourceTrackAndCreatedAtAfter(rawSignal: String, sourceTrack: SourceTrack, createdAt: java.time.OffsetDateTime): Boolean

    @Query("SELECT i FROM Idea i ORDER BY i.score DESC NULLS LAST, i.createdAt DESC")
    fun findAllOrderedByScore(): List<Idea>

    fun findByStatus(status: IdeaStatus, pageable: Pageable): Page<Idea>

    fun findByStatusIn(statuses: Collection<IdeaStatus>, pageable: Pageable): Page<Idea>

    fun findByStatusAndCreatedAtGreaterThanEqual(status: IdeaStatus, from: java.time.OffsetDateTime, pageable: Pageable): Page<Idea>

    fun findByStatusAndCreatedAtLessThan(status: IdeaStatus, before: java.time.OffsetDateTime, pageable: Pageable): Page<Idea>

    fun findByStatusAndUpdatedAtGreaterThanEqual(status: IdeaStatus, from: java.time.OffsetDateTime, pageable: Pageable): Page<Idea>

    @Query("SELECT new com.daybrew.idea.StatusCount(i.status, COUNT(i)) FROM Idea i GROUP BY i.status")
    fun countAllGroupedByStatus(): List<StatusCount>

    fun deleteByStatusAndUpdatedAtBefore(status: IdeaStatus, cutoff: java.time.OffsetDateTime): Int
}
