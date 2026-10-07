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
import org.springframework.http.HttpStatus
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.security.web.csrf.CsrfFilter
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler
import org.springframework.security.web.savedrequest.NullRequestCache
import org.springframework.security.web.util.matcher.DispatcherTypeRequestMatcher
import org.unividuell.auth.internal.AccountSignIn
import org.unividuell.auth.internal.CsrfCookieFilter
import org.unividuell.auth.internal.LoginController
import org.unividuell.auth.internal.ProviderFailureHandler
import org.unividuell.auth.internal.ProviderUserService
import org.unividuell.auth.internal.TestLoginConfiguration

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

    @Bean
    fun roleAllowlist(properties: AuthProperties): RoleAllowlist = RoleAllowlist(properties.roles)

    @Bean
    fun accountSignIn(provisioner: AccountProvisioner, allowlist: RoleAllowlist): AccountSignIn =
        AccountSignIn(provisioner = provisioner, allowlist = allowlist)

    @Bean
    fun providerUserService(signIn: AccountSignIn): ProviderUserService = ProviderUserService(signIn = signIn)

    /**
     * The SPA contract. Spring Security applies it to every HttpSecurity before the app's own
     * configuration, so these matchers precede the app's. Never `anyRequest` here: Spring rejects
     * any matcher added after it, and the app's rules still have to follow.
     */
    @Bean
    fun authHttpSecurityCustomizer(
        properties: AuthProperties,
        providerUserService: ProviderUserService,
        clients: ObjectProvider<ClientRegistrationRepository>,
    ): Customizer<HttpSecurity> = Customizer { http ->
        http {
            authorizeHttpRequests {
                authorize(pattern = "/login/**", access = permitAll)
                authorize(pattern = "/oauth2/**", access = permitAll)
                // A container re-dispatches every sendError to /error; else an anonymous 400 or 404 becomes 401.
                authorize(matches = DispatcherTypeRequestMatcher(DispatcherType.ERROR), access = permitAll)
            }
            // oauth2Login cannot start without a client; no client means no provider door at all.
            if (clients.ifAvailable != null) {
                oauth2Login {
                    loginPage = "/login"
                    authenticationFailureHandler = ProviderFailureHandler()
                    userInfoEndpoint {
                        userService = providerUserService
                        oidcUserService = ProviderUserService.oidcRefusal
                    }
                }
            }
            exceptionHandling {
                authenticationEntryPoint = HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)
            }
            // A cached request — the SPA's bootstrap call that got the 401 — would be replayed after
            // sign-in and land the user on raw JSON. Without a cache, sign-in goes to "/".
            requestCache {
                requestCache = NullRequestCache()
            }
            csrf {
                csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse()
                // The plain handler keeps cookie and header equal; the default XOR one breaks SPAs.
                csrfTokenRequestHandler = CsrfTokenRequestAttributeHandler()
            }
            addFilterAfter<CsrfFilter>(CsrfCookieFilter(properties.csrfCookie.excludedPaths))
            logout {
                logoutUrl = "/logout"
                logoutSuccessHandler = HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)
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
