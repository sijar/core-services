package com.example.coreservices.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.session.SessionRegistry
import org.springframework.security.core.session.SessionRegistryImpl
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler
import org.springframework.security.web.session.HttpSessionEventPublisher

@Configuration
@EnableWebSecurity
class SecurityConfiguration {
    @Bean
    fun passwordEncoder(): PasswordEncoder = Argon2PasswordEncoder(16, 32, 1, 19 * 1024, 2)

    @Bean
    fun sessionRegistry(): SessionRegistry = SessionRegistryImpl()

    @Bean
    fun httpSessionEventPublisher() = HttpSessionEventPublisher()

    @Bean
    fun securityFilterChain(http: HttpSecurity, sessionRegistry: SessionRegistry): SecurityFilterChain {
        val csrfHandler = CsrfTokenRequestAttributeHandler()
        http
            .csrf { csrf ->
                csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(csrfHandler)
                    .ignoringRequestMatchers("/api/webhooks/stripe")
            }
            .authorizeHttpRequests { auth ->
                auth.requestMatchers("/", "/index.html", "/app.js", "/styles.css", "/actuator/health", "/api/csrf", "/api/auth/register", "/api/auth/login", "/api/auth/password-reset/request", "/api/auth/password-reset/confirm", "/api/webhooks/stripe", "/dev/mailbox", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                    .anyRequest().authenticated()
            }
            .exceptionHandling { errors ->
                errors.authenticationEntryPoint { _, response, _ ->
                    response.status = 401
                    response.contentType = "application/json"
                    response.writer.write("{\"error\":\"Authentication required\"}")
                }
            }
            .sessionManagement { sessions ->
                sessions.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                    .sessionFixation { fixation -> fixation.changeSessionId() }
            }
            .logout { it.disable() }
        http.addFilterBefore(ExpiredSessionFilter(sessionRegistry), AnonymousAuthenticationFilter::class.java)
        return http.build()
    }
}
