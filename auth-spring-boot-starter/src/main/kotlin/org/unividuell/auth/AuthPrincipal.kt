package org.unividuell.auth

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.core.user.OAuth2User
import java.io.Serializable
import java.util.TreeSet
import java.util.UUID

/**
 * The session principal. It is JDK-serialized into the session store at sign-in and never refreshed,
 * so it holds only what was true at sign-in — no app data, no rights granted at runtime. An
 * incompatible change logs every user out; `AuthPrincipalTest` keeps the 0.1.0 form readable.
 */
class AuthPrincipal(
    val id: UUID,
    val provider: String,
    val login: String,
    roles: Set<String>,
) : OAuth2User, Serializable {

    /** A sorted copy: a [TreeSet] serializes the same way whichever set the caller passed. */
    val roles: Set<String> = TreeSet(roles)

    override fun getName(): String = id.toString()

    override fun getAttributes(): Map<String, Any> = mapOf("provider" to provider, "login" to login)

    override fun getAuthorities(): Collection<GrantedAuthority> =
        listOf(SimpleGrantedAuthority("ROLE_USER")) + roles.map { SimpleGrantedAuthority("ROLE_$it") }

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
