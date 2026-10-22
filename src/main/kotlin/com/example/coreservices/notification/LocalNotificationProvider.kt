package com.example.coreservices.notification

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@Profile("local | test")
class LocalNotificationConfiguration {
    @Bean fun localEmailProvider(mailbox: LocalMessageMailbox) = LocalNotificationProvider(NotificationChannel.EMAIL, mailbox)
    @Bean fun localSmsProvider(mailbox: LocalMessageMailbox) = LocalNotificationProvider(NotificationChannel.SMS, mailbox)
    @Bean fun localWhatsAppProvider(mailbox: LocalMessageMailbox) = LocalNotificationProvider(NotificationChannel.WHATSAPP, mailbox)
}

class LocalNotificationProvider(override val channel: NotificationChannel, private val mailbox: LocalMessageMailbox) : NotificationProvider {
    private val log = LoggerFactory.getLogger(javaClass)
    override val providerName = "local-log"

    override fun send(recipient: String, subject: String, message: String): DeliveryResult {
        val recipientHint = if (channel == NotificationChannel.EMAIL) recipient.substringAfter('@', "invalid") else "phone"
        log.info("Local notification queued: channel={} recipientHint={}", channel, recipientHint)
        val id = "local-${java.util.UUID.randomUUID()}"
        mailbox.receive(recipient, channel.name, subject, message)
        return DeliveryResult(id)
    }
}
