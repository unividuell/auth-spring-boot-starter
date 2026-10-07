package org.unividuell.auth.internal

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.AuthenticationException
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.web.DefaultRedirectStrategy
import org.springframework.security.web.authentication.AuthenticationFailureHandler

/**
 * oauth2Login's failure handler: logs the failure and sends the browser to the error page. Spring's
 * default logs only at TRACE and stores the exception in the session — creating one for a forged
 * callback, a row each with Spring Session JDBC — for an error page that never reads it.
 */
internal class ProviderFailureHandler : AuthenticationFailureHandler {

    private val logger = KotlinLogging.logger {}

    private val redirectStrategy = DefaultRedirectStrategy()

    override fun onAuthenticationFailure(
        request: HttpServletRequest,
        response: HttpServletResponse,
        exception: AuthenticationException,
    ) {
        // Error code and description only: the cause chain may carry the provider's response.
        logger.warn {
            val error = (exception as? OAuth2AuthenticationException)?.error
            "provider sign-in failed: ${error?.errorCode ?: exception.javaClass.simpleName} ${error?.description.orEmpty()}"
                .trimEnd()
        }

        redirectStrategy.sendRedirect(request, response, "/login?error")
    }
}
