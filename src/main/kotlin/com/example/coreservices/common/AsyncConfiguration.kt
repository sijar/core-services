package com.example.coreservices.common

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.ThreadPoolExecutor

@Configuration
class AsyncConfiguration {
    @Bean("taskExecutor")
    fun taskExecutor(): ThreadPoolTaskExecutor = ThreadPoolTaskExecutor().apply {
        corePoolSize = 2
        maxPoolSize = 4
        setQueueCapacity(100)
        setThreadNamePrefix("core-services-async-")
        setRejectedExecutionHandler(ThreadPoolExecutor.AbortPolicy())
        setWaitForTasksToCompleteOnShutdown(true)
        setAwaitTerminationSeconds(10)
        initialize()
    }
}
