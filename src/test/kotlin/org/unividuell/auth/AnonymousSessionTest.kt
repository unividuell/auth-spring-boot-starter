package org.unividuell.auth

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.unividuell.auth.testapp.TestApplication

/*
 * Anonymous traffic creates no HTTP session: with Spring Session JDBC each one is a database row.
 *
 * Not pinned: /oauth2/authorization/{id}, which stores the authorization request in one by design.
 *
 * Each class gets a fresh context: csrf() in other classes leaves a session-backed CSRF repository
 * in the shared one, and every request there would create a session.
 */

/** The request left no session behind, not even an empty one. */
private fun ResultActionsDsl.shouldCreateNoSession() = andReturn().request.getSession(false).shouldBeNull()

/** localhost: no profile, the picker is open. */
@SpringBootTest(classes = [TestApplication::class])
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class AnonymousSessionTest(@Autowired val mockMvc: MockMvc) {

    @Test
    fun `an unauthenticated API call creates no session`() {
        // The SPA's bootstrap call, and what a healthcheck without a cookie looks like.
        mockMvc.get("/api/me").andExpect { status { isUnauthorized() } }.shouldCreateNoSession()
    }

    @Test
    fun `a path excluded from the CSRF cookie creates no session`() {
        mockMvc.get("/api/public/preview").andExpect { status { isUnauthorized() } }.shouldCreateNoSession()
    }

    @Test
    fun `the picker creates no session`() {
        mockMvc.get("/login").andExpect { status { isOk() } }.shouldCreateNoSession()
    }

    @Test
    fun `the error page creates no session`() {
        mockMvc.get("/login?error").andExpect { status { isOk() } }.shouldCreateNoSession()
    }

    @Test
    fun `an unknown path under login creates no session`() {
        mockMvc.get("/login/nothing").andExpect { status { isNotFound() } }.shouldCreateNoSession()
    }

    @Test
    fun `a logout without a CSRF token creates no session`() {
        mockMvc.post("/logout").andExpect { status { isForbidden() } }.shouldCreateNoSession()
    }
}

/** Staging: the lock is shut, the key is known to the test only. */
@SpringBootTest(classes = [TestApplication::class])
@AutoConfigureMockMvc
@ActiveProfiles("staging")
@TestPropertySource(properties = ["unividuell.auth.test-login.key=open-sesame"])
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class AnonymousSessionLockedTest(@Autowired val mockMvc: MockMvc) {

    @Test
    fun `the locked page creates no session`() {
        mockMvc.get("/login").andExpect { status { isOk() } }.shouldCreateNoSession()
    }

    @Test
    fun `a wrong key creates no session`() {
        // The token travels as the SPA sends it, cookie and header: csrf() would put a session behind it.
        val token = mockMvc.get("/login").andReturn().response.setCookieValue("XSRF-TOKEN").shouldNotBeNull()

        mockMvc.post("/login/test/unlock") {
            cookie(Cookie("XSRF-TOKEN", token))
            header("X-XSRF-TOKEN", token)
            param("key", "guessing")
        }.andExpect { status { isOk() } }.shouldCreateNoSession()
    }
}

/** Production: one client, no test login. */
@SpringBootTest(classes = [TestApplication::class])
@AutoConfigureMockMvc
@ActiveProfiles("production")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class AnonymousSessionProviderTest(@Autowired val mockMvc: MockMvc) {

    @Test
    fun `the redirect to the provider creates no session`() {
        mockMvc.get("/login").andExpect {
            status { isFound() }
            redirectedUrl("/oauth2/authorization/github")
        }.shouldCreateNoSession()
    }
}
