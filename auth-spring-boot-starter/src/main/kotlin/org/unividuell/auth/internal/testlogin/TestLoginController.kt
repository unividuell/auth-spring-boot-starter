package org.unividuell.auth.internal.testlogin

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.view.RedirectView
import org.unividuell.auth.internal.html
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** The test login's two POSTs. The entry page is [TestLoginService.entryPage], served by `LoginController`. */
@Controller
class TestLoginController(private val testLogin: TestLoginService) {

    private val logger = KotlinLogging.logger {}

    private val securityContextRepository = HttpSessionSecurityContextRepository()

    @PostMapping("/login/test/as")
    fun signInAs(
        @RequestParam login: String,
        @RequestParam(required = false) redirect: String?,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): RedirectView {
        // TestLoginService.entryPage guards the page's door; this is the other door.
        if (!testLogin.gate.isOpen(request)) return redirectTo("/login/start")

        val user = testLogin.find(login) ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown test user")

        // As on the provider door: the app's hook failing is a failed sign-in, not a 500.
        val principal = try {
            testLogin.signIn(user)
        } catch (e: RuntimeException) {
            logger.warn(e) { "account provisioning failed for provider 'test'" }
            return redirectTo("/login/start?error")
        }

        // As the provider door does: a session id handed out before the sign-in must not be signed in.
        request.getSession(false)?.let { request.changeSessionId() }

        val token = OAuth2AuthenticationToken(principal, principal.authorities, "test")
        val context = SecurityContextHolder.createEmptyContext().apply { authentication = token }
        SecurityContextHolder.setContext(context)
        securityContextRepository.saveContext(context, request, response)

        return redirectTo(safeRedirect(redirect))
    }

    /**
     * Post/Redirect/Get on success, so a reload does not post the key again. A wrong key renders the
     * locked page instead of redirecting, which keeps the attempt out of the address bar and logs.
     */
    @PostMapping("/login/test/unlock")
    fun unlock(
        @RequestParam key: String,
        @RequestParam(required = false) redirect: String?,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ResponseEntity<String> {
        if (!testLogin.gate.accepts(key)) {
            // No rate limit guards this endpoint, so noticing attempts is what remains. Never the key.
            logger.warn { "rejected a wrong test-login key" }
            return html(TestLoginPages.locked(csrf = csrfToken(request), redirect = redirect, wrongKey = true))
        }

        testLogin.gate.unlock(request = request, response = response)

        // URLEncoder, not UriComponentsBuilder: the latter reads "{...}" as a template variable.
        val target = if (redirect.isNullOrBlank()) {
            "/login/start"
        } else {
            "/login/start?redirect=" + URLEncoder.encode(redirect, StandardCharsets.UTF_8)
        }
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, target).build()
    }

    /** Expansion off: RedirectView would read "{...}" in the target as a template and throw. */
    private fun redirectTo(target: String) = RedirectView(target).apply { setExpandUriTemplateVariables(false) }
}
