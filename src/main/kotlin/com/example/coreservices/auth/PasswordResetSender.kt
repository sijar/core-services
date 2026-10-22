package com.example.coreservices.auth

import java.util.UUID

interface PasswordResetSender {
    fun send(userId: UUID, email: String, token: String)
}
