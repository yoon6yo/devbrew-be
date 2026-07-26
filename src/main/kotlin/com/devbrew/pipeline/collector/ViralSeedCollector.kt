package com.devbrew.pipeline.collector

import com.devbrew.idea.SourceTrack
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class ViralSeedCollector(
    @Value("\${devbrew.pipeline.viral.seed-topics}") private val seedTopics: List<String>,
) : IdeaCollector {

    override fun collect(): List<RawSignal> {
        return seedTopics.map { topic ->
            RawSignal(
                title = "Idea exploration: $topic",
                body = "Generate a viral, solo-implementable $topic idea for indie developers. " +
                        "It should be completable in 2-3 weeks and have clear monetization potential.",
                url = null,
                track = SourceTrack.VIRAL,
            )
        }
    }
}
