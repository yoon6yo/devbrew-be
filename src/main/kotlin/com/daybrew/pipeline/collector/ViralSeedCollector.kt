package com.daybrew.pipeline.collector

import com.daybrew.config.DayBrewProperties
import com.daybrew.idea.SourceTrack
import org.springframework.stereotype.Component

@Component
class ViralSeedCollector(
    private val props: DayBrewProperties,
) : IdeaCollector {

    override fun collect(): List<RawSignal> {
        return props.pipeline.viral.seedTopics.map { topic ->
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
