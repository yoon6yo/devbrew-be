package com.devbrew.idea

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.util.Optional

class IdeaServiceTest {

    private val ideaRepository = mockk<IdeaRepository>()
    private val userStarRepository = mockk<UserStarRepository>()
    private lateinit var service: IdeaService

    @BeforeEach
    fun setUp() {
        service = IdeaService(ideaRepository, userStarRepository)
    }

    private fun idea(
        id: Long = 1L,
        status: IdeaStatus = IdeaStatus.PENDING,
        score: Short? = null,
        starCount: Int = 0,
    ) = Idea(id = id, title = "T$id", description = "D$id", sourceTrack = SourceTrack.SAAS,
        score = score, status = status, starCount = starCount)

    // ── getById ──────────────────────────────────────────────────────────────

    @Test
    fun `getById returns idea when found`() {
        val idea = idea(1L)
        every { ideaRepository.findById(1L) } returns Optional.of(idea)
        assertThat(service.getById(1L)).isEqualTo(idea)
    }

    @Test
    fun `getById throws NoSuchElementException when not found`() {
        every { ideaRepository.findById(99L) } returns Optional.empty()
        assertThatThrownBy { service.getById(99L) }
            .isInstanceOf(NoSuchElementException::class.java)
            .hasMessageContaining("99")
    }

    // ── updateScore ──────────────────────────────────────────────────────────

    @Test
    fun `updateScore sets score, reason and transitions to SCORED`() {
        val idea = idea(1L, IdeaStatus.PENDING)
        every { ideaRepository.findById(1L) } returns Optional.of(idea)
        every { ideaRepository.save(any()) } answers { firstArg() }

        val result = service.updateScore(1L, 8, "Good market fit")

        assertThat(result.score).isEqualTo(8.toShort())
        assertThat(result.scoreReason).isEqualTo("Good market fit")
        assertThat(result.status).isEqualTo(IdeaStatus.SCORED)
    }

    @Test
    fun `updateScore persists via repository`() {
        val idea = idea(1L, IdeaStatus.PENDING)
        every { ideaRepository.findById(1L) } returns Optional.of(idea)
        every { ideaRepository.save(any()) } answers { firstArg() }

        service.updateScore(1L, 9, "Excellent")

        verify(exactly = 1) { ideaRepository.save(any()) }
    }

    // ── markNotified ─────────────────────────────────────────────────────────

    @Test
    fun `markNotified transitions status to NOTIFIED`() {
        val idea = idea(1L, IdeaStatus.SCORED, score = 8)
        every { ideaRepository.findById(1L) } returns Optional.of(idea)
        every { ideaRepository.save(any()) } answers { firstArg() }

        val result = service.markNotified(1L)

        assertThat(result.status).isEqualTo(IdeaStatus.NOTIFIED)
    }

    // ── reject ───────────────────────────────────────────────────────────────

    @Test
    fun `reject transitions status to REJECTED`() {
        val idea = idea(1L, IdeaStatus.SCORED)
        every { ideaRepository.findById(1L) } returns Optional.of(idea)
        every { ideaRepository.save(any()) } answers { firstArg() }

        val result = service.reject(1L)

        assertThat(result.status).isEqualTo(IdeaStatus.REJECTED)
    }

    @Test
    fun `reject throws when idea not found`() {
        every { ideaRepository.findById(99L) } returns Optional.empty()
        assertThatThrownBy { service.reject(99L) }
            .isInstanceOf(NoSuchElementException::class.java)
    }

    // ── isDuplicate ──────────────────────────────────────────────────────────

    @Test
    fun `isDuplicate returns true when source URL already exists`() {
        every { ideaRepository.existsBySourceUrlAndSourceTrack("https://ex.com", SourceTrack.SAAS) } returns true
        assertThat(service.isDuplicate("https://ex.com", null, SourceTrack.SAAS)).isTrue()
    }

    @Test
    fun `isDuplicate returns false when URL not yet seen`() {
        every { ideaRepository.existsBySourceUrlAndSourceTrack("https://new.com", SourceTrack.SAAS) } returns false
        assertThat(service.isDuplicate("https://new.com", null, SourceTrack.SAAS)).isFalse()
    }

    @Test
    fun `isDuplicate checks rawSignal when URL is null`() {
        every { ideaRepository.existsByRawSignalAndSourceTrack("raw body", SourceTrack.GITHUB) } returns true
        assertThat(service.isDuplicate(null, "raw body", SourceTrack.GITHUB)).isTrue()
    }

    @Test
    fun `isDuplicate returns false when both URL and rawSignal are null`() {
        assertThat(service.isDuplicate(null, null, SourceTrack.SAAS)).isFalse()
        verify(exactly = 0) { ideaRepository.existsBySourceUrlAndSourceTrack(any(), any()) }
        verify(exactly = 0) { ideaRepository.existsByRawSignalAndSourceTrack(any(), any()) }
    }

    @Test
    fun `isDuplicate prefers URL check over rawSignal when both provided`() {
        every { ideaRepository.existsBySourceUrlAndSourceTrack("https://ex.com", SourceTrack.SAAS) } returns false
        service.isDuplicate("https://ex.com", "raw body", SourceTrack.SAAS)
        verify(exactly = 0) { ideaRepository.existsByRawSignalAndSourceTrack(any(), any()) }
    }

    // ── getPage ──────────────────────────────────────────────────────────────

    @Test
    fun `getPage without status calls findAll`() {
        val pageable = PageRequest.of(0, 20)
        val page = PageImpl(listOf(idea(1L)), pageable, 1)
        every { ideaRepository.findAll(pageable) } returns page

        val result = service.getPage(null, pageable)

        assertThat(result.totalElements).isEqualTo(1)
        verify { ideaRepository.findAll(pageable) }
        verify(exactly = 0) { ideaRepository.findByStatus(any<IdeaStatus>(), any()) }
    }

    @Test
    fun `getPage with NOTIFIED status calls findByStatus`() {
        val pageable = PageRequest.of(0, 20)
        val page = PageImpl(listOf(idea(1L, IdeaStatus.NOTIFIED)), pageable, 1)
        every { ideaRepository.findByStatus(IdeaStatus.NOTIFIED, pageable) } returns page

        val result = service.getPage(IdeaStatus.NOTIFIED, pageable)

        assertThat(result.content[0].status).isEqualTo(IdeaStatus.NOTIFIED)
        verify { ideaRepository.findByStatus(IdeaStatus.NOTIFIED, pageable) }
        verify(exactly = 0) { ideaRepository.findAll(any<org.springframework.data.domain.Pageable>()) }
    }

    @Test
    fun `getPage returns empty page when no ideas exist`() {
        val pageable = PageRequest.of(0, 20)
        every { ideaRepository.findAll(pageable) } returns PageImpl(emptyList(), pageable, 0)

        val result = service.getPage(null, pageable)

        assertThat(result.isEmpty).isTrue()
        assertThat(result.totalElements).isEqualTo(0)
    }
}
