package org.unividuell.auth

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.springframework.boot.LazyInitializationBeanFactoryPostProcessor
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.context.annotation.Configuration
import org.unividuell.auth.internal.testlogin.TestLoginService
import org.unividuell.auth.testapp.TestApplication

/** The configurations that must not start. The runner loads no application.yaml: every key is here. */
class StartupChecksTest {

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    class NoProvisionerApp

    /** Without a frontend: every app has to name one, so only the frontend checks start from here. */
    private val bare = WebApplicationContextRunner().withUserConfiguration(TestApplication::class.java)

    private val runner = bare.withPropertyValues("unividuell.auth.frontend=spa")

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
            context.getBeanNamesForType(TestLoginService::class.java).toList().shouldBeEmpty()
        }
    }

    @Test
    fun `starts on localhost with no client at all`() {
        runner.run { context -> context.startupFailure.shouldBeNull() }
    }

    @Test
    fun `refuses an unmapped client while the picker owns the page too`() {
        runner.withPropertyValues(*github, *google).run { context ->
            context.startupFailure.shouldNotBeNull().rootMessage() shouldContain "'google'"
        }
    }

    @Test
    fun `refuses production without a client`() {
        runner.withPropertyValues("spring.profiles.active=production").run { context ->
            context.startupFailure.shouldNotBeNull().rootMessage() shouldContain "No way to sign in"
        }
    }

    @Test
    fun `refuses a switched-off test login without a client`() {
        runner.withPropertyValues("unividuell.auth.test-login.enabled=false").run { context ->
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
    fun `refuses a client without an identity mapping`() {
        // Boot's google defaults request openid, which Spring's stock OIDC service would sign in.
        runner.withPropertyValues(*google, "spring.profiles.active=production").run { context ->
            val message = context.startupFailure.shouldNotBeNull().rootMessage()
            message shouldContain "'google'"
            message shouldContain "0.1.0 maps only 'github'"
        }
    }

    @Test
    fun `refuses a client that requests openid`() {
        runner.withPropertyValues(
            *github,
            "spring.security.oauth2.client.registration.github.scope=openid,read:user",
            "spring.profiles.active=production",
        ).run { context ->
            val message = context.startupFailure.shouldNotBeNull().rootMessage()
            message shouldContain "'github'"
            message shouldContain "openid"
        }
    }

    @Test
    fun `refuses staging without a key`() {
        runner.withPropertyValues("spring.profiles.active=staging").run { context ->
            context.startupFailure.shouldNotBeNull().rootMessage() shouldContain "unividuell.auth.test-login.key"
        }
    }

    @Test
    fun `starts on staging with a key and no client`() {
        runner.withPropertyValues("spring.profiles.active=staging", "unividuell.auth.test-login.key=open-sesame")
            .run { context -> context.startupFailure.shouldBeNull() }
    }

    @Test
    fun `refuses a key next to a client`() {
        // /oauth2/authorization/github would be a second door, past the lock.
        runner.withPropertyValues(*github, "spring.profiles.active=staging", "unividuell.auth.test-login.key=open-sesame")
            .run { context ->
                val message = context.startupFailure.shouldNotBeNull().rootMessage()
                message shouldContain "unividuell.auth.test-login.key"
                message shouldContain "spring.security.oauth2.client.registration"
            }
    }

    @Test
    fun `refuses an app without an AccountProvisioner`() {
        WebApplicationContextRunner()
            .withUserConfiguration(NoProvisionerApp::class.java)
            .withPropertyValues(*github, "spring.profiles.active=production", "unividuell.auth.frontend=spa")
            .run { context ->
                context.startupFailure.shouldNotBeNull().rootMessage() shouldContain "AccountProvisioner"
            }
    }

    @Test
    fun `refuses under lazy initialization too`() {
        // spring.main.lazy-initialization would defer each check to the first request needing its bean.
        val lazy = { runner: WebApplicationContextRunner ->
            runner.withInitializer { it.addBeanFactoryPostProcessor(LazyInitializationBeanFactoryPostProcessor()) }
        }
        val refused = mapOf(
            "unmapped client" to arrayOf(*google, "spring.profiles.active=production"),
            "key next to a client" to arrayOf(*github, "spring.profiles.active=staging", "unividuell.auth.test-login.key=k"),
            "staging without a key" to arrayOf("spring.profiles.active=staging"),
            "role entry without prefix" to
                arrayOf(*github, "spring.profiles.active=production", "unividuell.auth.roles.super-admin=octocat"),
            "login page for spa" to arrayOf(*github, "spring.profiles.active=production", "unividuell.auth.login-page=/login"),
        )

        refused.forEach { (case, properties) ->
            lazy(runner).withPropertyValues(*properties).run { context ->
                withClue(case) { context.startupFailure.shouldNotBeNull() }
            }
        }
        lazy(bare).withPropertyValues(*github, "spring.profiles.active=production").run { context ->
            withClue("no frontend") { context.startupFailure.shouldNotBeNull() }
        }
        lazy(WebApplicationContextRunner().withUserConfiguration(NoProvisionerApp::class.java))
            .withPropertyValues(*github, "spring.profiles.active=production", "unividuell.auth.frontend=spa")
            .run { context -> withClue("no AccountProvisioner") { context.startupFailure.shouldNotBeNull() } }
    }

    @Test
    fun `refuses a role entry without provider prefix`() {
        runner.withPropertyValues(*github, "spring.profiles.active=production", "unividuell.auth.roles.super-admin=octocat")
            .run { context ->
                context.startupFailure.shouldNotBeNull().rootMessage() shouldContain "provider:login"
            }
    }

    @Test
    fun `refuses an app that does not name its frontend`() {
        bare.withPropertyValues(*github, "spring.profiles.active=production").run { context ->
            context.startupFailure.shouldNotBeNull().rootMessage() shouldContain "unividuell.auth.frontend is not set"
        }
    }

    @Test
    fun `refuses a server-rendered app without a login page`() {
        for (missing in listOf(emptyArray(), arrayOf("unividuell.auth.login-page= "))) {
            bare.withPropertyValues("unividuell.auth.frontend=server-rendered", *missing).run { context ->
                withClue(missing.toList()) {
                    context.startupFailure.shouldNotBeNull().rootMessage() shouldContain "login-page is required"
                }
            }
        }
    }

    @Test
    fun `refuses a login page for a single-page app`() {
        runner.withPropertyValues("unividuell.auth.login-page=/login").run { context ->
            context.startupFailure.shouldNotBeNull().rootMessage() shouldContain "routes its own login page"
        }
    }

    @Test
    fun `refuses a login page that is not a page of the app`() {
        // Under /login/ and /oauth2/ a logout would start the sign-in again and sign straight back in.
        // The page is opened to anonymous requests as a path pattern: a wildcard would open the whole app.
        // Dot segments and percent-encoding hide a path under /login/ until the browser normalizes it.
        val notAppPages = listOf(
            "login", "//evil.example", "/\\evil.example", "/login/start", "/login/test/as",
            "/oauth2/authorization/github", "/welcome?next=/", "/welcome#top",
            "/**", "/*", "/{page}", "/{*rest}",
            "/./login/start", "/a/../login/start", "/log%69n/start", "/a//b",
        )
        assertSoftly {
            for (page in notAppPages) {
                bare.withPropertyValues("unividuell.auth.frontend=server-rendered", "unividuell.auth.login-page=$page")
                    .run { context ->
                        withClue(page) {
                            context.startupFailure?.rootMessage().orEmpty() shouldContain "must be a page of the app"
                        }
                    }
            }
        }
    }

    @Test
    fun `starts a server-rendered app with a page of its own`() {
        for (page in listOf("/login", "/welcome", "/sign-in", "/a/b_c.d~e")) {
            bare.withPropertyValues("unividuell.auth.frontend=server-rendered", "unividuell.auth.login-page=$page")
                .run { context -> withClue(page) { context.startupFailure.shouldBeNull() } }
        }
    }
}
