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
    )

    private val store = ConcurrentHashMap<String, Entry>()

    fun isBlocked(ip: String): Boolean {
        val entry = store[ip] ?: return false
        return entry.lockedUntil.get() > System.currentTimeMillis()
    }

    fun recordFailure(ip: String) {
        val entry = store.computeIfAbsent(ip) { Entry() }
        if (entry.failures.incrementAndGet() >= MAX_FAILURES) {
            entry.lockedUntil.set(System.currentTimeMillis() + LOCKOUT_MS)
        }
    }

    fun recordSuccess(ip: String) {
        store.remove(ip)
    }

    @Scheduled(fixedDelay = 60_000)
    fun evictExpired() {
        val now = System.currentTimeMillis()
        store.entries.removeIf { (_, v) ->
            v.lockedUntil.get() in 1 until now && v.failures.get() >= MAX_FAILURES
        }
    }
}
