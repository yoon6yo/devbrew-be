package com.devbrew.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
@EnableConfigurationProperties(DevBrewProperties::class)
class LlmConfig {

    @Bean
    fun webClient(): WebClient = WebClient.builder().build()
}
