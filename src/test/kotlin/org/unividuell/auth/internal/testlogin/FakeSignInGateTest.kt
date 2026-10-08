package org.unividuell.auth.internal.testlogin

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Test
import org.springframework.mock.env.MockEnvironment
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class FakeSignInGateTest {

    private fun profiles(vararg names: String) = MockEnvironment().apply { setActiveProfiles(*names) }

    private fun gate(key: String, environment: MockEnvironment = profiles()) =
        FakeSignInGate(rawKey = key, environment = environment)

    private fun issuedCookie(gate: FakeSignInGate, request: MockHttpServletRequest = MockHttpServletRequest()): String {
        val response = MockHttpServletResponse()
        gate.unlock(request = request, response = response)
        return response.getHeader("Set-Cookie").shouldNotBeNull()
    }

    private fun requestWith(value: String) = MockHttpServletRequest().apply {
        setCookies(Cookie(FakeSignInGate.COOKIE_NAME, value))
    }

    @Test
    fun `an empty key is no lock at all`() {
        gate(key = "").isOpen(MockHttpServletRequest()) shouldBe true
        gate(key = "   ").isOpen(MockHttpServletRequest()) shouldBe true
    }

    @Test
    fun `a configured key locks a request without the cookie`() {
        gate(key = "open-sesame").isOpen(MockHttpServletRequest()) shouldBe false
    }

    @Test
    fun `a request carrying the cookie the gate issued is open`() {
        val gate = gate(key = "open-sesame")
        val value = issuedCookie(gate).substringAfter("=").substringBefore(";")

        gate.isOpen(requestWith(value)) shouldBe true
    }

    @Test
    fun `a cookie from a different key stays locked`() {
        val foreign = issuedCookie(gate(key = "another-key")).substringAfter("=").substringBefore(";")

        gate(key = "open-sesame").isOpen(requestWith(foreign)) shouldBe false
    }

    @Test
    fun `the cookie carries the hash, never the key`() {
        issuedCookie(gate(key = "open-sesame")) shouldNotContain "open-sesame"
    }

    @Test
    fun `the cookie is scoped, long-lived and not readable by scripts`() {
        val header = issuedCookie(gate(key = "open-sesame"))

        header shouldContain "Path=/login"
        header shouldContain "HttpOnly"
        header shouldContain "SameSite=Lax"
        header shouldContain "Max-Age=31536000"
        // A plain-HTTP request: Secure must follow the request, or localhost could never unlock.
        header shouldNotContain "Secure"
    }

    @Test
    fun `a secure request gets a secure cookie`() {
        issuedCookie(gate = gate(key = "open-sesame"), request = MockHttpServletRequest().apply { isSecure = true }) shouldContain
            "Secure"
    }

    @Test
    fun `accepts the configured key and nothing else`() {
        val gate = gate(key = "open-sesame")

        gate.accepts("open-sesame") shouldBe true
        gate.accepts("  open-sesame  ") shouldBe true
        gate.accepts("open-sesam") shouldBe false
        gate.accepts("") shouldBe false
    }

    @Test
    fun `without a key everything is accepted`() {
        gate(key = "").accepts("whatever") shouldBe true
    }

    @Test
    fun `any active profile refuses to start without a key`() {
        // Not just staging: a new app deployed without its profile would otherwise stand open.
        listOf("staging", "demo").forEach { profile ->
            val thrown = shouldThrow<IllegalStateException> {
                gate(key = "", environment = profiles(profile))
            }
            thrown.message shouldContain "unividuell.auth.test-login.key"
            thrown.message shouldContain profile
        }
    }

    @Test
    fun `a profile with a key starts locked`() {
        gate(key = "open-sesame", environment = profiles("staging")).isOpen(MockHttpServletRequest()) shouldBe false
    }
}
