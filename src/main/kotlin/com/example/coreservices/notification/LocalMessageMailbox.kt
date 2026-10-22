package com.example.coreservices.notification

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant

data class LocalMailboxMessage(val channel: String, val subject: String, val message: String, val receivedAt: Instant)

@Component
@Profile("local | test")
class LocalMessageMailbox(private val clock: Clock) {
    private val messages = Caffeine.newBuilder().maximumSize(1_000).expireAfterWrite(Duration.ofMinutes(30)).build<String, List<LocalMailboxMessage>>()

    @Synchronized
    fun receive(recipient: String, channel: String, subject: String, message: String) {
        val inbox = messages.getIfPresent(recipient).orEmpty()
        messages.put(recipient, (inbox + LocalMailboxMessage(channel, subject, message, clock.instant())).takeLast(50))
    }

    fun read(recipient: String): List<LocalMailboxMessage> = messages.getIfPresent(recipient).orEmpty()
}
