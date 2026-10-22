package com.example.coreservices.phone

import com.example.coreservices.security.UserPrincipal
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

data class PhoneStartRequest(@field:Pattern(regexp = "^\\+[1-9][0-9]{7,14}$") val phoneNumber: String)
data class PhoneConfirmRequest(@field:NotBlank @field:Pattern(regexp = "^[0-9]{6}$") val code: String)

@RestController
@RequestMapping("/api/phone/verification")
class PhoneVerificationController(private val verification: PhoneVerificationService) {
    @GetMapping
    fun status(@AuthenticationPrincipal user: UserPrincipal) = verification.status(user.id)

    @PostMapping("/start")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun start(@AuthenticationPrincipal user: UserPrincipal, @Valid @RequestBody body: PhoneStartRequest): Map<String, String> {
        verification.start(user.id, body.phoneNumber)
        return mapOf("message" to "If delivery is available, a verification code will arrive shortly")
    }

    @PostMapping("/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun confirm(@AuthenticationPrincipal user: UserPrincipal, @Valid @RequestBody body: PhoneConfirmRequest) = verification.confirm(user.id, body.code)
}
