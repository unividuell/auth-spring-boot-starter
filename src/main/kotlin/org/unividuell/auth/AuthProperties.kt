package org.unividuell.auth

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("unividuell.auth")
data class AuthProperties(
    /** Kebab-case role name → `provider:login` entries; see [RoleAllowlist]. */
    val roles: Map<String, List<String>> = emptyMap(),
    val testLogin: TestLogin = TestLogin(),
    val csrfCookie: CsrfCookie = CsrfCookie(),
) {
    data class TestLogin(
        /** Also requires a profile other than `production`. */
        val enabled: Boolean = true,
        /** Empty means no lock — allowed only while no profile is active, i.e. on localhost. */
        val key: String = "",
        val users: List<TestUser> = AuthProperties.FUTURAMA,
    )

    /** [name] is shown in the picker and handed to the app; [emoji] only tells the rows apart. */
    data class TestUser(val login: String, val name: String? = null, val emoji: String = "🙂")

    /** Paths whose responses must stay cookie-free, e.g. a publicly cached preview endpoint. */
    data class CsrfCookie(val excludedPaths: List<String> = emptyList())

    companion object {
        /** countdown's seed users. Logins spelled exactly as before: existing rows match on them. */
        val FUTURAMA: List<TestUser> = listOf(
            TestUser(login = "Fry", emoji = "🍕"),
            TestUser(login = "leela", name = "Turanga Leela", emoji = "👁️"),
            TestUser(login = "Bender", emoji = "🤖"),
            TestUser(login = "prof", name = "Prof Farnsworth", emoji = "🔬"),
            TestUser(login = "amy", emoji = "💅"),
            TestUser(login = "hermes", name = "Hermes Conrad", emoji = "📋"),
            TestUser(login = "zoidberg", name = "Dr. Zoidberg", emoji = "🦞"),
            TestUser(login = "scruffy", name = "Scruffy", emoji = "🧹"),
            TestUser(login = "zapp", name = "Zapp Brannigan", emoji = "🎖️"),
            TestUser(login = "kif", name = "Kif Kroker", emoji = "😩"),
            TestUser(login = "nibbler", name = "Nibbler", emoji = "🐾"),
            TestUser(login = "mom", name = "Mom", emoji = "🏭"),
        )
    }
}
