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
 * Anonymous traffic creates no HTTP session. With Spring Session JDBC every session is a database
 * row, so a cookie-less healthcheck that creates one per hit fills the table: a sibling app
 * collected about 88k empty sessions that way. The lib's part of the promise: NullRequestCache,
 * the CSRF token in a cookie, a sessionless provider failure handler, pages that need no session.
 *
 * Not pinned: /oauth2/authorization/{id}. Spring stores the authorization request in a session
 * there, by design — it is how the callback finds it again.
 *
 * Each class below owns a fresh context. csrf() in other classes swaps the shared CsrfFilter's
 * cookie repository for a session-backed one for good; there every request would create a session
 * and these tests would fail for a reason that has nothing to do with the lib.
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
