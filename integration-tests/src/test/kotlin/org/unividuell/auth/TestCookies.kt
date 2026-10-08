package org.unividuell.auth

import org.springframework.mock.web.MockHttpServletResponse

/** A cookie's value read from the raw Set-Cookie headers, whether or not the mock also parses them. */
fun MockHttpServletResponse.setCookieValue(name: String): String? =
    getHeaders("Set-Cookie")
        .firstOrNull { it.startsWith("$name=") }
        ?.substringBefore(";")
        ?.substringAfter("$name=")
