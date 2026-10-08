package org.unividuell.auth.internal

import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Lazy
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.core.oidc.OidcScopes
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.unividuell.auth.internal.provider.ProviderUserService
import org.unividuell.auth.internal.testlogin.TestLoginService

/**
 * Owns `GET /login/start`, the one URL the SPA's sign-in button points at: the picker while the test
 * login is on, the provider otherwise. Spring's generated login page stays off because oauth2Login
 * names this URL as its login page. The bare `/login` stays unmapped: an SPA routes it itself, and
 * edge and dev proxy forward only the paths below it.
 *
 * `?error` always renders the error page: Spring sends a failed callback here, and redirecting
 * straight back to the provider would loop on a claim that keeps failing. Only the parameter's
 * presence counts — MockMvc hands a valueless `?error` over as null, a servlet container as "".
 *
 * Eager, for its startup checks: lazy initialization would defer them to the first request.
 */
@Controller
@Lazy(false)
class LoginController(
    testLoginProvider: ObjectProvider<TestLoginService>,
    clients: ObjectProvider<ClientRegistrationRepository>,
) {

    private val testLogin: TestLoginService? = testLoginProvider.ifAvailable

    private val registrations: List<ClientRegistration> = registrations(clients.ifAvailable)

    /** Null while the picker owns the page; then neither "no client" nor "several" stops the start. */
    private val providerPath: String? =
        if (testLogin != null) null else singleProviderPath(registrations.map { it.registrationId })

    init {
        // With the picker on too: /oauth2/authorization/{id} stays reachable beside it.
        registrations.forEach(::checkMapped)

        check(testLogin?.gate?.hasKey != true || registrations.isEmpty()) {
            "unividuell.auth.test-login.key is set next to OAuth2 client(s) " +
                "${registrations.joinToString { it.registrationId }} (spring.security.oauth2.client.registration) — " +
                "/oauth2/authorization/{id} would be a door past the lock; remove the client"
        }
    }

    @GetMapping("/login/start")
    fun login(
        request: HttpServletRequest,
        @RequestParam(required = false) redirect: String?,
    ): ResponseEntity<String> {
        if ("error" in request.parameterMap) return html(LoginPages.error())

        val picker = testLogin
            ?: return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, requireNotNull(providerPath)).build()

        return html(picker.entryPage(request = request, redirect = redirect))
    }
}

private fun registrations(repository: ClientRegistrationRepository?): List<ClientRegistration> = when (repository) {
    null -> emptyList()
    is Iterable<*> -> repository.filterIsInstance<ClientRegistration>()
    else -> error("${repository.javaClass.name} cannot list its clients, and the sign-in needs to know them")
}

private fun singleProviderPath(ids: List<String>): String {
    check(ids.isNotEmpty()) {
        "No way to sign in: no OAuth2 client is registered and the test login is off " +
            "(profile 'production', or unividuell.auth.test-login.enabled=false)"
    }
    check(ids.size == 1) {
        "Sign-in has ${ids.size} OAuth2 clients (${ids.joinToString()}) but no chooser page yet — register exactly one"
    }
    return "/oauth2/authorization/${ids.single()}"
}

/** An unmapped client fails only at its callback; an `openid` one would sign in past the app. */
private fun checkMapped(registration: ClientRegistration) {
    val id = registration.registrationId
    check(ProviderUserService.supports(id)) {
        "OAuth2 client '$id' has no identity mapping — the starter maps only 'github'"
    }
    check(OidcScopes.OPENID !in registration.scopes) {
        "OAuth2 client '$id' requests scope 'openid', but there is no OpenID Connect mapping yet — " +
            "the starter maps only 'github', over plain OAuth2"
    }
}
