package com.daybrew.auth

import com.daybrew.config.DayBrewProperties
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

class AdminUserBootstrapTest {

    private val userRepository = mockk<UserRepository>()
    private val passwordEncoder = BCryptPasswordEncoder()

    private fun bootstrap(password: String, email: String = "admin@daybrew.local") = AdminUserBootstrap(
        userRepository,
        passwordEncoder,
        DayBrewProperties(admin = DayBrewProperties.AdminProps(password = password, email = email)),
    )

    @Test
    fun `creates admin user when none exists and password is set`() {
        every { userRepository.existsByRole(UserRole.ADMIN) } returns false
        val savedSlot = slot<User>()
        every { userRepository.save(capture(savedSlot)) } answers { savedSlot.captured }

        bootstrap("securePass1").bootstrap()

        verify(exactly = 1) { userRepository.save(any()) }
        assertThat(savedSlot.captured.role).isEqualTo(UserRole.ADMIN)
        assertThat(savedSlot.captured.provider).isEqualTo(Provider.LOCAL)
        assertThat(passwordEncoder.matches("securePass1", savedSlot.captured.passwordHash)).isTrue()
    }

    @Test
    fun `skips bootstrap when admin user already exists`() {
        every { userRepository.existsByRole(UserRole.ADMIN) } returns true

        bootstrap("anyPassword").bootstrap()

        verify(exactly = 0) { userRepository.save(any()) }
    }

    @Test
    fun `skips bootstrap when password is blank`() {
        every { userRepository.existsByRole(UserRole.ADMIN) } returns false

        bootstrap("").bootstrap()

        verify(exactly = 0) { userRepository.save(any()) }
    }

    @Test
    fun `uses configured admin email`() {
        every { userRepository.existsByRole(UserRole.ADMIN) } returns false
        val savedSlot = slot<User>()
        every { userRepository.save(capture(savedSlot)) } answers { savedSlot.captured }

        bootstrap("securePass1", email = "custom-admin@company.com").bootstrap()

        assertThat(savedSlot.captured.email).isEqualTo("custom-admin@company.com")
    }
}
