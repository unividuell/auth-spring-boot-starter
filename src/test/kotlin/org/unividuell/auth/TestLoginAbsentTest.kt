package org.unividuell.auth

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.unividuell.auth.testapp.TestApplication

/** Off means the beans do not exist: 404, never 403, which would advertise the feature. */
@SpringBootTest(classes = [TestApplication::class])
@AutoConfigureMockMvc
@ActiveProfiles("production")
class TestLoginAbsentInProductionTest(@Autowired val mockMvc: MockMvc) {

    @Test
    fun `the sign-in POST does not exist`() {
        mockMvc.post("/login/test/as") {
            with(csrf())
            param("login", "leela")
        }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `the unlock POST does not exist`() {
        mockMvc.post("/login/test/unlock") {
            with(csrf())
            param("key", "anything")
        }.andExpect { status { isNotFound() } }
    }
}

@SpringBootTest(classes = [TestApplication::class], properties = ["unividuell.auth.test-login.enabled=false"])
@AutoConfigureMockMvc
class TestLoginSwitchedOffTest(@Autowired val mockMvc: MockMvc) {

    @Test
    fun `the sign-in POST does not exist`() {
        mockMvc.post("/login/test/as") {
            with(csrf())
            param("login", "leela")
        }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `GET login replays the production flow`() {
        mockMvc.get("/login").andExpect { redirectedUrl("/oauth2/authorization/github") }
    }
}
