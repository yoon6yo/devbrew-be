package com.devbrew.idea

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.util.Optional

class IdeaServiceStarTest {

    private val ideaRepository = mockk<IdeaRepository>()
    private val userStarRepository = mockk<UserStarRepository>()
    private lateinit var service: IdeaService

    private val testIdea = Idea(
        id = 1L,
        title = "Test Idea",
        description = "Description",
        sourceTrack = SourceTrack.SAAS,
        score = 8,
        starCount = 0,
    )

    @BeforeEach
    fun setUp() {
        service = IdeaService(ideaRepository, userStarRepository)
    }

    @Test
    fun `starIdea returns true and increments count when not yet starred`() {
        every { ideaRepository.findById(1L) } returns Optional.of(testIdea)
        every { userStarRepository.existsByIdeaIdAndFingerprint(1L, "fp-abc") } returns false
        every { userStarRepository.save(any()) } returns mockk()
        every { ideaRepository.save(any()) } answers { firstArg() }

        val (idea, starred) = service.starIdea(1L, "fp-abc")

        assertThat(starred).isTrue()
        assertThat(idea.starCount).isEqualTo(1)
        verify { userStarRepository.save(match { it.ideaId == 1L && it.fingerprint == "fp-abc" }) }
    }

    @Test
    fun `starIdea returns false when already starred`() {
        every { ideaRepository.findById(1L) } returns Optional.of(testIdea.apply { starCount = 1 })
        every { userStarRepository.existsByIdeaIdAndFingerprint(1L, "fp-abc") } returns true

        val (_, starred) = service.starIdea(1L, "fp-abc")

        assertThat(starred).isFalse()
        verify(exactly = 0) { userStarRepository.save(any()) }
    }

    @Test
    fun `unstarIdea returns true and decrements count when starred`() {
        val starredIdea = Idea(
            id = 1L, title = "T", description = "D",
            sourceTrack = SourceTrack.SAAS, starCount = 3,
        )
        every { ideaRepository.findById(1L) } returns Optional.of(starredIdea)
        every { userStarRepository.existsByIdeaIdAndFingerprint(1L, "fp-abc") } returns true
        every { userStarRepository.deleteByIdeaIdAndFingerprint(1L, "fp-abc") } returns Unit
        every { ideaRepository.save(any()) } answers { firstArg() }

        val (idea, unstarred) = service.unstarIdea(1L, "fp-abc")

        assertThat(unstarred).isTrue()
        assertThat(idea.starCount).isEqualTo(2)
    }

    @Test
    fun `unstarIdea returns false when not starred`() {
        every { ideaRepository.findById(1L) } returns Optional.of(testIdea)
        every { userStarRepository.existsByIdeaIdAndFingerprint(1L, "fp-abc") } returns false

        val (_, unstarred) = service.unstarIdea(1L, "fp-abc")

        assertThat(unstarred).isFalse()
        verify(exactly = 0) { ideaRepository.save(any()) }
    }

    @Test
    fun `starCount never goes below zero on unstar`() {
        val zeroStarIdea = Idea(
            id = 1L, title = "T", description = "D",
            sourceTrack = SourceTrack.SAAS, starCount = 0,
        )
        every { ideaRepository.findById(1L) } returns Optional.of(zeroStarIdea)
        every { userStarRepository.existsByIdeaIdAndFingerprint(1L, "fp-abc") } returns true
        every { userStarRepository.deleteByIdeaIdAndFingerprint(1L, "fp-abc") } returns Unit
        every { ideaRepository.save(any()) } answers { firstArg() }

        val (idea, _) = service.unstarIdea(1L, "fp-abc")

        assertThat(idea.starCount).isEqualTo(0)
    }

    @Test
    fun `starIdea rejects blank fingerprint`() {
        assertThatThrownBy { service.starIdea(1L, "") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `starIdea rejects fingerprint over 64 chars`() {
        val longFp = "a".repeat(65)
        assertThatThrownBy { service.starIdea(1L, longFp) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
