package com.example.coreservices.auth

import com.example.coreservices.security.UserPrincipal
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.ResponseStatus

data class CredentialsRequest(
    @field:Email @field:NotBlank @field:Size(max = 254) val email: String,
    @field:NotBlank @field:Size(max = 128) val password: String
)

data class ResetRequest(@field:Email @field:NotBlank @field:Size(max = 254) val email: String)
data class ResetConfirmRequest(@field:NotBlank @field:Size(max = 128) val token: String, @field:NotBlank @field:Size(max = 128) val newPassword: String)

@RestController
class AuthController(private val auth: AuthService) {
    @GetMapping("/api/csrf")
    fun csrf(token: CsrfToken): Map<String, String> = mapOf("headerName" to token.headerName, "token" to token.token)

    @PostMapping("/api/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    fun register(@Valid @RequestBody body: CredentialsRequest, request: HttpServletRequest): Map<String, Any> =
        auth.register(body.email, body.password, request.remoteAddr ?: "unknown").let { mapOf("id" to it.id, "email" to it.email) }

    @PostMapping("/api/auth/login")
    fun login(@Valid @RequestBody body: CredentialsRequest, request: HttpServletRequest, response: HttpServletResponse): Map<String, Any> =
        auth.login(body.email, body.password, request.remoteAddr ?: "unknown", request, response).let { mapOf("id" to it.id, "email" to it.email) }

    @PostMapping("/api/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(request: HttpServletRequest, response: HttpServletResponse) = auth.logout(request, response)

    @PostMapping("/api/auth/password-reset/request")
    fun requestReset(@Valid @RequestBody body: ResetRequest, request: HttpServletRequest): Map<String, String> {
        auth.requestPasswordReset(body.email, request.remoteAddr ?: "unknown")
        return mapOf("message" to "If an account exists for that email, reset instructions will be sent")
    }

    @PostMapping("/api/auth/password-reset/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun confirmReset(@Valid @RequestBody body: ResetConfirmRequest) = auth.confirmPasswordReset(body.token, body.newPassword)

    @GetMapping("/api/me")
    fun me(@AuthenticationPrincipal principal: UserPrincipal): Map<String, Any> = auth.currentUser(principal)
}
