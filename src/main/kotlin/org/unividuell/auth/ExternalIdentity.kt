package org.unividuell.auth

/**
 * Who a sign-in door says the person is, before the app has looked them up.
 *
 * [subject] is the provider's stable id, always a String: Discord ids are 64-bit snowflakes that a
 * JSON number cannot carry beyond 2^53, so GitHub's numeric id is stringified to match.
 */
data class ExternalIdentity(
    val provider: String,
    val subject: String,
    val login: String,
    val name: String?,
    val email: String?,
)
