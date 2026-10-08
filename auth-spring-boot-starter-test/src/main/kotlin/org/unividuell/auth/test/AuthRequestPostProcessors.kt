package org.unividuell.auth.test

import jakarta.servlet.http.Cookie
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.unividuell.auth.AuthPrincipal

/**
 * A browser signed in as [principal] the way the lib signs one in — an [OAuth2AuthenticationToken]
 * whose registration id is the principal's provider — and holding a CSRF token ([withCsrfToken]).
 */
fun signedInAs(principal: AuthPrincipal): RequestPostProcessor {
    val signIn = authentication(OAuth2AuthenticationToken(principal, principal.authorities, principal.provider))
    val csrf = withCsrfToken()
    return RequestPostProcessor { request -> csrf.postProcessRequest(signIn.postProcessRequest(request)) }
}

/**
 * The CSRF token the way a browser holds it: the lib's `XSRF-TOKEN` cookie, echoed in the
 * `X-XSRF-TOKEN` header a single-page app or htmx sends. The lib's CsrfFilter only checks that the
 * two are equal, so a fixed token runs through the real mechanism.
 *
 * Not spring-security-test's `csrf()`: it swaps the shared CsrfFilter's cookie repository for a
 * session-backed one, for good. Every later request in the same test context then gets no
 * `XSRF-TOKEN` cookie and creates a session.
 */
fun withCsrfToken(): RequestPostProcessor = RequestPostProcessor { request ->
    request.setCookies(*request.cookies.orEmpty(), Cookie(CSRF_COOKIE, TEST_CSRF_TOKEN))
    request.addHeader(CSRF_HEADER, TEST_CSRF_TOKEN)
    request
}

/** The token [withCsrfToken] holds; a page rendered for such a request carries it. */
const val TEST_CSRF_TOKEN: String = "test-csrf-token"

private const val CSRF_COOKIE = "XSRF-TOKEN"
private const val CSRF_HEADER = "X-XSRF-TOKEN"
