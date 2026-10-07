package org.unividuell.auth

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.web.WebAttributes
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.unividuell.auth.testapp.TestApplication

/** Production: one client, no test login. `/login` belongs to the provider. */
@SpringBootTest(classes = [TestApplication::class])
@AutoConfigureMockMvc
@ActiveProfiles("production")
@ExtendWith(OutputCaptureExtension::class)
class ProviderLoginTest(@Autowired val mockMvc: MockMvc) {

    @Test
    fun `GET login sends the browser to the only provider`() {
        // Spring's own generated login page would answer 200 here, before any controller.
        mockMvc.get("/login").andExpect {
            status { isFound() }
            redirectedUrl("/oauth2/authorization/github")
        }
    }

    @Test
    fun `the provider path starts the real OAuth flow`() {
        val location = mockMvc.get("/oauth2/authorization/github").andExpect {
            status { isFound() }
        }.andReturn().response.getHeader("Location").shouldNotBeNull()

        location shouldStartWith "https://github.com/login/oauth/authorize"
    }

    @Test
    fun `a failed callback lands on the error page instead of looping`() {
        // No authorization request was ever saved, so Spring rejects the callback as forged.
        mockMvc.get("/login/oauth2/code/github?code=abc&state=forged").andExpect {
            status { isFound() }
            redirectedUrl("/login?error")
        }
    }

    @Test
    fun `a failed callback leaves no session behind`() {
        // Spring's default handler stores the exception in a session, creating one for a forged callback.
        mockMvc.get("/login/oauth2/code/github?code=abc&state=forged").andExpect {
            redirectedUrl("/login?error")
        }.andReturn().request.getSession(false).shouldBeNull()
    }

    @Test
    fun `a failed callback keeps its exception out of an existing session`() {
        val session = MockHttpSession()

        mockMvc.get("/login/oauth2/code/github?code=abc&state=forged") { this.session = session }

        session.getAttribute(WebAttributes.AUTHENTICATION_EXCEPTION).shouldBeNull()
    }

    @Test
    fun `a failed callback is logged by its error code, not by its code`(output: CapturedOutput) {
        mockMvc.get("/login/oauth2/code/github?code=leaked-code-4711&state=forged")

        output.out shouldContain "authorization_request_not_found"
        output.out shouldNotContain "leaked-code-4711"
    }

    @Test
    fun `the error page renders and never redirects`() {
        val response = mockMvc.get("/login?error").andExpect {
            status { isOk() }
            content { contentType("text/html;charset=UTF-8") }
        }.andReturn().response

        response.getHeader("Location").shouldBeNull()
        response.contentAsString shouldContain "Anmeldung fehlgeschlagen"
        response.contentAsString shouldContain """<a class="action" href="/login">Erneut versuchen</a>"""
    }

    @Test
    fun `the error page declares a mobile viewport`() {
        // Without it phones lay the page out at ~980px and scale it down.
        mockMvc.get("/login?error").andReturn().response.contentAsString shouldContain
            """<meta name="viewport" content="width=device-width,initial-scale=1">"""
    }
}
