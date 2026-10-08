package org.unividuell.auth.internal

import org.springframework.security.web.savedrequest.CookieRequestCache
import org.springframework.security.web.savedrequest.NullRequestCache
import org.springframework.security.web.savedrequest.RequestCache
import org.unividuell.auth.AuthProperties.Frontend

/**
 * The app's frontend as configured, checked once at startup. There is no default: a single-page app
 * and a server-rendered one differ in what an anonymous request, a logout and a remembered request
 * get, and the lib must not guess which one it serves.
 */
class FrontendSetting(frontend: Frontend?, loginPage: String?) {

    val frontend: Frontend = checkNotNull(frontend) {
        "unividuell.auth.frontend is not set — say spa or server-rendered"
    }

    /** Where anonymous requests and logout land; null for a single-page app, which routes its own. */
    val loginPage: String? = when (this.frontend) {
        Frontend.SPA -> {
            check(loginPage.isNullOrBlank()) {
                "unividuell.auth.login-page is set for frontend spa — a single-page app routes its own login page; remove it"
            }
            null
        }
        Frontend.SERVER_RENDERED -> {
            val page = loginPage?.trim().orEmpty()
            check(page.isNotEmpty()) {
                "unividuell.auth.login-page is required for frontend server-rendered — a page of the app, e.g. /login"
            }
            check(isAppPage(page)) {
                "unividuell.auth.login-page '$page' must be a page of the app: a path starting with / outside /login/ and /oauth2/"
            }
            page
        }
    }

    /**
     * Where a sign-in returns to, for both doors. A single-page app keeps nothing: its bootstrap call
     * that got the 401, replayed after sign-in, would land the user on raw JSON. A server-rendered app
     * keeps the page in a cookie, never the session, and only pages the browser navigated to.
     */
    val requestCache: RequestCache = when (this.loginPage) {
        null -> NullRequestCache()
        else -> CookieRequestCache().apply { setRequestMatcher(PageNavigation) }
    }
}

/**
 * A plain path on this site that the lib does not own. A logout that lands under /login/ or /oauth2/
 * starts the sign-in again, which in production signs the user straight back in.
 */
private fun isAppPage(path: String): Boolean =
    path.startsWith("/") &&
        !path.startsWith("//") &&
        !path.startsWith("/\\") &&
        path.none { it == '?' || it == '#' } &&
        !path.startsWith("/login/") &&
        !path.startsWith("/oauth2/")
