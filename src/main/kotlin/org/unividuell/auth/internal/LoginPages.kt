package org.unividuell.auth.internal

import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import java.nio.charset.StandardCharsets

/** The server-rendered pages under /login. German copy; every echoed value is escaped. */
internal object LoginPages {

    fun error(): String = page(
        title = "Anmeldung fehlgeschlagen",
        body = """<h1>Anmeldung fehlgeschlagen</h1>
          <p>Die Anmeldung hat nicht geklappt.</p>
          <a class="action" href="/login">Erneut versuchen</a>""",
    )

    /**
     * The shell all pages share. Without the viewport meta, phones lay a page out at their ~980px
     * fallback and scale it down — that reads as a CSS bug and is not one.
     */
    fun page(title: String, body: String): String =
        """<!doctype html><html lang="de"><head><meta charset="utf-8">
          <meta name="viewport" content="width=device-width,initial-scale=1">
          <title>$title</title>
          <style>
            :root{color-scheme:light dark;--bg:#fafaf9;--card:#fff;--border:#e7e5e4;--fg:#1c1917;--hover:#f5f5f4}
            @media (prefers-color-scheme:dark){
              :root{--bg:#1c1917;--card:#292524;--border:#44403c;--fg:#fafaf9;--hover:#44403c}
            }
            *{box-sizing:border-box}
            body{margin:0;padding:1.5rem 1rem;min-height:100dvh;display:flex;align-items:center;justify-content:center;
                 font:16px/1.4 system-ui,sans-serif;background:var(--bg);color:var(--fg)}
            .card{width:100%;max-width:22rem;background:var(--card);border:1px solid var(--border);
                  border-radius:12px;padding:1.25rem}
            h1{font-size:1.125rem;font-weight:600;margin:0 0 1rem}
            p{margin:0 0 1rem}
            form{margin:0 0 .5rem}
            form:last-of-type{margin-bottom:0}
            button,a.action{display:flex;align-items:center;gap:.75rem;width:100%;min-height:44px;padding:.5rem .75rem;
                   border:1px solid var(--border);border-radius:8px;background:transparent;color:inherit;
                   font:inherit;text-align:left;text-decoration:none;cursor:pointer}
            button:hover,a.action:hover{background:var(--hover)}
            .action{justify-content:center}
            input[type=password]{width:100%;min-height:44px;padding:.5rem .75rem;margin:0 0 .5rem;
                   border:1px solid var(--border);border-radius:8px;background:transparent;color:inherit;font:inherit}
            .error{color:#dc2626;font-size:.9375rem}
            .chip{flex:none;display:grid;place-items:center;width:28px;height:28px;border-radius:50%;
                  background:#e7e5e4;font-size:15px;line-height:1}
          </style></head>
          <body><div class="card">$body</div></body></html>"""
}

internal fun html(body: String): ResponseEntity<String> =
    ResponseEntity.ok().contentType(MediaType("text", "html", StandardCharsets.UTF_8)).body(body)
