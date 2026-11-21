package org.unividuell.auth

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

class TestUserTest {

    @Test
    fun `refuses a colour that is not #rrggbb`() {
        // The colour lands in a style attribute: anything but a plain hex value is refused at boot.
        shouldThrow<IllegalArgumentException> {
            AuthProperties.TestUser(login = "fry", color = "red;background:url(x)")
        }.message shouldContain "#rrggbb"
    }
}
