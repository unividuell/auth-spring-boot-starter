package org.unividuell.auth

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("unividuell.auth")
data class AuthProperties(
    /** Kebab-case role name → `provider:login` entries; see [RoleAllowlist]. */
    val roles: Map<String, List<String>> = emptyMap(),
    val testLogin: TestLogin = TestLogin(),
    val csrfCookie: CsrfCookie = CsrfCookie(),
    /** Who builds the pages: a single-page app in the browser, or the server. Required, no default. */
    val frontend: Frontend? = null,
    /** `server-rendered` only: where anonymous requests and logout land. A page of the app. */
    val loginPage: String? = null,
) {
    /** `spa` or `server-rendered` in configuration. */
    enum class Frontend { SPA, SERVER_RENDERED }

    data class TestLogin(
        /** Also requires a profile other than `production`. */
        val enabled: Boolean = true,
        /** Empty means no lock — allowed only while no profile is active, i.e. on localhost. */
        val key: String = "",
        val users: List<TestUser> = AuthProperties.FUTURAMA,
    )

    /** [name] is shown in the picker and handed to the app; [color] (`#rrggbb`) only tells the rows apart. */
    data class TestUser(val login: String, val name: String? = null, val color: String? = null) {
        init {
            // Lands in a style attribute: a plain hex value or nothing.
            require(color == null || HEX.matches(color)) { "test user $login: color must be #rrggbb, got: $color" }
        }
    }

    /** Paths whose responses must stay cookie-free, e.g. a publicly cached preview endpoint. */
    data class CsrfCookie(val excludedPaths: List<String> = emptyList())

    companion object {
        private val HEX = Regex("^#[0-9a-fA-F]{6}$")

        /** countdown's seed users. Logins spelled exactly as before: existing rows match on them. */
        val FUTURAMA: List<TestUser> = listOf(
            TestUser(login = "Fry", color = "#eb7d1f"),
            TestUser(login = "leela", name = "Turanga Leela", color = "#952f8e"),
            TestUser(login = "Bender", color = "#9daec7"),
            TestUser(login = "prof", name = "Prof Farnsworth", color = "#f6f6f6"),
            TestUser(login = "amy", color = "#f48fa6"),
            TestUser(login = "hermes", name = "Hermes Conrad", color = "#34330f"),
            TestUser(login = "zoidberg", name = "Dr. Zoidberg", color = "#f55f5f"),
            TestUser(login = "scruffy", name = "Scruffy", color = "#2e4a50"),
            TestUser(login = "zapp", name = "Zapp Brannigan", color = "#860523"),
            TestUser(login = "kif", name = "Kif Kroker", color = "#afd573"),
            TestUser(login = "nibbler", name = "Nibbler", color = "#000000"),
            TestUser(login = "mom", name = "Mom", color = "#043e41"),
        )
    }
}
