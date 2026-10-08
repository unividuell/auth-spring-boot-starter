package org.unividuell.auth.internal.testlogin

/**
 * [candidate] if it is a path on this site, else "/". Protocol-relative forms leave the site; tab,
 * CR and LF are refused because browsers strip them before resolving, turning a harmless-looking
 * path into a protocol-relative one. Every caller is permitAll, so a gap here is an open redirect.
 */
fun safeRedirect(candidate: String?): String {
    if (candidate == null) return "/"
    if (candidate.any { it == '\t' || it == '\n' || it == '\r' }) return "/"

    val sameSite = candidate.startsWith("/") &&
        !candidate.startsWith("//") &&
        !candidate.startsWith("/\\")
    return if (sameSite) candidate else "/"
}
