package com.devbrew.llm

import com.devbrew.config.DevBrewProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicLong

@Component
class GeminiDailyBudget(private val props: DevBrewProperties) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Volatile private var currentDate: LocalDate = LocalDate.now()
    private val promptTokens = AtomicLong(0)
    private val completionTokens = AtomicLong(0)

    fun recordUsage(prompt: Int, completion: Int) {
        ensureSameDay()
        promptTokens.addAndGet(prompt.toLong())
        completionTokens.addAndGet(completion.toLong())
    }

    fun isDailyBudgetExceeded(): Boolean {
        ensureSameDay()
        val costUsd = (promptTokens.get() * 0.10 + completionTokens.get() * 0.40) / 1_000_000.0
        val costKrw = costUsd * props.gemini.usdToKrw
        return costKrw >= props.gemini.dailyBudgetKrw
    }

    private fun ensureSameDay() {
        val today = LocalDate.now()
        if (today != currentDate) {
            synchronized(this) {
                if (today != currentDate) {
                    log.info("Resetting Gemini daily budget counter for {}", today)
                    promptTokens.set(0)
                    completionTokens.set(0)
                    currentDate = today
                }
            }
        }
    }
}
