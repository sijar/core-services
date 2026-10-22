package com.example.coreservices.auth

import com.example.coreservices.common.toPostgresTimestamp
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.util.Base64
import java.util.UUID

@Service
class PasswordResetProcessor(
    private val jdbc: JdbcClient,
    private val sender: PasswordResetSender,
    private val clock: Clock,
    @Value("\${auth.reset-token-ttl:30m}") private val resetTtl: Duration
) {
    private val random = SecureRandom()

    @Async
    @Transactional
    fun enqueue(email: String) {
        val user = jdbc.sql("SELECT id,email FROM users WHERE email=:email")
            .param("email", email).query { rs, _ -> rs.getObject("id", UUID::class.java) to rs.getString("email") }.optional().orElse(null)
            ?: return
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))
        val hash = MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        jdbc.sql("INSERT INTO password_reset_tokens(id,user_id,token_hash,expires_at) VALUES (:id,:user,:hash,:expiry)")
            .param("id", UUID.randomUUID()).param("user", user.first).param("hash", hash).param("expiry", clock.instant().plus(resetTtl).toPostgresTimestamp()).update()
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() = sender.send(user.first, user.second, token)
        })
    }
}
