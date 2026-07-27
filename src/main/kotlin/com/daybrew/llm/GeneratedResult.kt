package com.daybrew.llm

import com.daybrew.idea.Idea

data class GeneratedResult(val idea: Idea, val score: ScoreResult?)
