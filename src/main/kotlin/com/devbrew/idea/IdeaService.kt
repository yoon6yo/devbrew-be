package com.devbrew.idea

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Service
@Transactional(readOnly = true)
class IdeaService(private val ideaRepository: IdeaRepository) {

    fun getAll(): List<Idea> = ideaRepository.findAllOrderedByScore()

    fun getById(id: Long): Idea = ideaRepository.findById(id)
        .orElseThrow { NoSuchElementException("Idea not found: $id") }

    fun getPending(): List<Idea> = ideaRepository.findByStatus(IdeaStatus.PENDING)

    fun getScored(): List<Idea> = ideaRepository.findByStatus(IdeaStatus.SCORED)

    @Transactional
    fun save(idea: Idea): Idea = ideaRepository.save(idea)

    @Transactional
    fun updateScore(id: Long, score: Short, reason: String): Idea {
        val idea = getById(id)
        idea.score = score
        idea.scoreReason = reason
        idea.status = IdeaStatus.SCORED
        idea.updatedAt = OffsetDateTime.now()
        return ideaRepository.save(idea)
    }

    @Transactional
    fun markNotified(id: Long): Idea {
        val idea = getById(id)
        idea.status = IdeaStatus.NOTIFIED
        idea.updatedAt = OffsetDateTime.now()
        return ideaRepository.save(idea)
    }

    @Transactional
    fun reject(id: Long): Idea {
        val idea = getById(id)
        idea.status = IdeaStatus.REJECTED
        idea.updatedAt = OffsetDateTime.now()
        return ideaRepository.save(idea)
    }

    fun isDuplicate(sourceUrl: String, track: SourceTrack): Boolean =
        ideaRepository.existsBySourceUrlAndSourceTrack(sourceUrl, track)
}
