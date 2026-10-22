package com.example.coreservices.notification

import com.example.coreservices.common.ApiException
import com.example.coreservices.common.toPostgresTimestamp
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.mail.MailException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestClientException
import java.time.Clock
import java.time.Duration
import java.util.UUID

@Service
class NotificationService(
    private val jdbc: JdbcClient,
    providers: List<NotificationProvider>,
    private val clock: Clock,
    @Value("\${notifications.retries:3}") private val maxAttempts: Int,
    @Value("\${notifications.rate-limit.per-recipient-hour:5}") private val recipientLimit: Int,
    @Value("\${notifications.rate-limit.per-user-day:20}") private val userDailyLimit: Int
) {
    private val providers = providers.associateBy { it.channel }

    init {
        require(maxAttempts in 1..5) { "Notification attempts must be between 1 and 5" }
    }

    @Transactional
    fun send(userId: UUID?, channel: NotificationChannel, recipient: String, type: String, subject: String, message: String, critical: Boolean = false): UUID {
        require(recipient.isNotBlank() && type.matches(Regex("[A-Z_]{2,40}")))
        val provider = providers[channel] ?: throw IllegalStateException("No provider configured for $channel")
        acquireRateLimitLock("recipient:$channel:$recipient")
        userId?.let { acquireRateLimitLock("user:$it") }
        val id = UUID.randomUUID()
        jdbc.sql("INSERT INTO notifications (id,user_id,channel,type,recipient,status,provider) VALUES (:id,:user,:channel,:type,:recipient,'PENDING',:provider)")
            .param("id", id).param("user", userId).param("channel", channel.name).param("type", type)
            .param("recipient", recipient).param("provider", provider.providerName).update()

        val enabled = if (userId == null) channel == NotificationChannel.EMAIL else preferenceEnabled(userId, channel)
        if ((!critical && !enabled) || isRateLimited(id, userId, channel, recipient)) {
            jdbc.sql("UPDATE notifications SET status='SUPPRESSED', updated_at=:now WHERE id=:id")
                .param("now", clock.instant().toPostgresTimestamp()).param("id", id).update()
            return id
        }

        var lastError = "Provider delivery failed"
        repeat(maxAttempts) { attemptIndex ->
            val started = clock.instant()
            var result: DeliveryResult? = null
            var error: String? = null
            try {
                result = provider.send(recipient, subject, message)
            } catch (failure: MailException) {
                lastError = "Provider error (${failure.javaClass.simpleName})"
                error = lastError
                if (attemptIndex + 1 < maxAttempts) pauseBeforeRetry(attemptIndex)
            } catch (failure: RestClientException) {
                lastError = "Provider error (${failure.javaClass.simpleName})"
                error = lastError
                if (attemptIndex + 1 < maxAttempts) pauseBeforeRetry(attemptIndex)
            }
            if (result != null) {
                recordAttempt(id, attemptIndex + 1, "SENT", null, started)
                jdbc.sql("UPDATE notifications SET status='SENT', attempts=:attempts,provider_message_id=:messageId,last_error=NULL,updated_at=:now WHERE id=:id")
                    .param("attempts", attemptIndex + 1).param("messageId", result.providerMessageId).param("now", clock.instant().toPostgresTimestamp()).param("id", id).update()
                return id
            }
            recordAttempt(id, attemptIndex + 1, "FAILED", error, started)
        }
        jdbc.sql("UPDATE notifications SET status='FAILED', attempts=:attempts,last_error=:error,updated_at=:now WHERE id=:id")
            .param("attempts", maxAttempts).param("error", lastError).param("now", clock.instant().toPostgresTimestamp()).param("id", id).update()
        return id
    }

    fun list(userId: UUID): List<Map<String, Any?>> = jdbc.sql("SELECT id,channel,type,status,provider,provider_message_id,attempts,last_error,created_at FROM notifications WHERE user_id=:user ORDER BY created_at DESC LIMIT 100")
        .param("user", userId).query { rs, _ ->
            mapOf("id" to rs.getObject("id", UUID::class.java), "channel" to rs.getString("channel"), "type" to rs.getString("type"),
                "status" to rs.getString("status"), "provider" to rs.getString("provider"), "providerMessageId" to rs.getString("provider_message_id"),
                "attempts" to rs.getInt("attempts"), "lastError" to rs.getString("last_error"), "createdAt" to rs.getTimestamp("created_at").toInstant())
        }.list()

    fun preferences(userId: UUID): Map<String, Boolean> {
        val result = NotificationChannel.entries.associate { it.name to (it == NotificationChannel.EMAIL) }.toMutableMap()
        jdbc.sql("SELECT channel,enabled FROM notification_preferences WHERE user_id=:user")
            .param("user", userId).query { rs, _ -> result[rs.getString("channel")] = rs.getBoolean("enabled") }.list()
        return result
    }

    @Transactional
    fun setPreference(userId: UUID, channel: NotificationChannel, enabled: Boolean) {
        jdbc.sql("INSERT INTO notification_preferences(user_id,channel,enabled,consent_recorded_at,updated_at) VALUES (:user,:channel,:enabled,:now,:now) ON CONFLICT(user_id,channel) DO UPDATE SET enabled=EXCLUDED.enabled,consent_recorded_at=EXCLUDED.consent_recorded_at,updated_at=EXCLUDED.updated_at")
            .param("user", userId).param("channel", channel.name).param("enabled", enabled).param("now", clock.instant().toPostgresTimestamp()).update()
    }

    @Transactional
    fun setPreferences(userId: UUID, preferences: Map<NotificationChannel, Boolean>) {
        preferences.forEach { (channel, enabled) ->
            jdbc.sql("INSERT INTO notification_preferences(user_id,channel,enabled,consent_recorded_at,updated_at) VALUES (:user,:channel,:enabled,:now,:now) ON CONFLICT(user_id,channel) DO UPDATE SET enabled=EXCLUDED.enabled,consent_recorded_at=EXCLUDED.consent_recorded_at,updated_at=EXCLUDED.updated_at")
                .param("user", userId).param("channel", channel.name).param("enabled", enabled).param("now", clock.instant().toPostgresTimestamp()).update()
        }
    }

    private fun preferenceEnabled(userId: UUID, channel: NotificationChannel): Boolean {
        if (channel == NotificationChannel.EMAIL) return jdbc.sql("SELECT enabled FROM notification_preferences WHERE user_id=:user AND channel=:channel")
            .param("user", userId).param("channel", channel.name).query(Boolean::class.java).optional().orElse(true)
        return jdbc.sql("SELECT enabled FROM notification_preferences WHERE user_id=:user AND channel=:channel")
            .param("user", userId).param("channel", channel.name).query(Boolean::class.java).optional().orElse(false)
    }

    private fun isRateLimited(id: UUID, userId: UUID?, channel: NotificationChannel, recipient: String): Boolean {
        val recentRecipient = jdbc.sql("SELECT count(*) FROM notifications WHERE id <> :id AND recipient=:recipient AND channel=:channel AND created_at > :after AND status <> 'SUPPRESSED'")
            .param("id", id).param("recipient", recipient).param("channel", channel.name).param("after", clock.instant().minus(Duration.ofHours(1)).toPostgresTimestamp())
            .query(Long::class.java).single()
        if (recentRecipient >= recipientLimit) return true
        if (userId == null) return false
        val recentUser = jdbc.sql("SELECT count(*) FROM notifications WHERE id <> :id AND user_id=:user AND created_at > :after AND status <> 'SUPPRESSED'")
            .param("id", id).param("user", userId).param("after", clock.instant().minus(Duration.ofDays(1)).toPostgresTimestamp()).query(Long::class.java).single()
        return recentUser >= userDailyLimit
    }

    private fun recordAttempt(id: UUID, number: Int, outcome: String, error: String?, started: java.time.Instant) {
        jdbc.sql("INSERT INTO notification_attempts(id,notification_id,attempt_number,outcome,error,started_at,finished_at) VALUES (:id,:notification,:number,:outcome,:error,:started,:finished)")
            .param("id", UUID.randomUUID()).param("notification", id).param("number", number).param("outcome", outcome)
            .param("error", error).param("started", started.toPostgresTimestamp()).param("finished", clock.instant().toPostgresTimestamp()).update()
    }

    private fun pauseBeforeRetry(attemptIndex: Int) {
        try {
            Thread.sleep(250L shl attemptIndex)
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            throw interrupted
        }
    }

    private fun acquireRateLimitLock(key: String) {
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))")
            .param("key", key).query { _, _ -> true }.single()
    }

    fun requireChannel(value: String): NotificationChannel = try {
        NotificationChannel.valueOf(value.uppercase())
    } catch (_: IllegalArgumentException) {
        throw ApiException(HttpStatus.BAD_REQUEST, "Unsupported notification channel")
    }
}
