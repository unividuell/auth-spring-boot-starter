# auth-spring-boot-starter

Sign-in for Spring Boot apps with a same-origin SPA: real OAuth2 providers in production, a
test-user picker everywhere else — behind a key wherever a profile is active.

## Use it

```xml
<dependency>
  <groupId>org.unividuell</groupId>
  <artifactId>auth-spring-boot-starter</artifactId>
  <version>0.1.0</version>
</dependency>
```

Provide the one bean the starter needs. Both doors, the provider and the test login, end here:

```kotlin
@Bean
fun accountProvisioner(accounts: AccountRepository) = AccountProvisioner { identity, roles ->
    // INSERT … ON CONFLICT (provider, subject) DO UPDATE … RETURNING id
    accounts.upsert(identity = identity, roles = roles)
}
```

Read the principal in controllers with `@AuthenticationPrincipal me: AuthPrincipal` (`me.id`,
`me.provider`, `me.login`, `me.roles`).

Add your own rules as usual. The starter's are applied first; end yours with `anyRequest`:

```kotlin
@Bean
fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
    http {
        authorizeHttpRequests {
            authorize(pattern = "/actuator/health", access = permitAll)
            authorize(matches = anyRequest, access = authenticated)
        }
    }
    return http.build()
}
```

Without a chain of your own, every request needs a signed-in user.

## Configuration

```yaml
unividuell:
  auth:
    roles:
      super-admin: ${SUPER_ADMINS:}     # provider:login, comma-separated → ROLE_SUPER_ADMIN
    test-login:
      enabled: true                     # never active under the production profile
      key: ${FAKE_SIGN_IN_KEY:}         # required as soon as any profile is active
      users: …                          # default: twelve Futurama characters
    csrf-cookie:
      excluded-paths: /api/preview/**   # responses that must stay cookie-free
spring.security.oauth2.client.registration.github:   # production only
  client-id: …
  client-secret: ${GITHUB_CLIENT_SECRET}
```

| | localhost | staging | production |
|---|---|---|---|
| `GET /login` | picker | key, then picker | redirect to the provider |
| OAuth2 client | not needed | none | required |

## The SPA contract

- An unauthenticated request gets **401**, never a redirect. The SPA sends the browser to `/login`
  (with `?redirect=/path` to come back there after a test login).
- Echo the `XSRF-TOKEN` cookie as the `X-XSRF-TOKEN` header on every mutating request.
- `POST /logout` answers **204**. After signing in, the browser lands on `/`.

## Refuses to start when

- there is no OAuth2 client and no active test login (no way in);
- there are several clients and no test login (the chooser page does not exist yet);
- the test login is on, its key is empty and any profile is active;
- a role entry lacks its `provider:` prefix;
- the app provides no `AccountProvisioner`.

## Rules

- `AuthPrincipal` is a snapshot taken at sign-in and serialized into the session. Only configured
  roles belong in it. Read anything granted at runtime live from your own rows.
- `org.unividuell.auth.internal` is not API. Do not component-scan `org.unividuell.auth`: the
  auto-configuration registers everything under its conditions; a scan would pick up the
  test-login controller outside them.

## Releasing

Push a tag `vX.Y.Z` on `main`; the release workflow tests, signs and publishes that version to
Maven Central. The pom itself stays on the next `-SNAPSHOT`.
