package com.example.coreservices.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.session.SessionRegistry
import org.springframework.web.filter.OncePerRequestFilter

class ExpiredSessionFilter(private val sessions: SessionRegistry) : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val session = request.getSession(false)
        val info = session?.let { sessions.getSessionInformation(it.id) }
        if (info?.isExpired == true) {
            sessions.removeSessionInformation(session.id)
            session.invalidate()
            SecurityContextHolder.clearContext()
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            response.contentType = "application/json"
            response.writer.write("{\"error\":\"Session expired\"}")
            return
        }
        chain.doFilter(request, response)
    }
}
