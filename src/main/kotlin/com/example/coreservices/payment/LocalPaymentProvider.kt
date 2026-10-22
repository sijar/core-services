package com.example.coreservices.payment

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.util.UUID

@Component
@Profile("local | test")
class LocalPaymentProvider : PaymentProvider {
    override val name = "local-fake"
    override fun createCheckout(paymentId: UUID, productName: String, amountMinor: Long, currency: String) =
        CheckoutSession("local-$paymentId", "http://localhost:8080/?payment=pending&id=$paymentId")
}
