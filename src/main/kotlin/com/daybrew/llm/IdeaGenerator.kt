package com.daybrew.llm

import com.daybrew.pipeline.collector.RawSignal

interface IdeaGenerator {
    fun generate(signal: RawSignal): GeneratedResult
    fun generateBatch(signals: List<RawSignal>): List<GeneratedResult> = signals.map { generate(it) }
}
