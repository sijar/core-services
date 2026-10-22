package com.example.coreservices.notification

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.http.MediaType
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import java.net.URI

@Component
@Profile("!local & !test")
class SmtpEmailProvider(
    private val mailSender: JavaMailSender,
    @Value("\${SMTP_FROM:}") private val from: String
) : NotificationProvider {
    override val channel = NotificationChannel.EMAIL
    override val providerName = "smtp"

    override fun send(recipient: String, subject: String, message: String): DeliveryResult {
        require(from.isNotBlank()) { "SMTP_FROM is required" }
        mailSender.send(SimpleMailMessage().apply {
            setFrom(from)
            setTo(recipient)
            setSubject(subject)
            setText(message)
        })
        return DeliveryResult(null)
    }
}

@Component
@Profile("!local & !test")
class TwilioSmsProvider(
    private val client: RestClient.Builder,
    @Value("\${TWILIO_ACCOUNT_SID:}") private val accountSid: String,
    @Value("\${TWILIO_AUTH_TOKEN:}") private val authToken: String,
    @Value("\${TWILIO_FROM:}") private val from: String
) : NotificationProvider {
    override val channel = NotificationChannel.SMS
    override val providerName = "twilio"

    override fun send(recipient: String, subject: String, message: String): DeliveryResult {
        require(accountSid.isNotBlank() && authToken.isNotBlank() && from.isNotBlank()) { "Twilio SMS credentials are required" }
        val form = LinkedMultiValueMap<String, String>().apply { add("To", recipient); add("From", from); add("Body", message) }
        client.build().post()
            .uri("https://api.twilio.com/2010-04-01/Accounts/$accountSid/Messages.json")
            .headers { it.setBasicAuth(accountSid, authToken) }
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve().toBodilessEntity()
        return DeliveryResult(null)
    }
}

@Component
@Profile("!local & !test")
class WhatsAppCloudProvider(
    private val client: RestClient.Builder,
    @Value("\${WHATSAPP_ACCESS_TOKEN:}") private val accessToken: String,
    @Value("\${WHATSAPP_PHONE_NUMBER_ID:}") private val phoneNumberId: String,
    @Value("\${WHATSAPP_API_VERSION:v22.0}") private val apiVersion: String
) : NotificationProvider {
    override val channel = NotificationChannel.WHATSAPP
    override val providerName = "whatsapp-cloud"

    override fun send(recipient: String, subject: String, message: String): DeliveryResult {
        require(accessToken.isNotBlank() && phoneNumberId.isNotBlank()) { "WhatsApp Cloud credentials are required" }
        val payload = mapOf(
            "messaging_product" to "whatsapp",
            "to" to recipient,
            "type" to "text",
            "text" to mapOf("preview_url" to false, "body" to message)
        )
        client.build().post()
            .uri(URI.create("https://graph.facebook.com/$apiVersion/$phoneNumberId/messages"))
            .headers { it.setBearerAuth(accessToken) }
            .contentType(MediaType.APPLICATION_JSON)
            .body(payload)
            .retrieve().toBodilessEntity()
        return DeliveryResult(null)
    }
}
