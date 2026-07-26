package com.daybrew.admin

import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin", description = "관리자 통계")
@SecurityRequirement(name = "bearerAuth")
class AdminStatsController(private val adminStatsService: AdminStatsService) {

    @GetMapping("/stats")
    fun getStats(): AdminStatsDto = adminStatsService.getStats()
}
