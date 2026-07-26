package com.devbrew.admin

import com.devbrew.pipeline.PipelineScheduler
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/pipeline")
@Tag(name = "Admin", description = "관리자 통계")
@SecurityRequirement(name = "bearerAuth")
class PipelineAdminController(private val pipelineScheduler: PipelineScheduler) {

    @PostMapping("/trigger")
    @Operation(summary = "파이프라인 수동 실행", description = "데이터 수집 → LLM 채점 → Slack 알림 파이프라인을 즉시 비동기 실행합니다.")
    fun trigger(): ResponseEntity<Map<String, String>> {
        pipelineScheduler.triggerAsync()
        return ResponseEntity.accepted().body(mapOf("status" to "triggered"))
    }
}
