package com.example.coreservices.notification

enum class NotificationChannel { EMAIL, SMS, WHATSAPP }

data class DeliveryResult(val providerMessageId: String?)

interface NotificationProvider {
    val channel: NotificationChannel
    val providerName: String
    fun send(recipient: String, subject: String, message: String): DeliveryResult
}
