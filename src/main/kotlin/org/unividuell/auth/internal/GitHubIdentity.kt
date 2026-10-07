package org.unividuell.auth.internal

import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.OAuth2Error
import org.unividuell.auth.ExternalIdentity

/** GitHub's `/user` attributes as an [ExternalIdentity]; `id` and `login` are mandatory. */
fun gitHubIdentity(attributes: Map<String, Any>): ExternalIdentity {
    val id = (attributes["id"] as? Number)?.toLong()
        ?: throw invalidClaims("missing or non-numeric 'id' in GitHub attributes")
    val login = attributes["login"] as? String
        ?: throw invalidClaims("missing or non-string 'login' in GitHub attributes")

    return ExternalIdentity(
        provider = "github",
        subject = id.toString(),
        login = login,
        name = attributes["name"] as? String,
        email = attributes["email"] as? String,
    )
}

fun invalidClaims(message: String) =
    OAuth2AuthenticationException(OAuth2Error("invalid_claims", message, null), message)
