package org.unividuell.auth

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.context.annotation.Configuration
import org.unividuell.auth.testapp.TestApplication

/** The configurations that must not start. The runner loads no application.yaml: every key is here. */
class StartupChecksTest {

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    class NoProvisionerApp

    private val runner = WebApplicationContextRunner().withUserConfiguration(TestApplication::class.java)

    private val github = arrayOf(
        "spring.security.oauth2.client.registration.github.client-id=id",
        "spring.security.oauth2.client.registration.github.client-secret=secret",
    )
    private val google = arrayOf(
        "spring.security.oauth2.client.registration.google.client-id=id",
        "spring.security.oauth2.client.registration.google.client-secret=secret",
    )

    private fun Throwable.rootMessage(): String = generateSequence(this) { it.cause }.last().message.orEmpty()

    @Test
    fun `starts in production with exactly one client`() {
        runner.withPropertyValues(*github, "spring.profiles.active=production").run { context ->
            context.startupFailure.shouldBeNull()
        }
    }

    @Test
    fun `refuses production without a client`() {
        runner.withPropertyValues("spring.profiles.active=production").run { context ->
            context.startupFailure.shouldNotBeNull().rootMessage() shouldContain "No way to sign in"
        }
    }

    @Test
    fun `refuses production with two clients`() {
        runner.withPropertyValues(*github, *google, "spring.profiles.active=production").run { context ->
            context.startupFailure.shouldNotBeNull().rootMessage() shouldContain "no chooser page"
        }
    }

    @Test
    fun `refuses an app without an AccountProvisioner`() {
        WebApplicationContextRunner()
            .withUserConfiguration(NoProvisionerApp::class.java)
            .withPropertyValues(*github, "spring.profiles.active=production")
            .run { context ->
                context.startupFailure.shouldNotBeNull().rootMessage() shouldContain "AccountProvisioner"
            }
    }

    @Test
    fun `refuses a role entry without provider prefix`() {
        runner.withPropertyValues(*github, "spring.profiles.active=production", "unividuell.auth.roles.super-admin=octocat")
            .run { context ->
                context.startupFailure.shouldNotBeNull().rootMessage() shouldContain "provider:login"
            }
    }
}
