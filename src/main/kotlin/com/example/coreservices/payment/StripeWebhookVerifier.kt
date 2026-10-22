package com.example.coreservices.payment

import com.example.coreservices.common.ApiException
import com.stripe.exception.StripeException
import com.stripe.model.Event
import com.stripe.net.Webhook
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component

@Component
class StripeWebhookVerifier(@Value("\${payments.stripe.webhook-secret:}") private val secret: String) {
    fun verify(payload: String, signature: String?): Event {
        if (secret.isBlank()) throw ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Payment webhook is not configured")
        return try {
            Webhook.constructEvent(payload, signature ?: "", secret)
        } catch (_: StripeException) {
            throw ApiException(HttpStatus.BAD_REQUEST, "Invalid webhook signature")
        }
    }
}
