package org.unividuell.auth

import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import jakarta.servlet.http.Cookie
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.unividuell.auth.test.withCsrfToken
import org.unividuell.auth.testapp.InMemoryAccounts
import org.unividuell.auth.testapp.TestApplication
import java.net.URI
import java.util.Base64

/** localhost: no profile, no key — the picker is open. */
@SpringBootTest(classes = [TestApplication::class])
@AutoConfigureMockMvc
class TestLoginTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val accounts: InMemoryAccounts,
) {

    private fun signInAs(login: String, session: MockHttpSession = MockHttpSession()): MockHttpSession =
        mockMvc.post("/login/test/as") {
            with(withCsrfToken())
            this.session = session
            param("login", login)
        }.andExpect {
            status { is3xxRedirection() }
        }.andReturn().request.session as MockHttpSession

    @Test
    fun `GET login start renders the picker`() {
        mockMvc.get("/login/start").andExpect {
            status { isOk() }
            content { contentType("text/html;charset=UTF-8") }
            content { string(containsString("leela")) }
            content { string(containsString("Turanga Leela")) }
            // The chip is decorative; without aria-hidden a screen reader announces "lobster Dr. Zoidberg".
            content { string(containsString("""<span class="chip" aria-hidden="true">🦞</span>""")) }
        }
    }

    @Test
    fun `the picker declares a mobile viewport`() {
        mockMvc.get("/login/start").andExpect {
            content { string(containsString("""<meta name="viewport" content="width=device-width,initial-scale=1">""")) }
        }
    }

    @Test
    fun `the picker lists every configured user in order`() {
        val html = mockMvc.get("/login/start").andReturn().response.contentAsString

        val positions = AuthProperties.FUTURAMA.map { html.indexOf("""name="login" value="${it.login}"""") }
        positions.forEach { it shouldBeGreaterThan -1 }
        positions shouldBe positions.sorted()
    }

    @Test
    fun `the bare login path stays the app's while the picker is on`() {
        mockMvc.get("/login").andExpect { status { isNotFound() } }
    }

    @Test
    fun `signing in provisions the test identity and lands on the root`() {
        mockMvc.post("/login/test/as") {
            with(withCsrfToken())
            param("login", "leela")
        }.andExpect {
            status { is3xxRedirection() }
            redirectedUrl("/")
        }

        accounts.provisioned.last() shouldBe ExternalIdentity(
            provider = "test",
            subject = "leela",
            login = "leela",
            name = "Turanga Leela",
            email = null,
        )
    }

    @Test
    fun `a single-page app's picker never returns to a remembered page`() {
        // Only a server-rendered app remembers pages; a cookie of that name changes nothing here. The value
        // is what CookieRequestCache writes (Base64 of path and query), so a cache that read it would use it.
        val remembered = Cookie("REDIRECT_URI", Base64.getEncoder().encodeToString("/api/me".toByteArray()))

        mockMvc.post("/login/test/as") {
            cookie(remembered)
            with(withCsrfToken())
            param("login", "leela")
        }.andExpect {
            status { is3xxRedirection() }
            redirectedUrl("/")
        }
    }

    @Test
    fun `the session carries the principal, configured roles included`() {
        val signedIn = signInAs(login = "prof")

        mockMvc.get("/api/me") { session = signedIn }.andExpect {
            status { isOk() }
            jsonPath("$.provider") { value("test") }
            jsonPath("$.login") { value("prof") }
            jsonPath("$.roles[0]") { value("SUPER_ADMIN") }
        }
    }

    @Test
    fun `signing in gives an existing session a new id`() {
        // Session fixation: an id handed out before the sign-in must not end up signed in.
        val session = MockHttpSession()
        val before = session.id

        signInAs(login = "leela", session = session).id shouldNotBe before
    }

    @Test
    fun `switching players replaces the user in the same session`() {
        // The lab's "Spieler wechseln" goes back through the picker while signed in.
        val session = signInAs(login = "leela")
        signInAs(login = "Bender", session = session)

        mockMvc.get("/api/me") { this.session = session }.andExpect {
            jsonPath("$.login") { value("Bender") }
        }
    }

    @Test
    fun `a login that is not a configured test user is refused`() {
        // permitAll endpoint: resolving any name would let anyone become any account.
        mockMvc.post("/login/test/as") {
            with(withCsrfToken())
            param("login", "octocat")
        }.andExpect {
            status { isBadRequest() }
        }

        accounts.provisioned.none { it.login == "octocat" } shouldBe true
    }

    @Test
    fun `the sign-in POST without a CSRF token is refused`() {
        mockMvc.post("/login/test/as") { param("login", "leela") }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `the unlock POST without a CSRF token is refused`() {
        mockMvc.post("/login/test/unlock") { param("key", "anything") }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `the picker carries a redirect through to its forms`() {
        // get(URI) keeps the percent-escapes as they are; get(String) would encode them again.
        mockMvc.get(URI("/login/start?redirect=/c/team/lab/sample%3Fseed%3D42")).andExpect {
            status { isOk() }
            content { string(containsString("""name="redirect" value="/c/team/lab/sample?seed=42"""")) }
        }
    }

    @Test
    fun `the picker escapes a redirect containing markup`() {
        mockMvc.get("""/login/start?redirect=/x"><script>alert(1)</script>""").andReturn().response.contentAsString shouldContain
            "&lt;script&gt;"
    }

    @Test
    fun `signing in returns to the requested path`() {
        mockMvc.post("/login/test/as") {
            with(withCsrfToken())
            param("login", "leela")
            param("redirect", "/c/team/lab/sample?seed=42")
        }.andExpect {
            redirectedUrl("/c/team/lab/sample?seed=42")
        }
    }

    @Test
    fun `signing in ignores an off-site redirect`() {
        listOf("//evil.example", "https://evil.example", "/\t/evil.example").forEach { hostile ->
            mockMvc.post("/login/test/as") {
                with(withCsrfToken())
                param("login", "leela")
                param("redirect", hostile)
            }.andExpect {
                redirectedUrl("/")
            }
        }
    }

    @Test
    fun `signing in redirects to a path with a brace instead of failing`() {
        // RedirectView would read "{b}" as a URI template variable and throw.
        mockMvc.post("/login/test/as") {
            with(withCsrfToken())
            param("login", "leela")
            param("redirect", "/a{b}")
        }.andExpect {
            status { is3xxRedirection() }
            redirectedUrl("/a{b}")
        }
    }
}

/** The app's hook failing on the test door ends where it does on the provider door. */
@SpringBootTest(classes = [TestApplication::class, TestLoginProvisioningFailedTest.FailingAccounts::class])
@AutoConfigureMockMvc
class TestLoginProvisioningFailedTest(@Autowired val mockMvc: MockMvc) {

    @TestConfiguration
    class FailingAccounts {

        @Bean
        @Primary
        fun failingAccounts() = AccountProvisioner { _, _ -> error("database down") }
    }

    @Test
    fun `a failing provisioner lands on the error page`() {
        mockMvc.post("/login/test/as") {
            with(withCsrfToken())
            param("login", "leela")
        }.andExpect {
            status { isFound() }
            redirectedUrl("/login/start?error")
        }
    }
}
