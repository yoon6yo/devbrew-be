package com.devbrew

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class DevBrewApplication

fun main(args: Array<String>) {
    runApplication<DevBrewApplication>(*args)
}
