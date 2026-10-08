package org.unividuell.auth

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.unividuell.auth.internal.FakeSignInGate
import org.unividuell.auth.testapp.TestApplication

/** The locked half: the only place the lock is shut. Everything else runs without a key. */
@SpringBootTest(classes = [TestApplication::class])
@AutoConfigureMockMvc
@ActiveProfiles("staging")
@TestPropertySource(properties = ["unividuell.auth.test-login.key=open-sesame"])
class TestLoginLockedTest(@Autowired val mockMvc: MockMvc) {

    private fun unlockedCookie(): Cookie {
        val value = mockMvc.post("/login/test/unlock") {
            with(csrf())
            param("key", "open-sesame")
        }.andReturn().response.setCookieValue(FakeSignInGate.COOKIE_NAME).shouldNotBeNull()

        return Cookie(FakeSignInGate.COOKIE_NAME, value)
    }

    @Test
    fun `without the cookie the picker is replaced by the locked page`() {
        val html = mockMvc.get("/login/start").andExpect {
            status { isOk() }
            content { contentType("text/html;charset=UTF-8") }
        }.andReturn().response.contentAsString

        html shouldContain "Gesperrt"
        // A locked door that lists the names behind it shows what there is to take.
        html shouldNotContain "leela"
        html shouldNotContain """name="login""""
    }

    @Test
    fun `the locked page declares a mobile viewport`() {
        mockMvc.get("/login/start").andReturn().response.contentAsString shouldContain
            """<meta name="viewport" content="width=device-width,initial-scale=1">"""
    }

    @Test
    fun `a wrong key changes nothing and reveals nothing`() {
        val response = mockMvc.post("/login/test/unlock") {
            with(csrf())
            param("key", "guessing")
        }.andExpect {
            status { isOk() }
        }.andReturn().response

        response.setCookieValue(FakeSignInGate.COOKIE_NAME).shouldBeNull()
        response.contentAsString shouldContain "Falscher Schlüssel"
        response.contentAsString shouldNotContain "open-sesame"
    }

    @Test
    fun `the right key issues the cookie and returns to the picker`() {
        val response = mockMvc.post("/login/test/unlock") {
            with(csrf())
            param("key", "open-sesame")
        }.andExpect {
            status { isFound() }
            redirectedUrl("/login/start")
        }.andReturn().response

        response.setCookieValue(FakeSignInGate.COOKIE_NAME).shouldNotBeNull() shouldNotContain "open-sesame"
    }

    @Test
    fun `with the cookie the picker renders`() {
        mockMvc.get("/login/start") { cookie(unlockedCookie()) }.andReturn().response.contentAsString shouldContain
            """name="login" value="leela""""
    }

    @Test
    fun `unlocking carries the redirect through to the picker`() {
        // Post/Redirect/Get: the destination must survive the moment the key is entered.
        mockMvc.post("/login/test/unlock") {
            with(csrf())
            param("key", "open-sesame")
            param("redirect", "/c/team/lab/sample?seed=42")
        }.andExpect {
            redirectedUrl("/login/start?redirect=%2Fc%2Fteam%2Flab%2Fsample%3Fseed%3D42")
        }
    }

    @Test
    fun `a wrong key keeps the redirect for the next attempt`() {
        mockMvc.post("/login/test/unlock") {
            with(csrf())
            param("key", "guessing")
            param("redirect", "/c/team/lab/sample?seed=42")
        }.andReturn().response.contentAsString shouldContain """name="redirect" value="/c/team/lab/sample?seed=42""""
    }

    @Test
    fun `the locked page escapes a redirect containing markup`() {
        mockMvc.get("""/login/start?redirect=/x"><script>alert(1)</script>""").andReturn().response.contentAsString shouldContain
            "&lt;script&gt;"
    }

    @Test
    fun `the sign-in POST is locked too, not just the page that shows it`() {
        // The logins are public in the config; an unguarded POST is the picker without the picker.
        val result = mockMvc.post("/login/test/as") {
            with(csrf())
            param("login", "leela")
        }.andExpect {
            redirectedUrl("/login/start")
        }.andReturn()

        result.request.getSession(false).shouldBeNull()
    }

    @Test
    fun `with the cookie the sign-in POST works`() {
        val signedIn = mockMvc.post("/login/test/as") {
            with(csrf())
            cookie(unlockedCookie())
            param("login", "leela")
        }.andExpect {
            redirectedUrl("/")
        }.andReturn().request.session as MockHttpSession

        mockMvc.get("/api/me") { session = signedIn }.andExpect { status { isOk() } }
    }

    @Test
    fun `logout does not spend the unlock`() {
        // "Once per browser profile": unlock once, then switch users freely.
        val response = mockMvc.post("/logout") {
            with(csrf())
            cookie(unlockedCookie())
        }.andExpect {
            status { isNoContent() }
        }.andReturn().response

        response.setCookieValue(FakeSignInGate.COOKIE_NAME).shouldBeNull()
    }
}
