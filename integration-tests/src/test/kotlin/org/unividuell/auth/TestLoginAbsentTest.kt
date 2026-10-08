package org.unividuell.auth

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.unividuell.auth.test.withCsrfToken
import org.unividuell.auth.testapp.TestApplication

/** Off means the beans do not exist: 404, never 403, which would advertise the feature. */
@SpringBootTest(classes = [TestApplication::class])
@AutoConfigureMockMvc
@ActiveProfiles("production")
class TestLoginAbsentInProductionTest(@Autowired val mockMvc: MockMvc) {

    @Test
    fun `the sign-in POST does not exist`() {
        mockMvc.post("/login/test/as") {
            with(withCsrfToken())
            param("login", "leela")
        }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `the unlock POST does not exist`() {
        mockMvc.post("/login/test/unlock") {
            with(withCsrfToken())
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
            with(withCsrfToken())
            param("login", "leela")
        }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `GET login start replays the production flow`() {
        mockMvc.get("/login/start").andExpect { redirectedUrl("/oauth2/authorization/github") }
    }
}
