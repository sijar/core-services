package com.example.coreservices.notification

import com.example.coreservices.security.UserPrincipal
import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class PreferencesRequest(@field:NotEmpty val preferences: Map<String, Boolean>)

@RestController
@RequestMapping("/api/notifications")
class NotificationController(private val notifications: NotificationService) {
    @GetMapping
    fun list(@AuthenticationPrincipal user: UserPrincipal) = notifications.list(user.id)

    @GetMapping("/preferences")
    fun preferences(@AuthenticationPrincipal user: UserPrincipal) = notifications.preferences(user.id)

    @PutMapping("/preferences")
    fun setPreferences(@AuthenticationPrincipal user: UserPrincipal, @Valid @RequestBody body: PreferencesRequest): Map<String, Boolean> {
        val values = body.preferences.mapKeys { (channel, _) -> notifications.requireChannel(channel) }
        notifications.setPreferences(user.id, values)
        return notifications.preferences(user.id)
    }
}
