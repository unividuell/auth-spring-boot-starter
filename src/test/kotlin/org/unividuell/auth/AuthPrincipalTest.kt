package org.unividuell.auth

import io.kotest.assertions.fail
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

class AuthPrincipalTest {

    private val principal = AuthPrincipal(
        id = UUID.fromString("018f0000-0000-7000-8000-000000000000"),
        provider = "github",
        login = "octocat",
        roles = setOf("TECHNICAL_USER", "SUPER_ADMIN"),
    )

    @Test
    fun `name is the account id`() {
        principal.name shouldBe "018f0000-0000-7000-8000-000000000000"
    }

    @Test
    fun `every principal is a user, plus one authority per role`() {
        principal.authorities.map { it.authority } shouldContainExactlyInAnyOrder
            listOf("ROLE_USER", "ROLE_SUPER_ADMIN", "ROLE_TECHNICAL_USER")
    }

    @Test
    fun `without roles only ROLE_USER remains`() {
        AuthPrincipal(id = principal.id, provider = "test", login = "Fry", roles = emptySet())
            .authorities.map { it.authority } shouldBe listOf("ROLE_USER")
    }

    @Test
    fun `survives a serialization round trip`() {
        val restored = deserialize(serialize(principal))

        restored.id shouldBe principal.id
        restored.provider shouldBe "github"
        restored.login shouldBe "octocat"
        restored.roles shouldBe setOf("SUPER_ADMIN", "TECHNICAL_USER")
    }

    /**
     * Sessions outlive deploys: a principal written by 0.1.0 must still load after every upgrade,
     * or each user is logged out. The first run writes the fixture and fails; commit it, run again.
     */
    @Test
    fun `reads a principal serialized by 0_1_0`() {
        val fixture = Path.of("src/test/resources/golden/auth-principal-0.1.0.ser")
        if (Files.notExists(fixture)) {
            Files.createDirectories(fixture.parent)
            Files.write(fixture, serialize(principal))
            fail("wrote $fixture — commit it and run again")
        }

        val restored = deserialize(Files.readAllBytes(fixture))

        restored.id shouldBe principal.id
        restored.provider shouldBe "github"
        restored.login shouldBe "octocat"
        restored.roles shouldBe setOf("SUPER_ADMIN", "TECHNICAL_USER")
    }

    private fun serialize(value: AuthPrincipal): ByteArray {
        val bytes = ByteArrayOutputStream()
        ObjectOutputStream(bytes).use { it.writeObject(value) }
        return bytes.toByteArray()
    }

    private fun deserialize(bytes: ByteArray): AuthPrincipal =
        ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() } as AuthPrincipal
}
