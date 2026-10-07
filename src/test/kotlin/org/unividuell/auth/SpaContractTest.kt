package org.unividuell.auth

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.ApplicationContext
import org.springframework.security.web.SecurityFilterChain
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.unividuell.auth.testapp.TestApplication

/**
 * A fresh context, and no `csrf()` in here: that post-processor swaps the shared CsrfFilter's
 * cookie repository for a session one for good, and other classes share this context.
 */
@SpringBootTest(classes = [TestApplication::class])
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class SpaContractTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val context: ApplicationContext,
) {

    @Test
    fun `without a chain of its own the app gets the lib's default`() {
        context.getBeansOfType(SecurityFilterChain::class.java).keys shouldBe setOf("authDefaultSecurityFilterChain")
    }

    @Test
    fun `an unauthenticated API call gets 401, not a redirect to the provider`() {
        mockMvc.get("/api/me").andExpect {
            status { isUnauthorized() }
            header { doesNotExist("Location") }
        }
    }

    @Test
    fun `a plain GET already sets the XSRF-TOKEN cookie`() {
        mockMvc.get("/api/me").andReturn().response.setCookieValue("XSRF-TOKEN").shouldNotBeNull()
    }

    @Test
    fun `an excluded path never sets the XSRF-TOKEN cookie`() {
        // Same 401 as above; only the path differs. A Set-Cookie here would defeat a shared cache.
        mockMvc.get("/api/public/preview").andReturn().response.setCookieValue("XSRF-TOKEN").shouldBeNull()
    }

    @Test
    fun `the lib's paths are open without signing in`() {
        mockMvc.get("/login/anything").andExpect { status { isNotFound() } }
    }

    @Test
    fun `logout without the CSRF token is refused`() {
        mockMvc.post("/logout").andExpect { status { isForbidden() } }
    }

    @Test
    fun `the cookie's value works as the X-XSRF-TOKEN header`() {
        // The SPA's real path: read the cookie, echo it. The default XOR handler would reject this.
        val token = mockMvc.get("/api/me").andReturn().response.setCookieValue("XSRF-TOKEN").shouldNotBeNull()

        mockMvc.post("/logout") {
            cookie(Cookie("XSRF-TOKEN", token))
            header("X-XSRF-TOKEN", token)
        }.andExpect { status { isNoContent() } }
    }
}
