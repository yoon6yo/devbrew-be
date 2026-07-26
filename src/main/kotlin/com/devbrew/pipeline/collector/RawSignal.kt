package com.devbrew.pipeline.collector

import com.devbrew.idea.SourceTrack

data class RawSignal(
    val title: String,
    val body: String,
    val url: String?,
    val track: SourceTrack,
)
