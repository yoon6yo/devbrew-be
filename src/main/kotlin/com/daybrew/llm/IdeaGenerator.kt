package com.daybrew.llm

import com.daybrew.idea.Idea
import com.daybrew.pipeline.collector.RawSignal

interface IdeaGenerator {
    fun generate(signal: RawSignal): Idea
}
