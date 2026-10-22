package com.example.coreservices.payment

import com.example.coreservices.common.ApiException
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class StripeWebhookVerifierTest {
    private val secret = "whsec_test_secret"

    @Test
    fun `accepts a valid Stripe signature`() {
        val timestamp = Instant.now().epochSecond
        val payload = JSONObject()
            .put("id", "evt_test_webhook")
            .put("object", "event")
            .put("api_version", "2025-06-30.basil")
            .put("created", timestamp)
            .put("data", JSONObject().put("object", JSONObject().put("id", "cs_test_123").put("object", "checkout.session")))
            .put("livemode", false)
            .put("pending_webhooks", 1)
            .put("request", JSONObject.NULL)
            .put("type", "checkout.session.completed")
            .toString()
        val signature = signatureHeader(timestamp, payload)

        assertEquals("evt_test_webhook", StripeWebhookVerifier(secret).verify(payload, signature).id)
    }

    @Test
    fun `rejects an invalid Stripe signature`() {
        assertThrows(ApiException::class.java) {
            StripeWebhookVerifier(secret).verify("{}", "t=1,v1=invalid")
        }
    }

    private fun signatureHeader(timestamp: Long, payload: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        val signature = mac.doFinal("$timestamp.$payload".toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "t=$timestamp,v1=$signature"
    }
}
