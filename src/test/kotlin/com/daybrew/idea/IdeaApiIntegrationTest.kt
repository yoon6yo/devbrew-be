package com.daybrew.idea

import com.daybrew.pipeline.PipelineScheduler
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("integration")
class IdeaApiIntegrationTest {

    @Autowired lateinit var applicationContext: WebApplicationContext
    @Autowired lateinit var ideaRepository: IdeaRepository
    @Autowired lateinit var userStarRepository: UserStarRepository
    @MockitoBean lateinit var pipelineScheduler: PipelineScheduler

    private lateinit var mvc: MockMvc

    @BeforeEach
    fun setup() {
        mvc = MockMvcBuilders.webAppContextSetup(applicationContext)
            .apply<DefaultMockMvcBuilder>(springSecurity())
            .build()
        userStarRepository.deleteAll()
        ideaRepository.deleteAll()
    }

    private fun saveIdea(title: String = "Test Idea") = ideaRepository.save(
        Idea(title = title, description = "Test description", sourceTrack = SourceTrack.GITHUB)
    )

    // ── Public list endpoint ──────────────────────────────────────────────────

    @Test
    fun `GET ideas returns 200 with empty content when no ideas exist`() {
        mvc.perform(get("/api/ideas"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content").isArray)
            .andExpect(jsonPath("$.content").isEmpty)
    }

    @Test
    fun `GET ideas returns saved idea in content`() {
        saveIdea("My Idea")
        mvc.perform(get("/api/ideas"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].title").value("My Idea"))
    }

    @Test
    fun `GET ideas filters by status`() {
        val idea = saveIdea("Notified Idea")
        ideaRepository.save(idea.apply { status = IdeaStatus.NOTIFIED })
        saveIdea("Pending Idea")

        mvc.perform(get("/api/ideas").param("status", "NOTIFIED"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].title").value("Notified Idea"))
    }

    // ── Single idea endpoint ──────────────────────────────────────────────────

    @Test
    fun `GET idea by id returns 404 when not found`() {
        mvc.perform(get("/api/ideas/99999"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `GET idea by id returns 200 when found`() {
        val saved = saveIdea("Found Idea")
        mvc.perform(get("/api/ideas/${saved.id}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.title").value("Found Idea"))
    }

    // ── Star endpoint ─────────────────────────────────────────────────────────

    @Test
    fun `POST star returns 201 on first star`() {
        val idea = saveIdea()
        mvc.perform(
            post("/api/ideas/${idea.id}/star")
                .header("X-Fingerprint", "fp-test-001")
        ).andExpect(status().isCreated)
    }

    @Test
    fun `POST star returns 409 on duplicate star from same fingerprint`() {
        val idea = saveIdea()
        mvc.perform(post("/api/ideas/${idea.id}/star").header("X-Fingerprint", "fp-test-001"))
        mvc.perform(post("/api/ideas/${idea.id}/star").header("X-Fingerprint", "fp-test-001"))
            .andExpect(status().isConflict)
    }

    @Test
    fun `POST star returns 404 when idea does not exist`() {
        mvc.perform(post("/api/ideas/99999/star").header("X-Fingerprint", "fp-test-001"))
            .andExpect(status().isNotFound)
    }

    // ── Unstar endpoint ───────────────────────────────────────────────────────

    @Test
    fun `DELETE star returns 200 when star exists`() {
        val idea = saveIdea()
        mvc.perform(post("/api/ideas/${idea.id}/star").header("X-Fingerprint", "fp-test-001"))
        mvc.perform(delete("/api/ideas/${idea.id}/star").header("X-Fingerprint", "fp-test-001"))
            .andExpect(status().isOk)
    }

    @Test
    fun `DELETE star returns 404 when star does not exist`() {
        val idea = saveIdea()
        mvc.perform(delete("/api/ideas/${idea.id}/star").header("X-Fingerprint", "fp-test-001"))
            .andExpect(status().isNotFound)
    }

    // ── Reject endpoint ───────────────────────────────────────────────────────

    @Test
    fun `POST reject returns 401 without authentication`() {
        val idea = saveIdea()
        mvc.perform(post("/api/ideas/${idea.id}/reject"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    @WithMockUser(roles = ["USER"])
    fun `POST reject returns 403 with USER role`() {
        val idea = saveIdea()
        mvc.perform(post("/api/ideas/${idea.id}/reject"))
            .andExpect(status().isForbidden)
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `POST reject returns 200 and sets status REJECTED with ADMIN role`() {
        val idea = saveIdea()
        mvc.perform(post("/api/ideas/${idea.id}/reject"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("REJECTED"))
    }

    // ── Admin stats endpoint ──────────────────────────────────────────────────

    @Test
    fun `GET admin stats returns 401 without authentication`() {
        mvc.perform(get("/api/admin/stats"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    @WithMockUser(roles = ["USER"])
    fun `GET admin stats returns 403 with USER role`() {
        mvc.perform(get("/api/admin/stats"))
            .andExpect(status().isForbidden)
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `GET admin stats returns 200 with ADMIN role`() {
        mvc.perform(get("/api/admin/stats"))
            .andExpect(status().isOk)
    }

    // ── Pipeline trigger endpoint ─────────────────────────────────────────────

    @Test
    fun `POST pipeline trigger returns 401 without authentication`() {
        mvc.perform(post("/api/admin/pipeline/trigger"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `POST pipeline trigger returns 202 with ADMIN role`() {
        mvc.perform(post("/api/admin/pipeline/trigger"))
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.message").value("Pipeline started"))
    }

    // ── Idea stats endpoint ───────────────────────────────────────────────────

    @Test
    fun `GET ideas stats returns 200 with status counts`() {
        saveIdea()
        mvc.perform(get("/api/ideas/stats"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.PENDING").value(1))
            .andExpect(jsonPath("$.SCORED").value(0))
            .andExpect(jsonPath("$.NOTIFIED").value(0))
            .andExpect(jsonPath("$.REJECTED").value(0))
    }

    // ── Login validation ──────────────────────────────────────────────────────

    @Test
    fun `POST login with blank email returns 400`() {
        mvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"","password":"somepassword"}""")
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `POST login with invalid email format returns 400`() {
        mvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"not-an-email","password":"somepassword"}""")
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `POST login with blank password returns 400`() {
        mvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"user@example.com","password":""}""")
        ).andExpect(status().isBadRequest)
    }

    // ── Actuator health ───────────────────────────────────────────────────────

    @Test
    fun `GET actuator health returns 200 without authentication`() {
        mvc.perform(get("/actuator/health"))
            .andExpect(status().isOk)
    }

    @Test
    fun `GET actuator health liveness returns 200`() {
        mvc.perform(get("/actuator/health/liveness"))
            .andExpect(status().isOk)
    }

    @Test
    fun `GET actuator health readiness returns 200`() {
        mvc.perform(get("/actuator/health/readiness"))
            .andExpect(status().isOk)
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    @Test
    fun `POST logout returns 204 and expires access_token cookie`() {
        mvc.perform(post("/api/auth/logout"))
            .andExpect(status().isNoContent)
            .andExpect(cookie().maxAge("access_token", 0))
    }
}
