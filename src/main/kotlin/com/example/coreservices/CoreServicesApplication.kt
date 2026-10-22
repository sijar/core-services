package com.example.coreservices

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync

@SpringBootApplication
@EnableAsync
class CoreServicesApplication

fun main(args: Array<String>) {
    runApplication<CoreServicesApplication>(*args)
}
