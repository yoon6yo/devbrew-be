package com.daybrew.llm

import com.daybrew.pipeline.collector.RawSignal

interface IdeaGenerator {
    fun generate(signal: RawSignal): GeneratedResult
}
