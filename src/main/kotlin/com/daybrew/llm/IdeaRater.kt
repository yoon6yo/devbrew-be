package com.daybrew.llm

import com.daybrew.idea.Idea

interface IdeaRater {
    fun rateAll(ideas: List<Idea>): Map<Long, ScoreResult>
}
