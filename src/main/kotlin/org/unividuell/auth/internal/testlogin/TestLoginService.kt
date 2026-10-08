package org.unividuell.auth.internal.testlogin

import jakarta.servlet.http.HttpServletRequest
import org.unividuell.auth.AuthPrincipal
import org.unividuell.auth.AuthProperties
import org.unividuell.auth.ExternalIdentity
import org.unividuell.auth.internal.AccountSignIn

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

    /** The entry page: the lock until this browser holds the key, then the picker. */
    fun entryPage(request: HttpServletRequest, redirect: String?): String =
        if (!gate.isOpen(request)) {
            TestLoginPages.locked(csrf = csrfToken(request), redirect = redirect, wrongKey = false)
        } else {
            TestLoginPages.picker(csrf = csrfToken(request), redirect = redirect, users = users)
        }
}
