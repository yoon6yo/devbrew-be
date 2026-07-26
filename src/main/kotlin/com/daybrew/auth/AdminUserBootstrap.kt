package com.daybrew.auth

import com.daybrew.config.DayBrewProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

@Component
class AdminUserBootstrap(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val props: DayBrewProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener(ApplicationReadyEvent::class)
    fun bootstrap() {
        if (userRepository.existsByRole(UserRole.ADMIN)) return
        if (props.admin.password.isBlank()) {
            log.warn("ADMIN_PASSWORD not set — skipping admin user bootstrap")
            return
        }
        userRepository.save(
            User(
                email = props.admin.email,
                passwordHash = passwordEncoder.encode(props.admin.password),
                role = UserRole.ADMIN,
                provider = Provider.LOCAL,
            )
        )
        log.info("Admin user bootstrapped: ${props.admin.email}")
    }
}
