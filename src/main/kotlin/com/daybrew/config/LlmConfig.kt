package com.daybrew.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.netty.channel.ChannelOption
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.time.Duration

@Configuration
@EnableConfigurationProperties(DayBrewProperties::class)
class LlmConfig {

    @Bean
    fun objectMapper(): ObjectMapper = ObjectMapper().apply { registerKotlinModule() }

    @Bean
    fun webClient(): WebClient {
        val httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
            .responseTimeout(Duration.ofSeconds(90))
        return WebClient.builder()
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .codecs { it.defaultCodecs().maxInMemorySize(10 * 1024 * 1024) }
            .build()
    }
}
