package com.example.coreservices

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(properties = [
    "spring.flyway.enabled=false",
    "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/core_services",
    "spring.datasource.username=unused",
    "spring.datasource.password=unused",
    "management.health.db.enabled=false"
])
@ActiveProfiles("local")
@AutoConfigureMockMvc
class ApplicationContextSmokeTest {
    @Autowired private lateinit var mvc: MockMvc

    @Test
    fun `security csrf and generated openapi endpoints load`() {
        mvc.perform(get("/api/csrf")).andExpect(status().isOk)
            .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"))
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk)
            .andExpect(jsonPath("$.paths['/api/auth/login']").exists())
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized)
    }
}
