package org.unividuell.auth.testapp

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Bean
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import org.unividuell.auth.AccountProvisioner
import org.unividuell.auth.AuthPrincipal
import org.unividuell.auth.ExternalIdentity
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/** A consumer as small as one can be. It scans only its own package, never the lib's. */
@SpringBootApplication
class TestApplication {

    @Bean
    fun accounts(): InMemoryAccounts = InMemoryAccounts()
}

class InMemoryAccounts : AccountProvisioner {

    val provisioned: MutableList<ExternalIdentity> = CopyOnWriteArrayList()
    private val ids = ConcurrentHashMap<String, UUID>()

    override fun provision(identity: ExternalIdentity, roles: Set<String>): UUID {
        provisioned += identity
        return ids.computeIfAbsent("${identity.provider}:${identity.subject}") { UUID.randomUUID() }
    }
}

@RestController
class TestApiController {

    @GetMapping("/api/me")
    fun me(@AuthenticationPrincipal me: AuthPrincipal): Map<String, Any> =
        mapOf("id" to me.id.toString(), "provider" to me.provider, "login" to me.login, "roles" to me.roles)

    @PostMapping("/api/ping")
    fun ping(): String = "pong"

    @GetMapping("/api/public/preview")
    fun preview(): String = "preview"
}
