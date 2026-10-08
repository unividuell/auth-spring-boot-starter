package org.unividuell.auth.internal

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
