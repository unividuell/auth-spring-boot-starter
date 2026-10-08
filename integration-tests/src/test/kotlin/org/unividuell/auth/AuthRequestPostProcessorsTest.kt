package org.unividuell.auth

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.unividuell.auth.test.TEST_CSRF_TOKEN
import org.unividuell.auth.test.signedInAs
import org.unividuell.auth.test.withCsrfToken
import org.unividuell.auth.testapp.TestApplication
import java.util.UUID

/** The test support an app gets from auth-spring-boot-starter-test, used the way an app uses it. */
@SpringBootTest(classes = [TestApplication::class])
@AutoConfigureMockMvc
class AuthRequestPostProcessorsTest(@Autowired val mockMvc: MockMvc) {

    private val fry = AuthPrincipal(id = UUID.randomUUID(), provider = "test", login = "Fry", roles = emptySet())

    @Test
    fun `signedInAs passes the CSRF check and the sign-in`() {
        mockMvc.post("/api/ping") { with(signedInAs(fry)) }.andExpect {
            status { isOk() }
            content { string("pong") }
        }
    }

    @Test
    fun `signedInAs puts the principal where the app reads it`() {
        mockMvc.get("/api/me") { with(signedInAs(fry)) }.andExpect {
            status { isOk() }
            jsonPath("$.id") { value(fry.id.toString()) }
            jsonPath("$.provider") { value("test") }
        }
    }

    @Test
    fun `withCsrfToken alone passes the CSRF check, not the sign-in`() {
        mockMvc.post("/api/ping") { with(withCsrfToken()) }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `without a token the CSRF check refuses a signed-in request`() {
        val token = OAuth2AuthenticationToken(fry, fry.authorities, fry.provider)

        mockMvc.post("/api/ping") { with(authentication(token)) }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `withCsrfToken keeps the cookies a request already carries`() {
        val request = MockHttpServletRequest().apply { setCookies(Cookie("SESSION", "abc")) }

        withCsrfToken().postProcessRequest(request)

        request.cookies.orEmpty().associate { it.name to it.value } shouldBe
            mapOf("SESSION" to "abc", "XSRF-TOKEN" to TEST_CSRF_TOKEN)
        request.getHeader("X-XSRF-TOKEN") shouldBe TEST_CSRF_TOKEN
    }

    @Test
    fun `after signedInAs, an anonymous request still gets the cookie and no session`() {
        // What spring-security-test's csrf() breaks for every later request in the context.
        mockMvc.post("/api/ping") { with(signedInAs(fry)) }.andExpect { status { isOk() } }

        val anonymous = mockMvc.get("/api/me").andExpect { status { isUnauthorized() } }.andReturn()

        anonymous.response.setCookieValue("XSRF-TOKEN").shouldNotBeNull()
        anonymous.request.getSession(false).shouldBeNull()
    }
}
