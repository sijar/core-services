package com.example.coreservices.payment

import com.stripe.Stripe
import com.stripe.exception.StripeException
import com.stripe.model.checkout.Session
import com.stripe.net.RequestOptions
import com.stripe.param.checkout.SessionCreateParams
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import jakarta.annotation.PostConstruct
import java.util.UUID

@Component
@Profile("!local & !test")
class StripePaymentProvider(
    @Value("\${payments.stripe.secret-key:}") private val secretKey: String,
    @Value("\${payments.stripe.webhook-secret:}") private val webhookSecret: String,
    @Value("\${payments.success-url}") private val successUrl: String,
    @Value("\${payments.cancel-url}") private val cancelUrl: String
) : PaymentProvider {
    override val name = "stripe"

    @PostConstruct
    fun validateConfiguration() {
        check(secretKey.startsWith("sk_test_")) { "Stripe test secret key is required in the prod profile" }
        check(webhookSecret.startsWith("whsec_")) { "Stripe webhook secret is required in the prod profile" }
        check(successUrl.startsWith("https://") && cancelUrl.startsWith("https://")) { "Stripe success and cancel URLs must use HTTPS in the prod profile" }
    }

    override fun createCheckout(paymentId: UUID, productName: String, amountMinor: Long, currency: String): CheckoutSession {
        check(secretKey.startsWith("sk_test_")) { "Stripe test secret key is required" }
        val params = SessionCreateParams.builder()
            .setMode(SessionCreateParams.Mode.PAYMENT)
            .setSuccessUrl(successUrl)
            .setCancelUrl(cancelUrl)
            .putMetadata("payment_id", paymentId.toString())
            .addLineItem(
                SessionCreateParams.LineItem.builder().setQuantity(1L)
                    .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                        .setCurrency(currency)
                        .setUnitAmount(amountMinor)
                        .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder().setName(productName).build())
                        .build())
                    .build()
            )
            .build()
        val session = try {
            Session.create(params, RequestOptions.builder().setApiKey(secretKey).build())
        } catch (failure: StripeException) {
            throw PaymentProviderException(failure)
        }
        return CheckoutSession(session.id ?: throw PaymentProviderException(IllegalStateException("Stripe session id missing")), session.url ?: throw PaymentProviderException(IllegalStateException("Stripe checkout URL missing")))
    }
}
