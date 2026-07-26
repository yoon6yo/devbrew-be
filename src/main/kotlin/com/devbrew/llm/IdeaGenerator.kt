package com.devbrew.llm

import com.devbrew.idea.Idea
import com.devbrew.pipeline.collector.RawSignal

interface IdeaGenerator {
    fun generate(signal: RawSignal): Idea
}
