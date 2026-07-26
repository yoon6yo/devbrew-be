package com.daybrew.idea

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import org.springdoc.core.annotations.ParameterObject
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/ideas")
@Tag(name = "Ideas", description = "아이디어 조회 및 관리")
class IdeaController(
    private val ideaService: IdeaService,
    private val adminStatsService: com.daybrew.admin.AdminStatsService,
    private val starRateLimiter: StarRateLimiter,
) {

    @GetMapping
    @Operation(
        summary = "아이디어 목록 조회 (페이지네이션)",
        description = """
            status 필터와 Pageable 정렬을 지원합니다. 인증 불필요.

            정렬 예시:
            - `?sort=score,desc` — 점수 높은 순 (기본값)
            - `?sort=starCount,desc` — 스타 많은 순
            - `?status=NOTIFIED&sort=starCount,desc` — Slack 알림된 아이디어 중 스타 순
        """,
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "조회 성공"),
    )
    fun list(
        @Parameter(description = "아이디어 상태 필터 (PENDING / SCORED / NOTIFIED / REJECTED). 생략 시 전체 조회")
        @RequestParam(required = false) status: IdeaStatus?,
        @ParameterObject @PageableDefault(size = 20, sort = ["score"], direction = Sort.Direction.DESC) pageable: Pageable,
    ): Page<IdeaDto> {
        adminStatsService.incrementPageViews()
        return ideaService.getPage(status, pageable).map { it.toDto() }
    }

    @GetMapping("/{id}")
    @Operation(summary = "특정 아이디어 단건 조회", description = "인증 불필요.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "조회 성공"),
        ApiResponse(responseCode = "404", description = "존재하지 않는 아이디어", content = [Content(schema = Schema(hidden = true))]),
    )
    fun get(@PathVariable id: Long): ResponseEntity<IdeaDto> =
        ResponseEntity.ok(ideaService.getById(id).toDto())

    @PostMapping("/{id}/star")
    @Operation(
        summary = "아이디어 스타 추가",
        description = """
            디바이스 고유 식별자(`X-Fingerprint`)를 기준으로 중복 스타를 방지합니다.
            FE에서는 `localStorage`에 저장한 UUID 또는 FingerprintJS로 생성한 값을 사용하세요.

            - 201: 스타 추가 성공
            - 409: 이미 스타한 상태 (중복 요청)
        """,
    )
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "스타 추가 성공"),
        ApiResponse(responseCode = "409", description = "이미 스타한 아이디어 (동일 디바이스 중복)", content = [Content(schema = Schema(hidden = true))]),
        ApiResponse(responseCode = "400", description = "X-Fingerprint 헤더 누락 또는 64자 초과", content = [Content(schema = Schema(hidden = true))]),
        ApiResponse(responseCode = "404", description = "존재하지 않는 아이디어", content = [Content(schema = Schema(hidden = true))]),
    )
    fun star(
        @PathVariable id: Long,
        @Parameter(
            name = "X-Fingerprint",
            description = "디바이스 고유 식별자. localStorage UUID 또는 FingerprintJS 값. 최대 64자.",
            required = true,
            `in` = ParameterIn.HEADER,
            schema = Schema(type = "string", maxLength = 64, example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"),
        )
        @RequestHeader("X-Fingerprint") fingerprint: String,
        request: HttpServletRequest,
    ): ResponseEntity<IdeaDto> {
        val directPeer = request.remoteAddr
        val ip = if (isPrivateAddress(directPeer))
            request.getHeader("X-Real-IP")?.takeIf { it.isNotBlank() } ?: directPeer
        else directPeer
        val retryAfter = starRateLimiter.checkAndRecord(ip)
        if (retryAfter != null) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, retryAfter.toString())
                .build()
        }
        val (idea, starred) = ideaService.starIdea(id, fingerprint)
        return if (starred) ResponseEntity.status(HttpStatus.CREATED).body(idea.toDto())
        else ResponseEntity.status(HttpStatus.CONFLICT).body(idea.toDto())
    }

    @DeleteMapping("/{id}/star")
    @Operation(
        summary = "아이디어 스타 취소",
        description = """
            `X-Fingerprint`로 식별된 디바이스의 스타를 취소합니다.

            - 200: 스타 취소 성공
            - 404: 해당 디바이스가 스타하지 않은 아이디어
        """,
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "스타 취소 성공"),
        ApiResponse(responseCode = "404", description = "스타하지 않은 아이디어 (취소할 대상 없음)", content = [Content(schema = Schema(hidden = true))]),
        ApiResponse(responseCode = "400", description = "X-Fingerprint 헤더 누락 또는 64자 초과", content = [Content(schema = Schema(hidden = true))]),
    )
    private fun isPrivateAddress(addr: String): Boolean =
        addr == "127.0.0.1" || addr == "::1" ||
            addr.startsWith("10.") ||
            addr.startsWith("192.168.") ||
            Regex("""^172\.(1[6-9]|2\d|3[01])\.""").containsMatchIn(addr)

    fun unstar(
        @PathVariable id: Long,
        @Parameter(
            name = "X-Fingerprint",
            description = "디바이스 고유 식별자. star 추가 시 사용한 값과 동일해야 합니다.",
            required = true,
            `in` = ParameterIn.HEADER,
            schema = Schema(type = "string", maxLength = 64, example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"),
        )
        @RequestHeader("X-Fingerprint") fingerprint: String,
    ): ResponseEntity<IdeaDto> {
        val (idea, unstarred) = ideaService.unstarIdea(id, fingerprint)
        return if (unstarred) ResponseEntity.ok(idea.toDto())
        else ResponseEntity.notFound().build()
    }

    @PostMapping("/{id}/reject")
    @Operation(
        summary = "아이디어 수동 거절 (관리자 전용)",
        description = """
            아이디어 status를 `REJECTED`로 변경합니다.
            LLM이 채점(SCORED)한 아이디어를 Slack 알림(NOTIFIED) 전에 수동으로 필터링할 때 사용합니다.
            JWT Bearer 토큰 인증이 필요합니다.
        """,
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "거절 처리 성공"),
        ApiResponse(responseCode = "401", description = "인증 토큰 없음 또는 만료", content = [Content(schema = Schema(hidden = true))]),
        ApiResponse(responseCode = "404", description = "존재하지 않는 아이디어", content = [Content(schema = Schema(hidden = true))]),
    )
    @SecurityRequirement(name = "bearerAuth")
    fun reject(@PathVariable id: Long): ResponseEntity<IdeaDto> =
        ResponseEntity.ok(ideaService.reject(id).toDto())
}
