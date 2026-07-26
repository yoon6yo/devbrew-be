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
        val deque = store.computeIfAbsent(ip) { ArrayDeque() }
        synchronized(deque) {
            while (deque.isNotEmpty() && now - deque.peekFirst() >= WINDOW_MS) {
                deque.pollFirst()
            }
            if (deque.size >= MAX_REQUESTS) {
                val oldest = deque.peekFirst()
                val retryAfterMs = WINDOW_MS - (now - oldest)
                return ((retryAfterMs + 999) / 1000).coerceAtLeast(1)
            }
            deque.addLast(now)
            return null
        }
    }

    @Scheduled(fixedDelay = 60_000)
    fun evictExpired() {
        val now = System.currentTimeMillis()
        store.entries.removeIf { (_, deque) ->
            synchronized(deque) {
                while (deque.isNotEmpty() && now - deque.peekFirst() >= WINDOW_MS) {
                    deque.pollFirst()
                }
                deque.isEmpty()
            }
        }
    }
}
