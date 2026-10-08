package org.unividuell.auth.internal

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.unividuell.auth.AccountProvisioner
import org.unividuell.auth.ExternalIdentity
import org.unividuell.auth.RoleAllowlist
import java.util.UUID

class AccountSignInTest {

    private val accountId = UUID.fromString("018f0000-0000-7000-8000-000000000002")
    private val calls = mutableListOf<Pair<ExternalIdentity, Set<String>>>()

    private val signIn = AccountSignIn(
        provisioner = AccountProvisioner { identity, roles ->
            calls += identity to roles
            accountId
        },
        allowlist = RoleAllowlist(mapOf("super-admin" to listOf("test:prof"), "technical-user" to listOf("test:prof"))),
    )

    private fun identity(login: String) =
        ExternalIdentity(provider = "test", subject = login, login = login, name = "Prof Farnsworth", email = null)

    @Test
    fun `hands the configured roles to the app and into the principal`() {
        val principal = signIn.signIn(identity(login = "prof"))

        calls.single() shouldBe (identity(login = "prof") to setOf("SUPER_ADMIN", "TECHNICAL_USER"))
        principal.id shouldBe accountId
        principal.provider shouldBe "test"
        principal.login shouldBe "prof"
        principal.roles shouldBe setOf("SUPER_ADMIN", "TECHNICAL_USER")
    }

    @Test
    fun `an identity on no list signs in without roles`() {
        signIn.signIn(identity(login = "Fry")).roles.shouldBeEmpty()

        calls.single().second.shouldBeEmpty()
    }

    @Test
    fun `a failing provisioner is not swallowed here`() {
        val failing = AccountSignIn(
            provisioner = AccountProvisioner { _, _ -> error("database down") },
            allowlist = RoleAllowlist(emptyMap()),
        )

        shouldThrow<IllegalStateException> { failing.signIn(identity(login = "Fry")) }
    }
}
