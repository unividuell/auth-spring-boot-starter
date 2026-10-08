package org.unividuell.auth.internal

import org.unividuell.auth.AccountProvisioner
import org.unividuell.auth.AuthPrincipal
import org.unividuell.auth.ExternalIdentity
import org.unividuell.auth.RoleAllowlist

/** The step both doors share: roles from the allowlist, account from the app, principal for the session. */
class AccountSignIn(
    private val provisioner: AccountProvisioner,
    private val allowlist: RoleAllowlist,
) {
    fun signIn(identity: ExternalIdentity): AuthPrincipal {
        val roles = allowlist.rolesFor(provider = identity.provider, login = identity.login)
        val id = provisioner.provision(identity = identity, roles = roles)
        return AuthPrincipal(id = id, provider = identity.provider, login = identity.login, roles = roles)
    }
}
