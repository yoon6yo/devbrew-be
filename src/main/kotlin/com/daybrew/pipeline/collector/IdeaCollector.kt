package com.daybrew.pipeline.collector

interface IdeaCollector {
    fun collect(): List<RawSignal>
}
