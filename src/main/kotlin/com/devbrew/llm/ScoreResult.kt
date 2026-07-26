package com.devbrew.llm

data class ScoreResult(
    val score: Short,
    val marketFit: Short,
    val novelty: Short,
    val feasibility: Short,
    val monetization: Short,
    val trend: Short,
    val reason: String,
)
