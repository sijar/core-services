package com.example.coreservices.auth

import com.example.coreservices.notification.NotificationChannel
import com.example.coreservices.notification.NotificationService
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import java.util.UUID

@Component
@Profile("local | test")
class LocalPasswordResetSender(
    private val mailbox: com.example.coreservices.notification.LocalMessageMailbox,
    @Value("\${auth.base-url}") private val baseUrl: String
) : PasswordResetSender {

    @Async
    override fun send(userId: UUID, email: String, token: String) {
        mailbox.receive(email, "EMAIL", "Reset your password", "Open this local-development reset link within 30 minutes: $baseUrl/#resetToken=$token")
    }
}

@Component
@Profile("!local & !test")
class NotificationPasswordResetSender(
    private val notifications: NotificationService,
    @Value("\${auth.base-url}") private val baseUrl: String
) : PasswordResetSender {
    @Async
    override fun send(userId: UUID, email: String, token: String) {
        val link = "$baseUrl/#resetToken=$token"
        notifications.send(userId, NotificationChannel.EMAIL, email, "PASSWORD_RESET", "Reset your password", "Use this one-time link within 30 minutes to reset your password: $link", true)
    }
}
