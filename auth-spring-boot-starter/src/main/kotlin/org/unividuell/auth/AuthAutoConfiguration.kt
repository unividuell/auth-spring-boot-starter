package org.unividuell.auth

import jakarta.servlet.DispatcherType
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration
import org.springframework.boot.security.oauth2.client.autoconfigure.servlet.OAuth2ClientWebSecurityAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Lazy
import org.springframework.http.HttpStatus
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler
import org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.security.web.csrf.CsrfFilter
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler
import org.springframework.security.web.util.matcher.DispatcherTypeRequestMatcher
import org.unividuell.auth.internal.AccountSignIn
import org.unividuell.auth.internal.CsrfCookieFilter
import org.unividuell.auth.internal.FrontendSetting
import org.unividuell.auth.internal.LoginController
import org.unividuell.auth.internal.provider.ProviderFailureHandler
import org.unividuell.auth.internal.provider.ProviderUserService
import org.unividuell.auth.internal.testlogin.TestLoginConfiguration

/**
 * Runs before Boot's own security auto-configurations, actuator's included, so that its default
 * chain — not one of Boot's form-login ones — is what an app without a chain of its own gets.
 */
@AutoConfiguration(
    before = [
        ServletWebSecurityAutoConfiguration::class,
        OAuth2ClientWebSecurityAutoConfiguration::class,
        ManagementWebSecurityAutoConfiguration::class,
    ],
)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(AuthProperties::class)
@Import(LoginController::class, TestLoginConfiguration::class)
class AuthAutoConfiguration {

    /** Eager, for its startup checks: lazy initialization would defer them to a request. */
    @Bean
    @Lazy(false)
    fun roleAllowlist(properties: AuthProperties): RoleAllowlist = RoleAllowlist(properties.roles)

    /** Eager, for its startup checks: lazy initialization would defer them to a request. */
    @Bean
    @Lazy(false)
    fun frontendSetting(properties: AuthProperties): FrontendSetting =
        FrontendSetting(frontend = properties.frontend, loginPage = properties.loginPage)

    /** Eager: without an [AccountProvisioner] the start must fail, not the first sign-in. */
    @Bean
    @Lazy(false)
    fun accountSignIn(provisioner: AccountProvisioner, allowlist: RoleAllowlist): AccountSignIn =
        AccountSignIn(provisioner = provisioner, allowlist = allowlist)

    @Bean
    fun providerUserService(signIn: AccountSignIn): ProviderUserService = ProviderUserService(signIn = signIn)

    /**
     * Sign-in, CSRF and logout, shaped by the app's frontend: the SPA contract, or page navigation for
     * a server-rendered app. Spring Security applies it to every HttpSecurity before the app's own
     * configuration, so these matchers precede the app's. Never `anyRequest` here: Spring rejects
     * any matcher added after it, and the app's rules still have to follow.
     */
    @Bean
    fun authHttpSecurityCustomizer(
        properties: AuthProperties,
        frontend: FrontendSetting,
        providerUserService: ProviderUserService,
        clients: ObjectProvider<ClientRegistrationRepository>,
    ): Customizer<HttpSecurity> = Customizer { http ->
        // Set exactly for a server-rendered app.
        val appLoginPage = frontend.loginPage
        http {
            authorizeHttpRequests {
                authorize(pattern = "/login/**", access = permitAll)
                authorize(pattern = "/oauth2/**", access = permitAll)
                if (appLoginPage != null) authorize(pattern = appLoginPage, access = permitAll)
                // A container re-dispatches every sendError to /error; else an anonymous 400 or 404 becomes 401.
                authorize(matches = DispatcherTypeRequestMatcher(DispatcherType.ERROR), access = permitAll)
            }
            // oauth2Login cannot start without a client; no client means no provider door at all.
            if (clients.ifAvailable != null) {
                oauth2Login {
                    loginPage = "/login/start"
                    authenticationFailureHandler = ProviderFailureHandler()
                    userInfoEndpoint {
                        userService = providerUserService
                        oidcUserService = ProviderUserService.oidcRefusal
                    }
                }
            }
            exceptionHandling {
                authenticationEntryPoint = when (appLoginPage) {
                    null -> HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)
                    else -> LoginUrlAuthenticationEntryPoint(appLoginPage)
                }
            }
            requestCache {
                requestCache = frontend.requestCache
            }
            csrf {
                csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse()
                // The plain handler keeps cookie and header equal; the default XOR one breaks SPAs.
                csrfTokenRequestHandler = CsrfTokenRequestAttributeHandler()
            }
            addFilterAfter<CsrfFilter>(CsrfCookieFilter(properties.csrfCookie.excludedPaths))
            logout {
                logoutUrl = "/logout"
                logoutSuccessHandler = when (appLoginPage) {
                    null -> HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)
                    else -> SimpleUrlLogoutSuccessHandler().apply { setDefaultTargetUrl(appLoginPage) }
                }
            }
        }
    }

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain::class)
    fun authDefaultSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http {
            authorizeHttpRequests {
                authorize(matches = anyRequest, access = authenticated)
            }
        }
        return http.build()
    }
}
