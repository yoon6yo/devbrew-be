package com.devbrew.idea

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/ideas")
class IdeaController(private val ideaService: IdeaService) {

    @GetMapping
    fun list(): List<Idea> = ideaService.getAll()

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long): ResponseEntity<Idea> =
        ResponseEntity.ok(ideaService.getById(id))

    @PostMapping("/{id}/reject")
    fun reject(@PathVariable id: Long): ResponseEntity<Idea> =
        ResponseEntity.ok(ideaService.reject(id))
}
