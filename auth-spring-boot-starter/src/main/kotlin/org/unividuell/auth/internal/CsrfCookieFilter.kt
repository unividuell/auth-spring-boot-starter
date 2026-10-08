package org.unividuell.auth.internal

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.server.PathContainer
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.pattern.PathPatternParser

/**
 * Reads the deferred [CsrfToken] on every request, which makes `CookieCsrfTokenRepository` write the
 * `XSRF-TOKEN` cookie. A plain GET — the SPA's bootstrap call — never reads it by itself, so the SPA
 * would have no cookie to echo and `POST /logout` would answer 403.
 *
 * [excludedPaths] are skipped: a Set-Cookie on a publicly cached response defeats every shared cache.
 */
class CsrfCookieFilter(excludedPaths: List<String>) : OncePerRequestFilter() {

    private val excluded = excludedPaths.map { PathPatternParser.defaultInstance.parse(it) }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = PathContainer.parsePath(request.requestURI)
        return excluded.any { it.matches(path) }
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        (request.getAttribute(CsrfToken::class.java.name) as? CsrfToken)?.token
        filterChain.doFilter(request, response)
    }
}
