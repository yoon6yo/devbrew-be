package com.daybrew.idea

import com.daybrew.admin.AdminStatsService
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import java.time.OffsetDateTime

class IdeaControllerTest {

    private val ideaService = mockk<IdeaService>()
    private val adminStatsService = mockk<AdminStatsService>(relaxed = true)
    private lateinit var controller: IdeaController

    @BeforeEach
    fun setUp() {
        controller = IdeaController(ideaService, adminStatsService)
    }

    private fun idea(id: Long = 1L, score: Short = 8, starCount: Int = 3, status: IdeaStatus = IdeaStatus.NOTIFIED) =
        Idea(
            id = id, title = "Idea $id", description = "Desc $id",
            sourceTrack = SourceTrack.SAAS, score = score, scoreReason = "Good",
            starCount = starCount, status = status,
            createdAt = OffsetDateTime.parse("2025-07-01T00:00:00Z"),
        )

    // ── GET /api/ideas ────────────────────────────────────────────────────────

    @Test
    fun `list returns page of IdeaDtos`() {
        val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "score"))
        val page = PageImpl(listOf(idea(1L), idea(2L)), pageable, 2)
        every { ideaService.getPage(null, pageable) } returns page

        val result = controller.list(null, pageable)

        assertThat(result.totalElements).isEqualTo(2)
        assertThat(result.content[0].id).isEqualTo(1L)
        assertThat(result.content[1].id).isEqualTo(2L)
    }

    @Test
    fun `list maps Idea to IdeaDto without rawSignal`() {
        val pageable = PageRequest.of(0, 20)
        val ideaWithRawSignal = Idea(
            id = 1L, title = "T", description = "D",
            sourceTrack = SourceTrack.SAAS, rawSignal = "SECRET_RAW_SIGNAL",
        )
        val page = PageImpl(listOf(ideaWithRawSignal), pageable, 1)
        every { ideaService.getPage(null, pageable) } returns page

        val result = controller.list(null, pageable)

        val dto = result.content[0]
        // IdeaDto does not have a rawSignal field — verified by compile-time type
        assertThat(dto).isInstanceOf(IdeaDto::class.java)
        assertThat(dto.title).isEqualTo("T")
    }

    @Test
    fun `list with status filter passes status to service`() {
        val pageable = PageRequest.of(0, 20)
        val page = PageImpl(listOf(idea(1L, status = IdeaStatus.NOTIFIED)), pageable, 1)
        every { ideaService.getPage(IdeaStatus.NOTIFIED, pageable) } returns page

        val result = controller.list(IdeaStatus.NOTIFIED, pageable)

        assertThat(result.content[0].status).isEqualTo(IdeaStatus.NOTIFIED)
    }

    @Test
    fun `list returns empty page when no ideas`() {
        val pageable = PageRequest.of(0, 20)
        every { ideaService.getPage(null, pageable) } returns PageImpl(emptyList(), pageable, 0)

        val result = controller.list(null, pageable)

        assertThat(result.isEmpty).isTrue()
    }

    // ── GET /api/ideas/{id} ───────────────────────────────────────────────────

    @Test
    fun `get returns 200 with IdeaDto`() {
        every { ideaService.getById(1L) } returns idea(1L, starCount = 5)

        val response = controller.get(1L)

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body!!.id).isEqualTo(1L)
        assertThat(response.body!!.starCount).isEqualTo(5)
    }

    @Test
    fun `get propagates NoSuchElementException when not found`() {
        every { ideaService.getById(99L) } throws NoSuchElementException("Idea not found: 99")

        org.assertj.core.api.Assertions.assertThatThrownBy { controller.get(99L) }
            .isInstanceOf(NoSuchElementException::class.java)
            .hasMessageContaining("99")
    }

    // ── POST /api/ideas/{id}/star ─────────────────────────────────────────────

    @Test
    fun `star returns 201 CREATED when new star added`() {
        val updated = idea(1L, starCount = 4)
        every { ideaService.starIdea(1L, "device-fp") } returns (updated to true)

        val response = controller.star(1L, "device-fp")

        assertThat(response.statusCode).isEqualTo(HttpStatus.CREATED)
        assertThat(response.body!!.starCount).isEqualTo(4)
    }

    @Test
    fun `star returns 409 CONFLICT when already starred`() {
        val existing = idea(1L, starCount = 4)
        every { ideaService.starIdea(1L, "device-fp") } returns (existing to false)

        val response = controller.star(1L, "device-fp")

        assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
    }

    @Test
    fun `star propagates IllegalArgumentException on invalid fingerprint`() {
        every { ideaService.starIdea(1L, "") } throws IllegalArgumentException("Invalid fingerprint")

        org.assertj.core.api.Assertions.assertThatThrownBy { controller.star(1L, "") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `star response body contains updated starCount`() {
        val updated = idea(1L, starCount = 10)
        every { ideaService.starIdea(1L, "fp-xyz") } returns (updated to true)

        val response = controller.star(1L, "fp-xyz")

        assertThat(response.body!!.starCount).isEqualTo(10)
    }

    // ── DELETE /api/ideas/{id}/star ───────────────────────────────────────────

    @Test
    fun `unstar returns 200 OK when successfully removed`() {
        val updated = idea(1L, starCount = 2)
        every { ideaService.unstarIdea(1L, "device-fp") } returns (updated to true)

        val response = controller.unstar(1L, "device-fp")

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body!!.starCount).isEqualTo(2)
    }

    @Test
    fun `unstar returns 404 NOT_FOUND when not previously starred`() {
        val idea = idea(1L)
        every { ideaService.unstarIdea(1L, "device-fp") } returns (idea to false)

        val response = controller.unstar(1L, "device-fp")

        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    // ── POST /api/ideas/{id}/reject ───────────────────────────────────────────

    @Test
    fun `reject returns 200 OK with rejected IdeaDto`() {
        val rejected = idea(1L, status = IdeaStatus.REJECTED)
        every { ideaService.reject(1L) } returns rejected

        val response = controller.reject(1L)

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body!!.status).isEqualTo(IdeaStatus.REJECTED)
    }

    @Test
    fun `reject propagates NoSuchElementException when idea not found`() {
        every { ideaService.reject(99L) } throws NoSuchElementException("Idea not found: 99")

        org.assertj.core.api.Assertions.assertThatThrownBy { controller.reject(99L) }
            .isInstanceOf(NoSuchElementException::class.java)
    }
}
