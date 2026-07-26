package com.devbrew.idea

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface IdeaRepository : JpaRepository<Idea, Long> {

    fun findByStatus(status: IdeaStatus): List<Idea>

    fun existsBySourceUrlAndSourceTrack(sourceUrl: String, sourceTrack: SourceTrack): Boolean

    fun existsByRawSignalAndSourceTrack(rawSignal: String, sourceTrack: SourceTrack): Boolean

    @Query("SELECT i FROM Idea i ORDER BY i.score DESC NULLS LAST, i.createdAt DESC")
    fun findAllOrderedByScore(): List<Idea>

    fun findByStatus(status: IdeaStatus, pageable: Pageable): Page<Idea>
}
