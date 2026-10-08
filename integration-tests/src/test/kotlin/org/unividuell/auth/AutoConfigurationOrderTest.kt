package org.unividuell.auth

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration
import org.springframework.security.web.SecurityFilterChain
import java.util.UUID

/**
 * Only the `before` entries order the lib ahead of actuator's security: sorted by name alone,
 * `org.springframework` comes first and its form-login chain would be the app's default. Either
 * entry suffices today — Boot orders OAuth2ClientWebSecurityAutoConfiguration before actuator's.
 */
class AutoConfigurationOrderTest {

    @Test
    fun `the lib's default chain wins over actuator's`() {
        WebApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                    ManagementWebSecurityAutoConfiguration::class.java,
                    ServletWebSecurityAutoConfiguration::class.java,
                    AuthAutoConfiguration::class.java,
                    // Actuator's chain reads its CORS setup from MVC; without it a mis-order fails to start instead.
                    WebMvcAutoConfiguration::class.java,
                ),
            )
            .withBean(AccountProvisioner::class.java, { AccountProvisioner { _, _ -> UUID.randomUUID() } })
            .withPropertyValues("unividuell.auth.frontend=spa")
            .run { context ->
                context.getBeansOfType(SecurityFilterChain::class.java).keys shouldBe setOf("authDefaultSecurityFilterChain")
            }
    }
}
