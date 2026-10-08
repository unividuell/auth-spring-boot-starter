package org.unividuell.auth

import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.unividuell.auth.test.signedInAs
import org.unividuell.auth.test.withCsrfToken
import org.unividuell.auth.testapp.TestApplication
import java.util.Base64
import java.util.UUID

/** `frontend: server-rendered`: page navigation where a single-page app gets status codes. */
@SpringBootTest(
    classes = [TestApplication::class],
    properties = ["unividuell.auth.frontend=server-rendered", "unividuell.auth.login-page=/welcome"],
)
@AutoConfigureMockMvc
class ServerRenderedTest(@Autowired val mockMvc: MockMvc) {

    private val fry = AuthPrincipal(id = UUID.randomUUID(), provider = "test", login = "Fry", roles = emptySet())

    /** The cookie CookieRequestCache keeps the request in, to return to it after sign-in. */
    private fun MvcResult.remembered() = response.getCookie("REDIRECT_URI")

    @Test
    fun `an anonymous page request is sent to the login page`() {
        mockMvc.get("/api/me").andExpect {
            status { isFound() }
            redirectedUrl("/welcome")
        }
    }

    @Test
    fun `an anonymous htmx or fetch request is sent to the login page too`() {
        mockMvc.post("/api/ping") {
            header("Sec-Fetch-Mode", "cors")
            with(withCsrfToken())
        }.andExpect {
            status { isFound() }
            redirectedUrl("/welcome")
        }
    }

    @Test
    fun `the login page is open without signing in`() {
        mockMvc.get("/welcome").andExpect {
            status { isOk() }
            content { string("welcome") }
        }
    }

    @Test
    fun `logout lands on the login page`() {
        mockMvc.post("/logout") { with(signedInAs(fry)) }.andExpect {
            status { isFound() }
            redirectedUrl("/welcome")
        }
    }

    @Test
    fun `a page navigation is remembered for after the sign-in`() {
        mockMvc.get("/api/me").andReturn().remembered().shouldNotBeNull()
        mockMvc.get("/api/me") { header("Sec-Fetch-Mode", "navigate") }.andReturn().remembered().shouldNotBeNull()
    }

    @Test
    fun `a fetch or htmx request is not remembered`() {
        // Replayed as a GET after the sign-in, it would land the user on a fragment or a 405.
        mockMvc.get("/api/me") { header("Sec-Fetch-Mode", "cors") }.andReturn().remembered().shouldBeNull()
        mockMvc.post("/api/ping") { with(withCsrfToken()) }.andReturn().remembered().shouldBeNull()
    }

    @Test
    fun `the picker returns to the remembered page`() {
        val remembered = mockMvc.get("/api/me?tab=parts").andReturn().remembered().shouldNotBeNull()

        val signIn = mockMvc.post("/login/test/as") {
            cookie(remembered)
            with(withCsrfToken())
            param("login", "Fry")
            // The picker's form always posts the field, empty when nothing was asked for.
            param("redirect", "")
        }.andExpect {
            status { isFound() }
            redirectedUrl("/api/me?tab=parts")
        }.andReturn()

        withClue("the remembered page is used up") { signIn.remembered().shouldNotBeNull().maxAge shouldBe 0 }
    }

    @Test
    fun `an explicit redirect wins over the remembered page`() {
        val remembered = mockMvc.get("/api/me").andReturn().remembered().shouldNotBeNull()

        mockMvc.post("/login/test/as") {
            cookie(remembered)
            with(withCsrfToken())
            param("login", "Fry")
            param("redirect", "/welcome")
        }.andExpect { redirectedUrl("/welcome") }
    }

    @Test
    fun `a forged remembered page never leads off the site`() {
        // The cookie is the browser's to set: another host, or a path that leaves the site, lands on the root.
        for (url in listOf("http://evil.example/garage", "http://localhost//evil.example/garage")) {
            mockMvc.post("/login/test/as") {
                cookie(Cookie("REDIRECT_URI", Base64.getEncoder().encodeToString(url.toByteArray())))
                with(withCsrfToken())
                param("login", "Fry")
            }.andExpect { redirectedUrl("/") }
        }
    }

    @Test
    fun `without a remembered page the picker lands on the root`() {
        mockMvc.post("/login/test/as") {
            with(withCsrfToken())
            param("login", "Fry")
        }.andExpect { redirectedUrl("/") }
    }

    @Test
    fun `anonymous requests still create no session`() {
        mockMvc.get("/api/me").andReturn().request.getSession(false).shouldBeNull()
        mockMvc.get("/welcome").andReturn().request.getSession(false).shouldBeNull()
    }
}
