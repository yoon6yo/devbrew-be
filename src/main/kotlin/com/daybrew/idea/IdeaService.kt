package com.daybrew.idea

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

@Service
@Transactional(readOnly = true)
class IdeaService(
    private val ideaRepository: IdeaRepository,
    private val userStarRepository: UserStarRepository,
) {

    fun getAll(): List<Idea> = ideaRepository.findAllOrderedByScore()

    fun getPage(
        status: IdeaStatus?,
        statuses: List<IdeaStatus>?,
        today: Boolean?,
        pageable: Pageable,
    ): Page<Idea> {
        val kst = ZoneId.of("Asia/Seoul")
        val todayStart = LocalDate.now(kst).atStartOfDay(kst).toOffsetDateTime()
        return when {
            !statuses.isNullOrEmpty() -> ideaRepository.findByStatusIn(statuses, pageable)
            status != null && today == true -> ideaRepository.findByStatusAndCreatedAtGreaterThanEqual(status, todayStart, pageable)
            status != null && today == false -> ideaRepository.findByStatusAndCreatedAtLessThan(status, todayStart, pageable)
            status != null -> ideaRepository.findByStatus(status, pageable)
            else -> ideaRepository.findAll(pageable)
        }
    }

    fun getById(id: Long): Idea = ideaRepository.findById(id)
        .orElseThrow { NoSuchElementException("Idea not found: $id") }

    fun getStatusCounts(): Map<String, Long> {
        val grouped = ideaRepository.countAllGroupedByStatus().associate { it.status.name to it.count }
        return IdeaStatus.entries.associate { it.name to (grouped[it.name] ?: 0L) }
    }

    fun getPending(): List<Idea> = ideaRepository.findByStatus(IdeaStatus.PENDING)

    fun getScored(): List<Idea> = ideaRepository.findByStatus(IdeaStatus.SCORED)

    @Transactional
    fun save(idea: Idea): Idea = ideaRepository.save(idea)

    @Transactional
    fun updateScore(id: Long, result: com.daybrew.llm.ScoreResult): Idea {
        val idea = getById(id)
        idea.score = result.score
        idea.scoreMarketFit = result.marketFit
        idea.scoreNovelty = result.novelty
        idea.scoreFeasibility = result.feasibility
        idea.scoreMonetization = result.monetization
        idea.scoreTrend = result.trend
        idea.scoreReason = result.reason
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

    @Transactional
    fun restore(id: Long): Idea {
        val idea = getById(id)
        require(idea.status == IdeaStatus.REJECTED) { "Only REJECTED ideas can be restored" }
        idea.status = if (idea.score != null) IdeaStatus.SCORED else IdeaStatus.PENDING
        idea.updatedAt = OffsetDateTime.now()
        return ideaRepository.save(idea)
    }

    @Transactional
    fun starIdea(id: Long, fingerprint: String): Pair<Idea, Boolean> {
        require(fingerprint.isNotBlank() && fingerprint.length <= 64) { "Invalid fingerprint" }
        val idea = getById(id)
        if (userStarRepository.existsByIdeaIdAndFingerprint(id, fingerprint)) {
            return idea to false
        }
        return try {
            userStarRepository.save(UserStar(ideaId = id, fingerprint = fingerprint))
            idea.starCount++
            ideaRepository.save(idea) to true
        } catch (e: DataIntegrityViolationException) {
            idea to false
        }
    }

    @Transactional
    fun unstarIdea(id: Long, fingerprint: String): Pair<Idea, Boolean> {
        require(fingerprint.isNotBlank() && fingerprint.length <= 64) { "Invalid fingerprint" }
        val idea = getById(id)
        if (!userStarRepository.existsByIdeaIdAndFingerprint(id, fingerprint)) {
            return idea to false
        }
        userStarRepository.deleteByIdeaIdAndFingerprint(id, fingerprint)
        idea.starCount = maxOf(0, idea.starCount - 1)
        return ideaRepository.save(idea) to true
    }

    fun isDuplicate(sourceUrl: String?, rawSignal: String?, track: SourceTrack): Boolean = when {
        sourceUrl != null -> ideaRepository.existsBySourceUrlAndSourceTrack(sourceUrl, track)
        rawSignal != null -> ideaRepository.existsByRawSignalAndSourceTrack(rawSignal, track)
        else -> false
    }
}
