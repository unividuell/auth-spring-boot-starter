package org.unividuell.auth.internal

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Lazy
import org.springframework.context.annotation.Profile
import org.springframework.core.env.Environment
import org.unividuell.auth.AuthProperties

/**
 * Everything of the test login, behind two gates: never under `production`, and only while
 * `unividuell.auth.test-login.enabled` holds. Off means the beans do not exist, so the endpoints
 * answer 404 — a 403 would advertise them.
 */
@Configuration(proxyBeanMethods = false)
@Profile("!production")
@ConditionalOnProperty(name = ["unividuell.auth.test-login.enabled"], havingValue = "true", matchIfMissing = true)
@Import(TestLoginController::class)
class TestLoginConfiguration {

    /** Eager, for its startup check: lazy initialization would defer it to a request. */
    @Bean
    @Lazy(false)
    fun fakeSignInGate(properties: AuthProperties, environment: Environment): FakeSignInGate =
        FakeSignInGate(rawKey = properties.testLogin.key, environment = environment)

    @Bean
    fun testLoginService(properties: AuthProperties, gate: FakeSignInGate, signIn: AccountSignIn): TestLoginService =
        TestLoginService(users = properties.testLogin.users, gate = gate, accounts = signIn)
}
