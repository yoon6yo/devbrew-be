package com.daybrew.admin

import com.daybrew.idea.SourceTrack
import com.daybrew.pipeline.PipelineScheduler
import com.daybrew.pipeline.PipelineStatusDto
import com.daybrew.pipeline.PipelineStatusTracker
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class PipelineTriggerRequest(
    val sources: List<SourceTrack>? = null,
)

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin", description = "관리자 통계")
@SecurityRequirement(name = "bearerAuth")
class AdminStatsController(
    private val adminStatsService: AdminStatsService,
    private val pipelineScheduler: PipelineScheduler,
    private val pipelineStatusTracker: PipelineStatusTracker,
) {

    @GetMapping("/stats")
    fun getStats(): AdminStatsDto = adminStatsService.getStats()

    @GetMapping("/pipeline/status")
    fun getPipelineStatus(): PipelineStatusDto = pipelineStatusTracker.get()

    @Operation(summary = "전체 파이프라인 수동 실행", description = "수집+생성+채점 전 단계를 순서대로 실행합니다. ADMIN 권한 필요.")
    @ApiResponse(responseCode = "202", description = "파이프라인 시작됨")
    @PostMapping("/pipeline/trigger")
    fun triggerPipeline(
        @RequestBody(required = false) req: PipelineTriggerRequest?,
    ): ResponseEntity<Map<String, String>> {
        val sourcesSet = (req ?: PipelineTriggerRequest()).sources?.toSet()
        pipelineScheduler.triggerAsync(sources = sourcesSet)
        return ResponseEntity.accepted().body(mapOf("message" to "Pipeline started"))
    }

    @Operation(summary = "수집+생성만 실행", description = "신호 수집 및 아이디어 생성만 수행합니다. 결과는 대기중 상태로 저장됩니다. ADMIN 권한 필요.")
    @ApiResponse(responseCode = "202", description = "수집 시작됨")
    @PostMapping("/pipeline/collect")
    fun triggerCollect(
        @RequestBody(required = false) req: PipelineTriggerRequest?,
    ): ResponseEntity<Map<String, String>> {
        val sourcesSet = (req ?: PipelineTriggerRequest()).sources?.toSet()
        pipelineScheduler.triggerCollectAsync(sources = sourcesSet)
        return ResponseEntity.accepted().body(mapOf("message" to "Collect started"))
    }

    @Operation(summary = "채점만 실행", description = "대기중인 아이디어 전체를 채점합니다. ADMIN 권한 필요.")
    @ApiResponse(responseCode = "202", description = "채점 시작됨")
    @PostMapping("/pipeline/score")
    fun triggerScore(): ResponseEntity<Map<String, String>> {
        pipelineScheduler.triggerScoreAsync()
        return ResponseEntity.accepted().body(mapOf("message" to "Score started"))
    }
}
