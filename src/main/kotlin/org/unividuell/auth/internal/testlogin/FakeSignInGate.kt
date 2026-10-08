package org.unividuell.auth.internal.testlogin

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.env.Environment
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import java.security.MessageDigest
import java.time.Duration

/**
 * The lock in front of the test login. A deployed non-prod environment runs real data on a public
 * URL, so the picker asks for a key once per browser profile.
 */
class FakeSignInGate(rawKey: String, environment: Environment) {

    /** Trimmed and blank-folded once: "no key" must mean the same to every caller. */
    private val key: String? = rawKey.trim().ifBlank { null }

    /** What a browser must present — the hash, never the key itself. */
    private val expected: String? = key?.let(::sha256Hex)

    /** Whether a key is set, i.e. this is a deployed environment's lock. */
    val hasKey: Boolean = key != null

    init {
        val active = environment.activeProfiles
        // Any profile is a deployed environment. Compose passes a missing variable through as an
        // empty string, so an unset key would leave the door open and look healthy doing it.
        check(key != null || active.isEmpty()) {
            "unividuell.auth.test-login.key is empty while profile(s) ${active.joinToString()} are active — " +
                "set a key, or switch the test login off with unividuell.auth.test-login.enabled=false"
        }
    }

    /** No key, no lock — the localhost default. */
    fun isOpen(request: HttpServletRequest): Boolean {
        val expected = expected ?: return true
        val presented = request.cookies?.firstOrNull { it.name == COOKIE_NAME }?.value ?: return false
        return constantTimeEquals(presented = presented, expected = expected)
    }

    /** Whether a typed-in candidate is the key. Compared as hashes, so always of equal length. */
    fun accepts(candidate: String): Boolean {
        val expected = expected ?: return true
        return constantTimeEquals(presented = sha256Hex(candidate.trim()), expected = expected)
    }

    /**
     * Issues the cookie. It holds the key's hash because the DevTools cookie panel shows values in
     * the clear and screenshots travel. `Path=/login` because nothing else reads it; `Secure` from
     * the request, or plain-HTTP localhost could never unlock.
     */
    fun unlock(request: HttpServletRequest, response: HttpServletResponse) {
        val value = expected ?: return
        val cookie = ResponseCookie.from(COOKIE_NAME, value)
            .path("/login")
            .httpOnly(true)
            .secure(request.isSecure)
            .sameSite("Lax")
            .maxAge(Duration.ofDays(365))
            .build()

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString())
    }

    private fun constantTimeEquals(presented: String, expected: String) =
        MessageDigest.isEqual(presented.toByteArray(Charsets.UTF_8), expected.toByteArray(Charsets.UTF_8))

    private fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { "%02x".format(it) }

    companion object {
        const val COOKIE_NAME = "unividuell_auth_test_login"
    }
}
