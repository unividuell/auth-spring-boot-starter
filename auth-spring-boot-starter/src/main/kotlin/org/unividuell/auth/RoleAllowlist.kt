package org.unividuell.auth

/** One configured role holder, normalized: provider and login lowercase. */
data class RoleMember(val provider: String, val login: String)

/**
 * Roles granted by configuration (`unividuell.auth.roles`), one list of `provider:login` per role;
 * the kebab-case key names the role (`super-admin` → `SUPER_ADMIN`).
 *
 * These are the only roles that belong in the session principal: a configured list is re-read at
 * every sign-in, so a principal is stale at most until the next one. Rights granted at runtime belong
 * in the app's own rows, read live.
 */
class RoleAllowlist(entriesByRole: Map<String, List<String>>) {

    init {
        // Keys that fold onto one role would collide below, the last list silently replacing the first.
        entriesByRole.keys.groupBy { roleName(key = it) }.forEach { (role, keys) ->
            check(keys.size == 1) { "unividuell.auth.roles keys ${keys.joinToString()} all name the role $role — keep one" }
        }
    }

    private val membersByRole: Map<String, List<RoleMember>> =
        entriesByRole.entries.associate { (key, values) ->
            roleName(key = key) to values.mapNotNull { parse(key = key, entry = it) }
        }

    /** The roles this identity holds; provider and login match case-insensitively. */
    fun rolesFor(provider: String, login: String): Set<String> {
        val candidate = RoleMember(provider = provider.lowercase(), login = login.lowercase())
        return membersByRole.filterValues { candidate in it }.keys.toSortedSet()
    }

    /** The configured holders of [role] (e.g. "SUPER_ADMIN"), for apps that report on them. */
    fun members(role: String): List<RoleMember> = membersByRole[role].orEmpty()

    private fun roleName(key: String): String {
        check(key.isNotBlank()) { "unividuell.auth.roles contains a blank role name" }
        return key.trim().uppercase().replace(oldChar = '-', newChar = '_')
    }

    private fun parse(key: String, entry: String): RoleMember? {
        val trimmed = entry.trim()
        if (trimmed.isEmpty()) return null

        val provider = trimmed.substringBefore(delimiter = ':', missingDelimiterValue = "").trim()
        val login = trimmed.substringAfter(delimiter = ':', missingDelimiterValue = "").trim()
        check(provider.isNotEmpty() && login.isNotEmpty()) {
            "unividuell.auth.roles.$key: '$entry' lacks the provider prefix — " +
                "write it as provider:login, e.g. github:octocat"
        }

        return RoleMember(provider = provider.lowercase(), login = login.lowercase())
    }
}
