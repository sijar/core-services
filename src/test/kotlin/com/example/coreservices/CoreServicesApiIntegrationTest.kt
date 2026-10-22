package com.example.coreservices

import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.mock.web.MockHttpSession
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class CoreServicesApiIntegrationTest {
    @Autowired private lateinit var mvc: MockMvc

    @Test
    fun `registration login and protected profile work`() {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized)
        val email = "${UUID.randomUUID()}@example.com"
        val password = "correct horse battery staple"
        mvc.perform(jsonPost("/api/auth/register", mapOf("email" to email, "password" to password)))
            .andExpect(status().isCreated)
        val login = mvc.perform(jsonPost("/api/auth/login", mapOf("email" to email, "password" to password)))
            .andExpect(status().isOk).andReturn()
        val session = login.request.getSession(false) as MockHttpSession
        mvc.perform(get("/api/me").session(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.email").value(email))
        mvc.perform(post("/api/auth/logout").session(session).with(csrf()))
            .andExpect(status().isNoContent)
        mvc.perform(get("/api/me").session(session)).andExpect(status().isUnauthorized)
    }

    @Test
    fun `reset link is single use and invalidates existing sessions`() {
        val email = "${UUID.randomUUID()}@example.com"
        val originalPassword = "correct horse battery staple"
        mvc.perform(jsonPost("/api/auth/register", mapOf("email" to email, "password" to originalPassword)))
            .andExpect(status().isCreated)
        val login = mvc.perform(jsonPost("/api/auth/login", mapOf("email" to email, "password" to originalPassword)))
            .andReturn()
        val session = login.request.getSession(false) as MockHttpSession
        mvc.perform(jsonPost("/api/auth/password-reset/request", mapOf("email" to email)))
            .andExpect(status().isOk)

        val mail = awaitMailbox(email)
        val resetLink = mail.getJSONObject(0).getString("message")
        val token = Regex("resetToken=([A-Za-z0-9_-]+)").find(resetLink)?.groupValues?.get(1)
        assertNotNull(token)
        val newPassword = "a newly-secure password"
        mvc.perform(jsonPost("/api/auth/password-reset/confirm", mapOf("token" to token, "newPassword" to newPassword)))
            .andExpect(status().isNoContent)
        mvc.perform(jsonPost("/api/auth/password-reset/confirm", mapOf("token" to token, "newPassword" to originalPassword)))
            .andExpect(status().isBadRequest)
        mvc.perform(get("/api/me").session(session)).andExpect(status().isUnauthorized)
        mvc.perform(jsonPost("/api/auth/login", mapOf("email" to email, "password" to newPassword)))
            .andExpect(status().isOk)
    }

    @Test
    fun `phone otp and payment operations are scoped to an authenticated account`() {
        val email = "${UUID.randomUUID()}@example.com"
        val password = "correct horse battery staple"
        mvc.perform(jsonPost("/api/auth/register", mapOf("email" to email, "password" to password))).andExpect(status().isCreated)
        val login = mvc.perform(jsonPost("/api/auth/login", mapOf("email" to email, "password" to password))).andReturn()
        val session = login.request.getSession(false) as MockHttpSession

        val phone = "+1415${(1000000..9999999).random()}"
        mvc.perform(jsonPost("/api/phone/verification/start", mapOf("phoneNumber" to phone)).session(session))
            .andExpect(status().isAccepted)
        val mailbox = awaitMailbox(phone)
        val otp = Regex("code is ([0-9]{6})").find(mailbox.getJSONObject(0).getString("message"))?.groupValues?.get(1)
        assertNotNull(otp)
        mvc.perform(jsonPost("/api/phone/verification/confirm", mapOf("code" to otp)).session(session))
            .andExpect(status().isNoContent)
        mvc.perform(get("/api/phone/verification").session(session))
            .andExpect(status().isOk).andExpect(jsonPath("$.verified").value(true))

        val payment = mvc.perform(jsonPost("/api/payments", mapOf("productCode" to "demo-item")).session(session))
            .andExpect(status().isCreated).andReturn()
        val paymentId = JSONObject(payment.response.contentAsString).getString("id")
        mvc.perform(get("/api/payments/$paymentId").session(session))
            .andExpect(status().isOk).andExpect(jsonPath("$.status").value("PENDING"))
        mvc.perform(get("/api/payments/$paymentId")).andExpect(status().isUnauthorized)
        mvc.perform(jsonPost("/api/payments", mapOf("productCode" to "arbitrary-price")).session(session))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `openapi documents the implemented api`() {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk)
            .andExpect(jsonPath("$.paths['/api/auth/login']").exists())
            .andExpect(jsonPath("$.paths['/api/payments']").exists())
    }

    private fun jsonPost(path: String, body: Any): MockHttpServletRequestBuilder = post(path)
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSONObject(body as Map<*, *>).toString())
        .with(csrf())

    private fun awaitMailbox(recipient: String): JSONArray {
        val deadline = System.nanoTime() + Duration.ofSeconds(4).toNanos()
        var result = JSONArray()
        while (System.nanoTime() < deadline && result.length() == 0) {
            Thread.sleep(25)
            val response: MvcResult = mvc.perform(get("/dev/mailbox").param("recipient", recipient))
                .andExpect(status().isOk).andReturn()
            result = JSONArray(response.response.contentAsString)
        }
        assertTrue(result.length() > 0, "Expected a local notification in the development mailbox")
        return result
    }

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:17-alpine")
            .apply {
                withDatabaseName("core_services")
                withUsername("test")
                withPassword("test")
            }

        @JvmStatic
        @DynamicPropertySource
        fun database(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.flyway.url", postgres::getJdbcUrl)
            registry.add("spring.flyway.user", postgres::getUsername)
            registry.add("spring.flyway.password", postgres::getPassword)
        }
    }
}
