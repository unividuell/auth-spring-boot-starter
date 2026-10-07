package org.unividuell.auth.internal

import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.ObjectProvider
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

/**
 * Owns `GET /login`, the one URL the SPA's sign-in button points at. Spring's generated login page
 * stays off because oauth2Login names this URL as its login page.
 *
 * `?error` always renders the error page: Spring sends a failed callback here, and redirecting
 * straight back to the provider would loop on a claim that keeps failing. Only the parameter's
 * presence counts — MockMvc hands a valueless `?error` over as null, a servlet container as "".
 */
@Controller
class LoginController(clients: ObjectProvider<ClientRegistrationRepository>) {

    private val providerPath: String = singleProviderPath(registrationIds(clients.ifAvailable))

    @GetMapping("/login")
    fun login(request: HttpServletRequest): ResponseEntity<String> {
        if ("error" in request.parameterMap) return html(LoginPages.error())

        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, providerPath).build()
    }
}

private fun registrationIds(repository: ClientRegistrationRepository?): List<String> = when (repository) {
    null -> emptyList()
    is Iterable<*> -> repository.filterIsInstance<ClientRegistration>().map { it.registrationId }
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
