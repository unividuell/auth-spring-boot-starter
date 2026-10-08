package org.unividuell.auth

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.web.SecurityFilterChain
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.unividuell.auth.testapp.TestApplication

/** An app with its own rules: the lib's contract must still wrap them. */
@SpringBootTest(classes = [TestApplication::class, AppChainTest.AppChain::class])
@AutoConfigureMockMvc
class AppChainTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val context: ApplicationContext,
) {

    @Configuration(proxyBeanMethods = false)
    class AppChain {

        @Bean
        fun appSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
            http {
                authorizeHttpRequests {
                    authorize(pattern = "/api/public/**", access = permitAll)
                    authorize(matches = anyRequest, access = authenticated)
                }
            }
            return http.build()
        }
    }

    @Test
    fun `the app's chain replaces the lib's default`() {
        context.getBeansOfType(SecurityFilterChain::class.java).keys shouldBe setOf("appSecurityFilterChain")
    }

    @Test
    fun `the app's own rules apply`() {
        mockMvc.get("/api/public/preview").andExpect { status { isOk() } }
    }

    @Test
    fun `the lib's rules come before the app's catch-all`() {
        mockMvc.get("/login/anything").andExpect { status { isNotFound() } }
    }

    @Test
    fun `the SPA contract holds on the app's chain`() {
        val response = mockMvc.get("/api/me").andExpect { status { isUnauthorized() } }.andReturn().response

        response.setCookieValue("XSRF-TOKEN").shouldNotBeNull()
    }
}
