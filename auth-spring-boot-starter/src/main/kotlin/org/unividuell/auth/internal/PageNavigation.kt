package org.unividuell.auth.internal

import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.web.util.matcher.RequestMatcher

/**
 * The requests a server-rendered app's sign-in may return to: pages the browser navigated to. A
 * fetch, XHR or htmx request announces itself with `Sec-Fetch-Mode: cors` or `same-origin`; replayed
 * as a GET after sign-in it would land the user on a fragment or a 405. Without the header (curl,
 * MockMvc, very old browsers) a GET counts as a navigation.
 */
internal object PageNavigation : RequestMatcher {
    override fun matches(request: HttpServletRequest): Boolean =
        request.method == "GET" && (request.getHeader("Sec-Fetch-Mode") ?: "navigate") == "navigate"
}
