package com.example.coreservices.security

import java.util.UUID

data class UserPrincipal(val id: UUID, val email: String) {
    override fun equals(other: Any?) = other is UserPrincipal && id == other.id
    override fun hashCode() = id.hashCode()
}
