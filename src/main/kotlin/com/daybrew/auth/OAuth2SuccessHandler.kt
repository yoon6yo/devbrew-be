package com.daybrew.auth

import com.daybrew.config.DayBrewProperties
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler
import org.springframework.stereotype.Component

@Component
class OAuth2SuccessHandler(
    private val userRepository: UserRepository,
    private val jwtTokenProvider: JwtTokenProvider,
    private val props: DayBrewProperties,
) : SimpleUrlAuthenticationSuccessHandler() {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication,
    ) {
        val frontendUrl = props.frontend.url.trimEnd('/')
        try {
            val oauthToken = authentication as OAuth2AuthenticationToken
            val oAuth2User = authentication.principal as OAuth2User
            val registrationId = oauthToken.authorizedClientRegistrationId

            val provider = when (registrationId) {
                "google" -> Provider.GOOGLE
                "kakao" -> Provider.KAKAO
                "github" -> Provider.GITHUB
                else -> throw IllegalArgumentException("Unsupported provider: $registrationId")
            }

            val email = extractEmail(provider, oAuth2User.attributes)
                ?: run {
                    log.warn("No email returned from provider=$registrationId, redirecting to error")
                    response.sendRedirect("$frontendUrl/login?error=no_email")
                    return
                }
            val providerId = extractProviderId(provider, oAuth2User.attributes)

            val user = (userRepository.findByEmail(email)
                ?: userRepository.findByProviderAndProviderId(provider, providerId)
                ?: userRepository.save(User(email = email, provider = provider, providerId = providerId)))
                .also { u ->
                    if (u.provider != provider || u.providerId != providerId) {
                        u.provider = provider
                        u.providerId = providerId
                        userRepository.save(u)
                    }
                }

            if (email == props.admin.email && user.role != UserRole.ADMIN) {
                user.role = UserRole.ADMIN
                userRepository.save(user)
            }

            val token = jwtTokenProvider.generate(user.id, user.email, user.role)
            val cookie = ResponseCookie.from("access_token", token)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(props.jwt.expirationMs / 1000)
                .sameSite("Lax")
                .build()
            response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString())
            response.sendRedirect("$frontendUrl/oauth2/callback")
        } catch (ex: Exception) {
            log.error("OAuth2 login failed, redirecting to error page", ex)
            if (!response.isCommitted) {
                response.sendRedirect("$frontendUrl/login?error=oauth_error")
            }
        }
    }

    private fun extractEmail(provider: Provider, attrs: Map<String, Any>): String? = when (provider) {
        Provider.GOOGLE, Provider.GITHUB -> attrs["email"] as? String
        Provider.KAKAO -> {
            @Suppress("UNCHECKED_CAST")
            (attrs["kakao_account"] as? Map<String, Any>)?.get("email") as? String
        }
        Provider.LOCAL -> null
    }

    private fun extractProviderId(provider: Provider, attrs: Map<String, Any>): String = when (provider) {
        Provider.KAKAO -> attrs["id"]?.toString() ?: ""
        else -> attrs["sub"]?.toString() ?: attrs["id"]?.toString() ?: ""
    }
}
