package org.unividuell.auth

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

class RoleAllowlistTest {

    @Test
    fun `grants a role to a listed identity, ignoring case`() {
        RoleAllowlist(mapOf("super-admin" to listOf("GitHub:Octocat")))
            .rolesFor(provider = "github", login = "octocat") shouldBe setOf("SUPER_ADMIN")
    }

    @Test
    fun `the same login at another provider holds nothing`() {
        RoleAllowlist(mapOf("super-admin" to listOf("github:octocat")))
            .rolesFor(provider = "test", login = "octocat")
            .shouldBeEmpty()
    }

    @Test
    fun `a kebab-case key names the role`() {
        RoleAllowlist(mapOf("technical-user" to listOf("github:bot")))
            .rolesFor(provider = "github", login = "bot") shouldBe setOf("TECHNICAL_USER")
    }

    @Test
    fun `one identity can hold several roles`() {
        RoleAllowlist(mapOf("super-admin" to listOf("test:prof"), "technical-user" to listOf("test:prof")))
            .rolesFor(provider = "test", login = "prof") shouldBe setOf("SUPER_ADMIN", "TECHNICAL_USER")
    }

    @Test
    fun `entries are trimmed and blank ones dropped`() {
        // Boot trims a comma-separated value itself; a quoted YAML entry or a direct caller is not.
        val allowlist = RoleAllowlist(mapOf("super-admin" to listOf(" github:alice", " github: bob ", "", "  ")))

        allowlist.members("SUPER_ADMIN") shouldBe listOf(
            RoleMember(provider = "github", login = "alice"),
            RoleMember(provider = "github", login = "bob"),
        )
        allowlist.rolesFor(provider = "github", login = "bob") shouldBe setOf("SUPER_ADMIN")
    }

    @Test
    fun `an entry without provider prefix stops the start`() {
        val thrown = shouldThrow<IllegalStateException> {
            RoleAllowlist(mapOf("super-admin" to listOf("octocat")))
        }

        thrown.message shouldContain "super-admin"
        thrown.message shouldContain "provider:login"
    }

    @Test
    fun `an entry without login stops the start`() {
        shouldThrow<IllegalStateException> {
            RoleAllowlist(mapOf("super-admin" to listOf("github:")))
        }
    }

    @Test
    fun `an unknown role has no members`() {
        RoleAllowlist(emptyMap()).members("SUPER_ADMIN").shouldBeEmpty()
    }
}
