package com.example.coreservices.payment

import com.example.coreservices.common.ApiException
import com.example.coreservices.common.toPostgresTimestamp
import com.example.coreservices.notification.NotificationChannel
import com.example.coreservices.notification.NotificationService
import org.json.JSONObject
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.Clock
import java.util.UUID

data class PaymentProduct(val name: String, val amountMinor: Long, val currency: String)

@Service
class PaymentService(
    private val jdbc: JdbcClient,
    private val provider: PaymentProvider,
    private val notifications: NotificationService,
    private val webhookVerifier: StripeWebhookVerifier,
    private val clock: Clock,
    @Value("\${payments.catalog.demo-item.name}") private val productName: String,
    @Value("\${payments.catalog.demo-item.amount-minor}") private val productAmount: Long,
    @Value("\${payments.catalog.demo-item.currency}") private val productCurrency: String,
) {
    fun create(userId: UUID, productCode: String): Map<String, Any> {
        if (productCode != "demo-item") throw ApiException(HttpStatus.BAD_REQUEST, "Unknown product")
        if (productAmount <= 0 || !productCurrency.matches(Regex("[a-z]{3}"))) throw IllegalStateException("Invalid server-side payment catalog configuration")
        val id = UUID.randomUUID()
        jdbc.sql("INSERT INTO payments(id,user_id,product_code,amount_minor,currency,status,provider,checkout_url) VALUES (:id,:user,:product,:amount,:currency,'PENDING',:provider,'pending')")
            .param("id", id).param("user", userId).param("product", productCode).param("amount", productAmount)
            .param("currency", productCurrency).param("provider", provider.name).update()
        try {
            val checkout = provider.createCheckout(id, productName, productAmount, productCurrency)
            jdbc.sql("UPDATE payments SET provider_session_id=:session,checkout_url=:url,updated_at=:now WHERE id=:id AND status='PENDING'")
                .param("session", checkout.id).param("url", checkout.url).param("now", clock.instant().toPostgresTimestamp()).param("id", id).update()
            return mapOf("id" to id, "status" to "PENDING", "checkoutUrl" to checkout.url, "amountMinor" to productAmount, "currency" to productCurrency)
        } catch (failure: PaymentProviderException) {
            jdbc.sql("UPDATE payments SET status='FAILED',updated_at=:now WHERE id=:id AND status='PENDING'")
                .param("now", clock.instant().toPostgresTimestamp()).param("id", id).update()
            throw ApiException(HttpStatus.BAD_GATEWAY, "Unable to create checkout session")
        }
    }

    fun list(userId: UUID): List<Map<String, Any?>> = jdbc.sql("SELECT id,product_code,amount_minor,currency,status,checkout_url,created_at,paid_at FROM payments WHERE user_id=:user ORDER BY created_at DESC LIMIT 100")
        .param("user", userId).query { rs, _ -> paymentRow(rs) }.list()

    fun get(userId: UUID, id: UUID): Map<String, Any?> = jdbc.sql("SELECT id,product_code,amount_minor,currency,status,checkout_url,created_at,paid_at FROM payments WHERE user_id=:user AND id=:id")
        .param("user", userId).param("id", id).query { rs, _ -> paymentRow(rs) }.optional().orElse(null)
        ?: throw ApiException(HttpStatus.NOT_FOUND, "Payment not found")

    @Transactional
    fun processStripeWebhook(payload: String, signature: String?) {
        val event = webhookVerifier.verify(payload, signature)
        val eventType = event.type ?: "unknown"
        val objectJson = event.dataObjectDeserializer.rawJson?.toString()
        val session = objectJson?.let(::JSONObject)
        val paymentId = session?.optJSONObject("metadata")?.optString("payment_id")?.let { value ->
            try { UUID.fromString(value) } catch (_: IllegalArgumentException) { null }
        }
        val row = paymentId?.let { id -> jdbc.sql("SELECT id,user_id,product_code,amount_minor,currency,status,provider,provider_session_id FROM payments WHERE id=:id FOR UPDATE")
            .param("id", id).query { rs, _ -> PaymentRow(rs.getObject("id", UUID::class.java), rs.getObject("user_id", UUID::class.java), rs.getString("product_code"), rs.getLong("amount_minor"), rs.getString("currency"), rs.getString("status"), rs.getString("provider"), rs.getString("provider_session_id")) }.optional().orElse(null) }

        val insert = jdbc.sql("INSERT INTO payment_webhook_events(id,provider,provider_event_id,event_type,payment_id,outcome,processed_at) VALUES (:id,'stripe',:event,:type,:payment,'IGNORED',:now) ON CONFLICT(provider,provider_event_id) DO NOTHING RETURNING id")
            .param("id", UUID.randomUUID()).param("event", event.id).param("type", eventType).param("payment", row?.id).param("now", clock.instant().toPostgresTimestamp())
            .query(UUID::class.java).optional().orElse(null)
        if (insert == null) return

        if (eventType !in setOf("checkout.session.completed", "checkout.session.async_payment_succeeded", "checkout.session.async_payment_failed", "checkout.session.expired") || row == null || session == null) return
        if (row.provider != "stripe" || row.providerSessionId != session.optString("id")) {
            rejectWebhook(insert)
            return
        }
        val reportedAmount = session.optLong("amount_total", -1)
        val reportedCurrency = session.optString("currency")
        if (reportedAmount != row.amountMinor || reportedCurrency != row.currency) {
            rejectWebhook(insert)
            return
        }

        val nextStatus = when (eventType) {
            "checkout.session.expired" -> "EXPIRED"
            "checkout.session.async_payment_failed" -> "FAILED"
            else -> {
                if (session.optString("payment_status") != "paid") return
                "PAID"
            }
        }
        if (row.status != "PENDING") return
        val update = if (nextStatus == "PAID") {
            jdbc.sql("UPDATE payments SET status='PAID',paid_at=:now,updated_at=:now,provider_payment_intent_id=:intent WHERE id=:id AND status='PENDING'")
                .param("now", clock.instant().toPostgresTimestamp()).param("intent", session.optString("payment_intent").takeIf(String::isNotBlank)).param("id", row.id).update()
        } else {
            jdbc.sql("UPDATE payments SET status=:status,updated_at=:now WHERE id=:id AND status='PENDING'")
                .param("status", nextStatus).param("now", clock.instant().toPostgresTimestamp()).param("id", row.id).update()
        }
        jdbc.sql("UPDATE payment_webhook_events SET outcome=:outcome,processed_at=:now WHERE id=:id")
            .param("outcome", if (update == 1) "PROCESSED" else "IGNORED").param("now", clock.instant().toPostgresTimestamp()).param("id", insert).update()

        if (update == 1 && nextStatus == "PAID") {
            val recipient = jdbc.sql("SELECT email FROM users WHERE id=:id").param("id", row.userId).query(String::class.java).single()
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() {
                    notifications.send(row.userId, NotificationChannel.EMAIL, recipient, "PAYMENT_CONFIRMATION", "Payment confirmed", "Your payment for ${row.productCode} has been confirmed.")
                }
            })
        }
    }

    private fun paymentRow(rs: java.sql.ResultSet): Map<String, Any?> = mapOf(
        "id" to rs.getObject("id", UUID::class.java), "productCode" to rs.getString("product_code"),
        "amountMinor" to rs.getLong("amount_minor"), "currency" to rs.getString("currency"),
        "status" to rs.getString("status"), "checkoutUrl" to rs.getString("checkout_url"),
        "createdAt" to rs.getTimestamp("created_at").toInstant(), "paidAt" to rs.getTimestamp("paid_at")?.toInstant()
    )

    private fun rejectWebhook(eventId: UUID) {
        jdbc.sql("UPDATE payment_webhook_events SET outcome='REJECTED',processed_at=:now WHERE id=:id")
            .param("now", clock.instant().toPostgresTimestamp()).param("id", eventId).update()
    }

    private data class PaymentRow(val id: UUID, val userId: UUID, val productCode: String, val amountMinor: Long, val currency: String, val status: String, val provider: String, val providerSessionId: String?)
}
