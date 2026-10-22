package com.example.coreservices.notification

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.context.annotation.Profile
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/dev/mailbox")
@Profile("local | test")
@Validated
class LocalMailboxController(private val mailbox: LocalMessageMailbox) {
    @GetMapping
    fun read(@RequestParam @NotBlank @Size(max = 254) recipient: String) = mailbox.read(recipient.trim().lowercase())
}
