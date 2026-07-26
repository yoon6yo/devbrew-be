package com.daybrew.admin

import com.daybrew.pipeline.PipelineScheduler
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.CompletableFuture

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin", description = "관리자 통계")
@SecurityRequirement(name = "bearerAuth")
class AdminStatsController(
    private val adminStatsService: AdminStatsService,
    private val pipelineScheduler: PipelineScheduler,
) {

    @GetMapping("/stats")
    fun getStats(): AdminStatsDto = adminStatsService.getStats()

    @Operation(
        summary = "파이프라인 수동 실행",
        description = "아이디어 수집·생성·채점 파이프라인을 즉시 실행합니다. 비동기로 처리되며 202 Accepted를 즉시 반환합니다. ADMIN 권한 필요.",
    )
    @ApiResponse(responseCode = "202", description = "파이프라인 시작됨")
    @PostMapping("/pipeline/trigger")
    fun triggerPipeline(): ResponseEntity<Map<String, String>> {
        CompletableFuture.runAsync { pipelineScheduler.runPipeline() }
        return ResponseEntity.accepted().body(mapOf("message" to "Pipeline started"))
    }
}
