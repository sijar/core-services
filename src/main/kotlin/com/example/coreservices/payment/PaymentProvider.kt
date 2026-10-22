package com.example.coreservices.payment

import java.util.UUID

data class CheckoutSession(val id: String, val url: String)

class PaymentProviderException(cause: Throwable) : RuntimeException("Payment provider request failed", cause)

interface PaymentProvider {
    val name: String
    fun createCheckout(paymentId: UUID, productName: String, amountMinor: Long, currency: String): CheckoutSession
}
