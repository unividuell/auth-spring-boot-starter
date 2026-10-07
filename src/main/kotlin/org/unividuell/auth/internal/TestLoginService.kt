package org.unividuell.auth.internal

import org.unividuell.auth.AuthPrincipal
import org.unividuell.auth.AuthProperties
import org.unividuell.auth.ExternalIdentity

/** The configured test users and how one signs in: provider "test", subject = login. */
class TestLoginService(
    val users: List<AuthProperties.TestUser>,
    val gate: FakeSignInGate,
    private val accounts: AccountSignIn,
) {
    /** Exact match: only a configured login resolves, never any account by name. */
    fun find(login: String): AuthProperties.TestUser? = users.firstOrNull { it.login == login }

    fun signIn(user: AuthProperties.TestUser): AuthPrincipal = accounts.signIn(
        ExternalIdentity(provider = "test", subject = user.login, login = user.login, name = user.name, email = null),
    )
}
