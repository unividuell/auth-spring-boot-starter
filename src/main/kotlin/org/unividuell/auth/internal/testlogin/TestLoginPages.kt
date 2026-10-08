package org.unividuell.auth.internal.testlogin

import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.web.csrf.CsrfToken
import org.unividuell.auth.AuthProperties
import org.unividuell.auth.internal.LoginPages.escape
import org.unividuell.auth.internal.LoginPages.page

/** The test login's pages, in the core's shell. German copy; every echoed value is escaped. */
internal object TestLoginPages {

    fun picker(csrf: CsrfToken, redirect: String?, users: List<AuthProperties.TestUser>): String {
        val buttons = users.joinToString(separator = "\n") { user ->
            """<form method="post" action="/login/test/as">
                 <input type="hidden" name="_csrf" value="${escape(csrf.token)}"/>
                 <input type="hidden" name="login" value="${escape(user.login)}"/>
                 <input type="hidden" name="redirect" value="${escape(redirect.orEmpty())}"/>
                 <button type="submit">
                   <span class="chip" aria-hidden="true">${escape(user.emoji)}</span>
                   <span>${escape(user.name ?: user.login)}</span>
                 </button>
               </form>"""
        }
        return page(title = "Test-Login", body = """<h1>Test-Login</h1>$buttons""")
    }

    /**
     * The locked page is the keyhole: being locked out always shows where the key goes. It names no
     * test user — a locked door that lists what is behind it shows what there is to take.
     */
    fun locked(csrf: CsrfToken, redirect: String?, wrongKey: Boolean): String {
        val error = if (wrongKey) """<p class="error">Falscher Schlüssel.</p>""" else ""
        return page(
            title = "Gesperrt",
            body = """<h1>Gesperrt</h1>
              <p>Diese Umgebung ist nicht öffentlich.</p>
              $error
              <form method="post" action="/login/test/unlock">
                <input type="hidden" name="_csrf" value="${escape(csrf.token)}"/>
                <input type="hidden" name="redirect" value="${escape(redirect.orEmpty())}"/>
                <input type="password" name="key" autocomplete="current-password" autofocus required
                       aria-label="Zugangsschlüssel" placeholder="Zugangsschlüssel"/>
                <button type="submit" class="action"><span>Freischalten</span></button>
              </form>""",
        )
    }
}

fun csrfToken(request: HttpServletRequest): CsrfToken = request.getAttribute(CsrfToken::class.java.name) as CsrfToken
