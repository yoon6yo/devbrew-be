package com.devbrew.pipeline.collector

interface IdeaCollector {
    fun collect(): List<RawSignal>
}
