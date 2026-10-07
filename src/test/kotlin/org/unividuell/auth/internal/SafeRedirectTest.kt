package org.unividuell.auth.internal

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SafeRedirectTest {

    @Test
    fun `keeps a path on this site`() {
        safeRedirect("/c/team/lab/sample?seed=42") shouldBe "/c/team/lab/sample?seed=42"
    }

    @Test
    fun `keeps a brace in a path`() {
        safeRedirect("/a{b}") shouldBe "/a{b}"
    }

    @Test
    fun `falls back to the root without a candidate`() {
        safeRedirect(null) shouldBe "/"
        safeRedirect("") shouldBe "/"
    }

    @Test
    fun `refuses everything that leaves the site`() {
        // Browsers strip tab, CR and LF before resolving, so each of the last three becomes
        // protocol-relative after a naive prefix check has already passed it.
        listOf(
            "//evil.example",
            "https://evil.example",
            "/\\evil.example",
            "evil",
            "/\t/evil.example",
            "/\n/evil.example",
            "/\r/evil.example",
        ).forEach { hostile -> safeRedirect(hostile) shouldBe "/" }
    }
}
