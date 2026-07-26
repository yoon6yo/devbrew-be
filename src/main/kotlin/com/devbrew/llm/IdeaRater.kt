package com.devbrew.llm

import com.devbrew.idea.Idea

interface IdeaRater {
    fun rateAll(ideas: List<Idea>): Map<Long, Pair<Short, String>>
}
