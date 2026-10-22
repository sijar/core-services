package com.example.coreservices.auth

import com.example.coreservices.common.ApiException
import com.example.coreservices.common.WindowRateLimiter
import com.example.coreservices.security.UserPrincipal
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseCookie
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.session.SessionRegistry
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.security.web.context.SecurityContextRepository
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.security.MessageDigest
import java.util.UUID

data class UserRecord(val id: UUID, val email: String, val passwordHash: String, val status: String)

@Service
class AuthService(
    private val jdbc: JdbcClient,
    private val encoder: PasswordEncoder,
    private val limiter: WindowRateLimiter,
    private val resetProcessor: PasswordResetProcessor,
    private val sessionRegistry: SessionRegistry,
    @Value("\${security.password.minimum-length:12}") private val minimumPasswordLength: Int,
    @Value("\${security.rate-limit.login-per-ip:20}") private val loginPerIp: Int,
    @Value("\${security.rate-limit.login-per-email:5}") private val loginPerEmail: Int,
    @Value("\${security.rate-limit.registration-per-ip:10}") private val registrationPerIp: Int,
    @Value("\${security.rate-limit.reset-per-ip:10}") private val resetPerIp: Int,
    @Value("\${server.servlet.session.cookie.secure:true}") private val secureCookie: Boolean
) {
    private val contextRepository: SecurityContextRepository = HttpSessionSecurityContextRepository()
    private val dummyPasswordHash = encoder.encode(UUID.randomUUID().toString())

    @Transactional
    fun register(emailInput: String, password: String, ip: String): UserPrincipal {
        val email = normalizeEmail(emailInput)
        if (!limiter.allow("register:ip:$ip", registrationPerIp) || !limiter.allow("register:email:$email", registrationPerIp)) tooMany()
        validatePassword(password)
        val id = UUID.randomUUID()
        val insertedId = jdbc.sql("INSERT INTO users (id,email,password_hash) VALUES (:id,:email,:hash) ON CONFLICT(email) DO NOTHING RETURNING id")
            .param("id", id).param("email", email).param("hash", encoder.encode(password)).query(UUID::class.java).optional().orElse(null)
            ?: throw ApiException(HttpStatus.CONFLICT, "An account with this email already exists")
        return UserPrincipal(insertedId, email)
    }

    fun login(emailInput: String, password: String, ip: String, request: HttpServletRequest, response: HttpServletResponse): UserPrincipal {
        val email = normalizeEmail(emailInput)
        if (!limiter.allow("login:ip:$ip", loginPerIp) || !limiter.allow("login:email:$email", loginPerEmail)) tooMany()
        val user = userByEmail(email)
        val passwordMatches = encoder.matches(password, user?.passwordHash ?: dummyPasswordHash)
        if (user == null || user.status != "ACTIVE" || !passwordMatches) {
            throw ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password")
        }
        request.getSession(false)?.let { sessionRegistry.removeSessionInformation(it.id) }
        val session = request.getSession(true)
        request.changeSessionId()
        val principal = UserPrincipal(user.id, user.email)
        val authentication = UsernamePasswordAuthenticationToken(principal, null, listOf(SimpleGrantedAuthority("ROLE_USER")))
        val securityContext: SecurityContext = SecurityContextHolder.createEmptyContext().also { it.authentication = authentication }
        SecurityContextHolder.setContext(securityContext)
        contextRepository.saveContext(securityContext, request, response)
        sessionRegistry.registerNewSession(session.id, principal)
        return principal
    }

    fun logout(request: HttpServletRequest, response: HttpServletResponse) {
        request.getSession(false)?.let { session ->
            sessionRegistry.removeSessionInformation(session.id)
            session.invalidate()
        }
        response.addHeader("Set-Cookie", ResponseCookie.from("CORESESSION", "").httpOnly(true).secure(secureCookie).sameSite("Lax").path("/").maxAge(0).build().toString())
        SecurityContextHolder.clearContext()
    }

    fun requestPasswordReset(emailInput: String, ip: String) {
        val email = normalizeEmail(emailInput)
        if (!limiter.allow("reset:ip:$ip", resetPerIp) || !limiter.allow("reset:email:$email", resetPerIp)) tooMany()
        resetProcessor.enqueue(email)
    }

    @Transactional
    fun confirmPasswordReset(token: String, newPassword: String) {
        validatePassword(newPassword)
        val userId = jdbc.sql("UPDATE password_reset_tokens SET used_at=now() WHERE token_hash=:hash AND used_at IS NULL AND expires_at > now() RETURNING user_id")
            .param("hash", sha256(token)).query(UUID::class.java).optional().orElse(null)
            ?: throw ApiException(HttpStatus.BAD_REQUEST, "Invalid or expired reset token")
        jdbc.sql("UPDATE users SET password_hash=:hash, updated_at=now() WHERE id=:id")
            .param("hash", encoder.encode(newPassword)).param("id", userId).update()
        jdbc.sql("UPDATE password_reset_tokens SET used_at=now() WHERE user_id=:id AND used_at IS NULL")
            .param("id", userId).update()
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() {
                sessionRegistry.getAllSessions(UserPrincipal(userId, ""), false).forEach { it.expireNow() }
            }
        })
    }

    fun currentUser(principal: UserPrincipal): Map<String, Any> = mapOf("id" to principal.id, "email" to principal.email)

    private fun userByEmail(email: String): UserRecord? = jdbc.sql("SELECT id,email,password_hash,status FROM users WHERE email=:email")
        .param("email", email).query { rs, _ -> UserRecord(rs.getObject("id", UUID::class.java), rs.getString("email"), rs.getString("password_hash"), rs.getString("status")) }
        .optional().orElse(null)

    private fun normalizeEmail(input: String) = input.trim().lowercase()

    private fun validatePassword(password: String) {
        if (password.length !in minimumPasswordLength..128) throw ApiException(HttpStatus.BAD_REQUEST, "Password must be between $minimumPasswordLength and 128 characters")
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun tooMany(): Nothing = throw ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many requests; try again later")
}
