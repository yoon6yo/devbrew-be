package com.daybrew.auth

import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, Long> {
    fun findByEmail(email: String): User?
    fun findByProviderAndProviderId(provider: Provider, providerId: String): User?
    fun existsByEmail(email: String): Boolean
    fun existsByRole(role: UserRole): Boolean
}
