package org.unividuell.auth.internal.provider

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.oauth2.core.user.OAuth2User
import org.unividuell.auth.ExternalIdentity
import org.unividuell.auth.internal.AccountSignIn

/**
 * oauth2Login's user service: loads the provider's user-info, maps it per provider and signs the
 * identity in. Every failure becomes an [OAuth2AuthenticationException], which Spring routes to
 * `/login/start?error` instead of failing the callback request.
 */
class ProviderUserService(
    private val signIn: AccountSignIn,
    private val delegate: OAuth2UserService<OAuth2UserRequest, OAuth2User> = DefaultOAuth2UserService(),
) : OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private val logger = KotlinLogging.logger {}

    override fun loadUser(userRequest: OAuth2UserRequest): OAuth2User {
        val provider = userRequest.clientRegistration.registrationId
        val mapping = MAPPINGS[provider] ?: throw unsupported(provider)

        val user = delegate.loadUser(userRequest)
            ?: throw invalidClaims("the $provider user-info endpoint returned no user")
        val identity = mapping(user.attributes)

        return try {
            signIn.signIn(identity)
        } catch (e: RuntimeException) {
            logger.warn(e) { "account provisioning failed for provider '$provider'" }
            throw OAuth2AuthenticationException(OAuth2Error("provisioning_failed"))
        }
    }

    companion object {
        /** Keyed by registration id. A new provider adds its mapping here. */
        private val MAPPINGS: Map<String, (Map<String, Any>) -> ExternalIdentity> = mapOf("github" to ::gitHubIdentity)

        /** Whether a client of this registration id can sign in; the start is refused for one that cannot. */
        internal fun supports(registrationId: String): Boolean = registrationId in MAPPINGS

        /**
         * oauth2Login's OIDC user service. No OIDC mapping exists yet; should an `openid` client slip past
         * the startup check, its sign-in fails here instead of reaching Spring's stock service, which
         * would sign the user in past the [AccountSignIn].
         */
        internal val oidcRefusal: OAuth2UserService<OidcUserRequest, OidcUser> =
            OAuth2UserService { throw unsupported(it.clientRegistration.registrationId) }

        private fun unsupported(provider: String): OAuth2AuthenticationException {
            val message = "no identity mapping for provider '$provider'"
            return OAuth2AuthenticationException(OAuth2Error("unsupported_provider", message, null), message)
        }
    }
}
