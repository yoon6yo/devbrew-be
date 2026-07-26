package com.daybrew.admin

import com.daybrew.pipeline.PipelineScheduler
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

    @PostMapping("/pipeline/trigger")
    fun triggerPipeline(): ResponseEntity<Map<String, String>> {
        CompletableFuture.runAsync { pipelineScheduler.runPipeline() }
        return ResponseEntity.accepted().body(mapOf("message" to "Pipeline started"))
    }
}
