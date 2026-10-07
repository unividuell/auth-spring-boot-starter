package org.unividuell.auth.internal

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService
import org.springframework.security.oauth2.core.OAuth2AccessToken
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.user.DefaultOAuth2User
import org.springframework.security.oauth2.core.user.OAuth2User
import org.unividuell.auth.AccountProvisioner
import org.unividuell.auth.AuthPrincipal
import org.unividuell.auth.ExternalIdentity
import org.unividuell.auth.RoleAllowlist
import java.time.Instant
import java.util.UUID

class ProviderUserServiceTest {

    private val accountId = UUID.fromString("018f0000-0000-7000-8000-000000000001")
    private val provisioned = mutableListOf<ExternalIdentity>()

    private val signIn = AccountSignIn(
        provisioner = AccountProvisioner { identity, _ ->
            provisioned += identity
            accountId
        },
        allowlist = RoleAllowlist(mapOf("super-admin" to listOf("github:octocat"))),
    )

    private fun request(registrationId: String = "github") = OAuth2UserRequest(
        CommonOAuth2Provider.GITHUB.getBuilder(registrationId).clientId("client").clientSecret("secret").build(),
        OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "token", Instant.now(), Instant.now().plusSeconds(60)),
    )

    /** The delegate stands in for the user-info call: it hands back [attributes] for any request. */
    private fun service(attributes: Map<String, Any>, accounts: AccountSignIn = signIn) = ProviderUserService(
        signIn = accounts,
        delegate = OAuth2UserService<OAuth2UserRequest, OAuth2User> {
            DefaultOAuth2User(emptyList(), attributes, attributes.keys.first())
        },
    )

    @Test
    fun `maps GitHub's user-info and signs the identity in`() {
        val principal = service(
            attributes = mapOf(
                "id" to 1_099_511_627_776L,
                "login" to "octocat",
                "name" to "The Octocat",
                "email" to "cat@example.com",
            ),
        ).loadUser(request())

        principal.shouldBeInstanceOf<AuthPrincipal>()
        principal.id shouldBe accountId
        principal.roles shouldBe setOf("SUPER_ADMIN")
        // Above 2^32, so an Int anywhere on the way would change it.
        provisioned.single() shouldBe ExternalIdentity(
            provider = "github",
            subject = "1099511627776",
            login = "octocat",
            name = "The Octocat",
            email = "cat@example.com",
        )
    }

    @Test
    fun `a GitHub user without an id cannot sign in`() {
        shouldThrow<OAuth2AuthenticationException> {
            service(attributes = mapOf("login" to "octocat")).loadUser(request())
        }

        provisioned.shouldBeEmpty()
    }

    @Test
    fun `a non-numeric id is no id`() {
        val thrown = shouldThrow<OAuth2AuthenticationException> {
            service(attributes = mapOf("id" to "4711", "login" to "octocat")).loadUser(request())
        }

        thrown.error.errorCode shouldBe "invalid_claims"
    }

    @Test
    fun `a GitHub user without a login cannot sign in`() {
        shouldThrow<OAuth2AuthenticationException> {
            service(attributes = mapOf("id" to 4711)).loadUser(request())
        }

        provisioned.shouldBeEmpty()
    }

    @Test
    fun `a provider without a mapping fails the sign-in`() {
        val thrown = shouldThrow<OAuth2AuthenticationException> {
            service(attributes = mapOf("id" to 4711, "login" to "octocat")).loadUser(request(registrationId = "discord"))
        }

        thrown.error.errorCode shouldBe "unsupported_provider"
        provisioned.shouldBeEmpty()
    }

    @Test
    fun `a failing provisioner fails the sign-in instead of the request`() {
        val failing = AccountSignIn(
            provisioner = AccountProvisioner { _, _ -> error("database down") },
            allowlist = RoleAllowlist(emptyMap()),
        )

        val thrown = shouldThrow<OAuth2AuthenticationException> {
            service(attributes = mapOf("id" to 4711, "login" to "octocat"), accounts = failing).loadUser(request())
        }

        thrown.error.errorCode shouldBe "provisioning_failed"
        // The failure handler puts this exception into the session, so the app's throwable must not ride along.
        thrown.cause.shouldBeNull()
    }
}
