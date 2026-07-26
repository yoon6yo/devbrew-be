package com.daybrew.idea

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

@Component
class StarRateLimiter {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val MAX_REQUESTS = 20
        const val WINDOW_MS = 60_000L
    }

    private val store = ConcurrentHashMap<String, ArrayDeque<Long>>()

    /** @return null if allowed; Retry-After seconds (>=1) if rate-limited */
    fun checkAndRecord(ip: String): Long? {
        val now = System.currentTimeMillis()
        var result: Long? = null
        store.compute(ip) { _, existing ->
            val deque = existing ?: ArrayDeque()
            while (deque.isNotEmpty() && now - deque.peekFirst() >= WINDOW_MS) deque.pollFirst()
            if (deque.size >= MAX_REQUESTS) {
                val retryAfterMs = WINDOW_MS - (now - deque.peekFirst())
                result = ((retryAfterMs + 999) / 1000).coerceAtLeast(1)
                deque
            } else {
                deque.addLast(now)
                deque
            }
        }
        return result
    }

    @Scheduled(fixedDelay = 60_000)
    fun evictExpired() {
        val now = System.currentTimeMillis()
        for (key in store.keys.toList()) {
            store.compute(key) { _, deque ->
                if (deque == null) return@compute null
                while (deque.isNotEmpty() && now - deque.peekFirst() >= WINDOW_MS) deque.pollFirst()
                if (deque.isEmpty()) null else deque
            }
        }
    }
}
