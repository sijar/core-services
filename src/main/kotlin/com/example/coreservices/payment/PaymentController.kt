package com.example.coreservices.payment

import com.example.coreservices.security.UserPrincipal
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class CreatePaymentRequest(@field:NotBlank @field:Pattern(regexp = "^[a-z0-9-]{1,64}$") val productCode: String)

@RestController
@RequestMapping("/api/payments")
class PaymentController(private val payments: PaymentService) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@AuthenticationPrincipal user: UserPrincipal, @Valid @RequestBody body: CreatePaymentRequest) = payments.create(user.id, body.productCode)

    @GetMapping
    fun list(@AuthenticationPrincipal user: UserPrincipal) = payments.list(user.id)

    @GetMapping("/{id}")
    fun get(@AuthenticationPrincipal user: UserPrincipal, @PathVariable id: UUID) = payments.get(user.id, id)
}

@RestController
class StripeWebhookController(private val payments: PaymentService) {
    @PostMapping("/api/webhooks/stripe")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun receive(@RequestBody payload: String, @RequestHeader("Stripe-Signature", required = false) signature: String?) = payments.processStripeWebhook(payload, signature)
}
