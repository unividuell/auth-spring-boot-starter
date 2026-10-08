# auth-spring-boot-starter

Sign-in for Spring Boot apps with a same-origin SPA: GitHub sign-in in production, a test-user
picker everywhere else — behind a key wherever a profile is active.

Requires Java 25, Spring Boot 4.1 and the servlet stack (Spring MVC).

## Use it

```xml
<dependency>
  <groupId>org.unividuell</groupId>
  <artifactId>auth-spring-boot-starter</artifactId>
  <version>0.1.0</version>
</dependency>
```

The app commits the lib's file repository (see [Releasing](#releasing)) and points Maven at it:

```xml
<repositories>
  <repository>
    <id>unividuell-local</id>
    <url>file://${project.basedir}/maven-repo</url>
  </repository>
</repositories>
```

Provide the one bean the starter needs. Both doors, the provider and the test login, end here:

```kotlin
@Bean
fun accountProvisioner(accounts: AccountRepository) = AccountProvisioner { identity, roles ->
    // INSERT … ON CONFLICT (provider, subject) DO UPDATE … RETURNING id
    accounts.upsert(identity = identity, roles = roles)
}
```

`identity.email` is GitHub's public profile address, unverified — never key or link accounts on
it.

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

Without a chain of your own, every request needs a signed-in user. Do not narrow yours with
`securityMatcher`: the starter's endpoints and CSRF live in that chain, so requests outside the
matcher lose them.

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

0.1.0 maps GitHub only: any other registration, or one requesting `openid`, refuses to start.
Profile names are taken literally: only `production` switches the test login off; any other, such
as `staging`, needs the key. The starter assumes the root context path.

## The SPA contract

- An unauthenticated request gets **401**, never a redirect. The SPA sends the browser to `/login`
  (with `?redirect=/path` to come back there after a test login).
- Echo the `XSRF-TOKEN` cookie as the `X-XSRF-TOKEN` header on every mutating request.
- `POST /logout` answers **204**. After signing in, the browser lands on `/`.

## Refuses to start when

- there is no OAuth2 client and no active test login (no way in);
- there are several clients and no test login (the chooser page does not exist yet);
- the test login is on, its key is empty and any profile is active;
- the test login has a key and an OAuth2 client is registered (a door past the lock);
- a client's registration id is not `github`, or it requests the `openid` scope — also while the
  test login is on;
- a role entry lacks its `provider:` prefix, or two role keys name the same role (`super-admin`,
  `Super-Admin`);
- the app provides no `AccountProvisioner`.

## Rules

- `AuthPrincipal` is a snapshot taken at sign-in and serialized into the session. Only configured
  roles belong in it. Read anything granted at runtime live from your own rows.
- Requests that stay anonymous create no session. A completed sign-in creates one, and starting a
  provider sign-in (`/oauth2/authorization/{id}`) keeps the authorization request in one. With
  Spring Session JDBC every session is a row. Never inject `HttpSession` or call `getSession()` or
  `getSession(true)` in code that runs for anonymous requests (a `@ModelAttribute`, a filter, an
  interceptor).
- `org.unividuell.auth.internal` is not API. Do not component-scan `org.unividuell.auth`: the
  auto-configuration registers everything under its conditions; a scan would pick up the
  test-login controller outside them.

## Releasing

Each app commits the lib's file repository.

```bash
./mvnw versions:set -DnewVersion=X.Y.Z -DgenerateBackupPoms=false
git commit -am "Release X.Y.Z" && git tag vX.Y.Z
./mvnw -B deploy -DskipTests -Dmaven.install.skip=true -DaltDeploymentRepository=app::file:///absolute/path/to/app/core/maven-repo
```

`-Dmaven.install.skip=true`: without it `deploy` also installs into `~/.m2`, which hides a missing
`<repository>` or an uncommitted `maven-repo/` until CI.

Commit that directory in the app. Then bump the lib to the next `-SNAPSHOT`:

```bash
./mvnw versions:set -DnewVersion=X.Y.(Z+1)-SNAPSHOT -DgenerateBackupPoms=false
git commit -am "Start X.Y.(Z+1)-SNAPSHOT"
```
