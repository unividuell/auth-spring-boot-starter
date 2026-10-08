package org.unividuell.auth

import java.util.UUID

/**
 * The one bean an app must provide: turn a signed-in identity into the app's own account id.
 *
 * Both doors — a real provider and the test login — end here, so a test user is provisioned exactly
 * like a real one. [roles] are the configured roles the identity holds right now, re-evaluated at
 * every sign-in; store them if the app reports on them. Throwing fails the sign-in.
 */
fun interface AccountProvisioner {
    fun provision(identity: ExternalIdentity, roles: Set<String>): UUID
}
