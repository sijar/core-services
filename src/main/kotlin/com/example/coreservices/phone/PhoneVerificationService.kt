package com.example.coreservices.phone

import com.example.coreservices.common.ApiException
import com.example.coreservices.common.toPostgresTimestamp
import com.example.coreservices.notification.NotificationChannel
import com.example.coreservices.notification.NotificationService
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Service
class PhoneVerificationService(
    private val jdbc: JdbcClient,
    private val notifications: NotificationService,
    private val clock: Clock,
    @Value("\${phone.otp-hmac-secret:}") private val hmacSecret: String,
    @Value("\${phone.code-ttl:10m}") private val codeTtl: Duration,
    @Value("\${phone.resend-cooldown:60s}") private val resendCooldown: Duration,
    @Value("\${phone.max-attempts:5}") private val maxAttempts: Int
) {
    private val random = SecureRandom()

    @Transactional
    fun start(userId: UUID, phone: String) {
        requireSecret()
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))")
            .param("key", "phone-otp:$userId").query { _, _ -> true }.single()
        val recent = jdbc.sql("SELECT count(*) FROM phone_verification_challenges WHERE user_id=:user AND created_at > :after")
            .param("user", userId).param("after", clock.instant().minus(resendCooldown).toPostgresTimestamp()).query(Long::class.java).single()
        if (recent > 0) throw ApiException(HttpStatus.TOO_MANY_REQUESTS, "Please wait before requesting another code")

        val id = UUID.randomUUID()
        val code = "%06d".format(random.nextInt(1_000_000))
        jdbc.sql("UPDATE users SET phone_number=:phone,phone_verified_at=NULL,updated_at=:now WHERE id=:user")
            .param("phone", phone).param("now", clock.instant().toPostgresTimestamp()).param("user", userId).update()
        jdbc.sql("INSERT INTO phone_verification_challenges(id,user_id,phone_number,code_hash,max_attempts,expires_at) VALUES (:id,:user,:phone,:hash,:max,:expiry)")
            .param("id", id).param("user", userId).param("phone", phone).param("hash", hmac(id, phone, code))
            .param("max", maxAttempts).param("expiry", clock.instant().plus(codeTtl).toPostgresTimestamp()).update()
        notifications.send(userId, NotificationChannel.SMS, phone, "PHONE_OTP", "Verify your phone", "Your verification code is $code. It expires in 10 minutes.", true)
    }

    @Transactional
    fun confirm(userId: UUID, code: String) {
        requireSecret()
        val challenge = jdbc.sql("SELECT id,phone_number,code_hash FROM phone_verification_challenges WHERE user_id=:user AND consumed_at IS NULL AND expires_at > :now ORDER BY created_at DESC LIMIT 1")
            .param("user", userId).param("now", clock.instant().toPostgresTimestamp())
            .query { rs, _ -> Challenge(rs.getObject("id", UUID::class.java), rs.getString("phone_number"), rs.getString("code_hash")) }
            .optional().orElse(null) ?: throw ApiException(HttpStatus.BAD_REQUEST, "Invalid or expired verification code")

        val expected = hmac(challenge.id, challenge.phone, code)
        val valid = MessageDigest.isEqual(expected.toByteArray(StandardCharsets.US_ASCII), challenge.hash.toByteArray(StandardCharsets.US_ASCII))
        if (!valid) {
            jdbc.sql("UPDATE phone_verification_challenges SET attempts=attempts+1,consumed_at=CASE WHEN attempts+1 >= max_attempts THEN now() ELSE consumed_at END WHERE id=:id AND consumed_at IS NULL AND expires_at > now() AND attempts < max_attempts")
                .param("id", challenge.id).update()
            throw ApiException(HttpStatus.BAD_REQUEST, "Invalid or expired verification code")
        }

        val consumed = jdbc.sql("UPDATE phone_verification_challenges SET consumed_at=now() WHERE id=:id AND consumed_at IS NULL AND expires_at > now() AND attempts < max_attempts RETURNING user_id")
            .param("id", challenge.id).query(UUID::class.java).optional().orElse(null)
            ?: throw ApiException(HttpStatus.BAD_REQUEST, "Invalid or expired verification code")
        jdbc.sql("UPDATE users SET phone_number=:phone,phone_verified_at=:now,updated_at=:now WHERE id=:user")
            .param("phone", challenge.phone).param("now", clock.instant().toPostgresTimestamp()).param("user", consumed).update()
    }

    fun status(userId: UUID): Map<String, Any?> = jdbc.sql("SELECT phone_number,phone_verified_at FROM users WHERE id=:user")
        .param("user", userId).query { rs, _ ->
            mapOf("phoneNumber" to rs.getString("phone_number"), "verified" to (rs.getTimestamp("phone_verified_at") != null),
                "verifiedAt" to rs.getTimestamp("phone_verified_at")?.toInstant())
        }.single()

    private fun requireSecret() {
        if (hmacSecret.toByteArray(StandardCharsets.UTF_8).size < 32) {
            throw IllegalStateException("OTP_HMAC_SECRET must contain at least 32 bytes")
        }
    }

    private fun hmac(id: UUID, phone: String, code: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(hmacSecret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        return mac.doFinal("$id|$phone|$code".toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private data class Challenge(val id: UUID, val phone: String, val hash: String)
}
