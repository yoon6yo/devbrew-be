package com.daybrew.auth

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

@Component
class LoginRateLimiter {

    companion object {
        const val MAX_FAILURES = 5
        const val LOCKOUT_MS = 15 * 60 * 1000L
    }

    private data class Entry(
        val failures: AtomicInteger = AtomicInteger(0),
        val lockedUntil: AtomicLong = AtomicLong(0),
        val lastFailedAt: AtomicLong = AtomicLong(System.currentTimeMillis()),
    )

    private val store = ConcurrentHashMap<String, Entry>()

    fun isBlocked(key: String): Boolean {
        val entry = store[key] ?: return false
        return entry.lockedUntil.get() > System.currentTimeMillis()
    }

    fun retryAfterSeconds(key: String): Long {
        val entry = store[key] ?: return 1L
        val remaining = entry.lockedUntil.get() - System.currentTimeMillis()
        return ((remaining + 999) / 1000).coerceAtLeast(1)
    }

    fun recordFailure(key: String) {
        val entry = store.computeIfAbsent(key) { Entry() }
        entry.lastFailedAt.set(System.currentTimeMillis())
        if (entry.failures.incrementAndGet() >= MAX_FAILURES) {
            entry.lockedUntil.set(System.currentTimeMillis() + LOCKOUT_MS)
        }
    }

    fun recordSuccess(key: String) {
        store.remove(key)
    }

    @Scheduled(fixedDelay = 60_000)
    fun evictExpired() {
        val now = System.currentTimeMillis()
        store.entries.removeIf { (_, v) ->
            val lockUntil = v.lockedUntil.get()
            if (lockUntil > 0) lockUntil < now
            else v.lastFailedAt.get() + LOCKOUT_MS < now
        }
    }
}
