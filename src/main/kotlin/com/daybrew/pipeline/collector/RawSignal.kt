package com.daybrew.pipeline.collector

import com.daybrew.idea.SourceTrack

data class RawSignal(
    val title: String,
    val body: String,
    val url: String?,
    val track: SourceTrack,
    val engagementScore: Int? = null,
)
