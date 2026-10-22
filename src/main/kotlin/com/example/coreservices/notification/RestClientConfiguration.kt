package com.example.coreservices.notification

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.net.http.HttpClient
import java.time.Duration

@Configuration
class RestClientConfiguration {
    @Bean
    @Primary
    fun providerRestClientBuilder(@Value("\${notifications.request-timeout:5s}") timeout: Duration): RestClient.Builder {
        val factory = JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(timeout).build()).apply {
            setReadTimeout(timeout)
        }
        return RestClient.builder().requestFactory(factory)
    }
}
