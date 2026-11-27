# 0010 - Security hardening

Date: 2026-11-27
Status: Accepted

## Context

Slice 8 hardens the API and the document served by Vite before authentication
arrives in slice 9. The data is public, but section 9 of `AGENTS.md` requires
security headers, a CSP, an explicit CORS allow list, deny by default and
error responses without internal details. The API only returns JSON; the
document is the Vue application, served by Vite in dev and preview, with `/api`
proxied to the backend.

Each finding below is classified:

- `OBSERVE`: executed by the agent with a real output recorded in
  `docs/slice8-requirements.md`.
- `DEDUIT`: inferred from the bytecode or the source of a dependency, not
  executed.
- `MANUAL`: checked by the owner by hand, not replayed by the agent.
- `LIMITE`: a known gap, accepted for now.
- `POINT OUVERT`: a known problem not handled in this slice.

## Decision

### API security headers

The API sets its headers in `SecurityConfig` through Spring Security.

- The CSP is a private Java constant, `API_CONTENT_SECURITY_POLICY`, not a
  property. A security policy is not environment configuration, and an
  environment variable must not be able to weaken it.
- The API CSP is
  `default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'`.
  Each directive:
  - `default-src 'none'`: the fallback for every fetch directive. A JSON
    response needs no script, style, font, image or connection, so a browser
    that renders an API response as a page loads nothing. This also covers
    fonts, so the API has no `font-src`: it would only be less strict.
  - `frame-ancestors 'none'`: not covered by `default-src`. No page may frame
    an API response, which closes clickjacking on any response rendered as
    HTML. It is the CSP equivalent of `X-Frame-Options: DENY`, which is kept
    for older browsers.
  - `base-uri 'none'`: not covered by `default-src`. A `<base>` element in a
    response rendered as HTML cannot redirect relative URLs.
  - `form-action 'none'`: not covered by `default-src`. A response rendered as
    HTML cannot submit a form anywhere.
- `Referrer-Policy: no-referrer`: no URL of the API leaks to another site.
- `X-Content-Type-Options: nosniff` and `X-Frame-Options: DENY` are set
  explicitly although they are Spring Security defaults, so that removing them
  is a visible change in the configuration.
- `X-XSS-Protection: 0` (`MANUAL`: seen by the owner with `curl` through the
  dev proxy; `DEDUIT` from the jar: the default constructor of
  `XXssProtectionHeaderWriter` in spring-security-web 7.1.1 uses
  `HeaderValue.DISABLED`). It is the Spring Security default and it turns off
  the legacy browser XSS filter, which could itself be abused; the CSP is the
  protection. Not configured explicitly and not tested.
- HSTS is left to the Spring Security default and is not tested. Spring only
  sends it on a secure request: behind a TLS reverse proxy (slice 13), Spring
  will see plain HTTP and stop sending it unless the forwarded headers are
  trusted. To revisit in slice 13 (see "HTTPS in production" in the open
  points).

### Error dispatch

- The `ERROR` dispatcher type is permitted. The container forwards a failed
  request to `/error`; denying that forward turned every 500 into an empty
  403 (`OBSERVE`, red before the fix). A direct `GET /error` is a normal
  request and stays 403.
- The default Spring Boot error format is kept, without message or stack
  trace, and no generic exception handler is added.
- Lesson: `server.error.include-*` is deprecated at error level since Boot
  4.0.0 and ignored; the active name is `spring.web.error.include-*`. A
  mutation written with the old name survived (MA6); the same mutation with
  the active name was killed (MA6b) (`OBSERVE`).
- `LIMITE`: no mutation removes the headers on error responses only. Error
  coverage comes from the mutations that remove a header everywhere, which
  also turn the 403, 500, 502 and 503 header tests red.
- `LIMITE`: 401 and 404 are not reachable today (no authentication,
  `Http403ForbiddenEntryPoint`, and `denyAll` before route resolution). They
  are tested in slice 9.

### CORS

- Allowed origins come from `PORTFOLIO_CORS_ALLOWED_ORIGINS`, bound only by
  the placeholder in `application.yml` (`OBSERVE`: relaxed binding alone does
  not bind the variable, mutation MB6).
- Origins are HTTPS only, except `http://localhost` and `http://127.0.0.1`,
  which require a port. Wildcards, paths, user info, query, fragment, `::1`
  and a trailing slash are rejected at startup with a fixed message that never
  echoes the value.
- Only `GET` is allowed, and `setAllowCredentials(false)` is explicit: the
  authentication cookie of slice 9 must revisit it on purpose.
- The configuration is registered on `/api/**` even when the list is empty, so
  every cross-origin request gets an explicit 403 by default.
- The source is passed to `.cors(...)` directly, not published as a bean.
  `DEDUIT` from the jar: Spring Security applies `cors(withDefaults())` as soon
  as a bean of type `UrlBasedCorsConfigurationSource` exists, so with a bean,
  removing `.cors(...)` would not remove CORS.
- The tests check the body `Invalid CORS request`, written by
  `DefaultCorsProcessor.rejectRequest` in Spring. It is what tells a CORS
  rejection apart from the empty deny-by-default 403. Accepted trade-off: if
  Spring changes this text, the tests fail visibly.
- `CorsUtils.isCorsRequest` compares scheme, host and port. MockMvc serves
  `http://localhost:80`, so the tests never use `http://localhost` without a
  port as an origin.
- The Vite dev proxy does not use `changeOrigin` (`OBSERVE`, by reading
  `vite.config.ts`). The proxy keeps `Host: 127.0.0.1:5173`, so a relayed
  request with `Origin: http://127.0.0.1:5173` stays same-origin for Spring.
  If `changeOrigin` were enabled, the `Host` would become the backend's and
  such a request would become a CORS request, rejected unless that origin is
  listed. To revisit in slice 9, when non-GET requests arrive.

### Preflight outside `/api/**` (MB9)

`CorsFilter` ends every preflight request (`OPTIONS` with `Origin` and
`Access-Control-Request-Method`) without calling the rest of the chain, even on
a path that has no CORS configuration. A preflight outside `/api/**` therefore
gets an empty 200, without `Access-Control-Allow-Origin` and with the security
headers, instead of the deny-by-default 403 (`OBSERVE`, characterization test
and mutation MD1).

The behaviour is kept. Without `Access-Control-Allow-Origin` the browser still
blocks the real request, so nothing is allowed. Getting a 403 would need a
custom filter, or a registration on `/**`, which would widen CORS to every
path. The characterization test makes a change in Spring or in the
registration visible.

### `HEAD /api/health` is denied

The permit rule is `requestMatchers(HttpMethod.GET, "/api/health")`, which
matches `GET` exactly. `HEAD` falls to `anyRequest().denyAll()` and gets a 403,
although Spring MVC would answer it with the `GET` handler (`MANUAL`: seen by
the owner through the dev proxy; `OBSERVE`: characterization test and
mutations MD2 and MD2b). The behaviour is kept: no client uses `HEAD`, and allowing a method
must stay an explicit decision.

### Document CSP

The document headers are set by `server.headers` (dev) and `preview.headers`
(preview) in `frontend/vite.config.ts`, built by `documentHeaders(csp)` in
`frontend/security-headers.ts`. They add the CSP, `Referrer-Policy:
no-referrer`, `X-Content-Type-Options: nosniff` and `X-Frame-Options: DENY`.

`DOCUMENT_CSP` is the production policy, served by preview.
`DEV_DOCUMENT_CSP` is an alias, identical today; a future difference, such as
the inline styles injected by HMR, stays reserved to dev.

The document CSP is
`default-src 'self'; script-src 'self'; style-src 'self'; font-src 'self'; img-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'`.
Each directive:

- `default-src 'self'`: the fallback for any fetch directive not listed. The
  application loads everything from its own origin.
- `script-src 'self'`: the build emits external module scripts only. No
  inline script and no `eval`, which is what makes an injected script
  harmless. A test forbids `'unsafe-inline'`, `'unsafe-eval'`, `*` and `data:`
  in the production policy.
- `style-src 'self'`: the build emits CSS files. No inline style is needed
  today; the HMR styles that `tokens.css` will bring in dev are the reserved
  dev difference.
- `font-src 'self'`: fonts are self-hosted only, no external font origin.
- `img-src 'self'`: no image is loaded from another origin today. Showing a
  remote image, such as an avatar, would be a decision that widens this
  directive.
- `connect-src 'self'`: the browser calls only `/api` on its own origin,
  through the proxy, and never the GitHub API. In dev it also covers the HMR
  WebSocket (checked manually by the owner).
- `object-src 'none'`: no plugin content.
- `base-uri 'self'`: a `<base>` element cannot point relative URLs to another
  origin.
- `form-action 'self'`: a form can only submit to the application's own
  origin.
- `POINT OUVERT` on `base-uri` and `form-action`: `'self'` was chosen in
  anticipation of a login form on the same origin (slice 9). Today the page
  has neither `<base>` nor `<form>`, so `'none'` would have the same effect
  and would be stricter. To revisit in slice 9.
- `frame-ancestors 'none'`: no page may frame the application.

The fetch directives equal to the `default-src` fallback are written out so
that a change of `default-src` cannot loosen them silently, and so that the
tests can check each one.

Other points:

- Only one CSP reaches the browser through the dev proxy, the API's (H3,
  `MANUAL`, consistent with the source: Vite mounts the proxy
  before the middlewares that set the headers).
- MC2, removing `preview.headers`, survives and is an equivalent mutation
  today. `DEDUIT` from the Vite source: preview resolves its headers as
  `preview?.headers ?? server.headers`, and the dev policy is identical to the
  production one, so the served behaviour does not change. When
  `DEV_DOCUMENT_CSP` diverges, a missing `preview.headers` would make preview
  serve the dev policy; the tests on the exact preview policy and on forbidden
  sources will then go red, because they read the resolved configuration.
  `preview.headers` stays explicit for that reason, as its comment says.
- Vite 8.4 allows only one dev server per project (`vite:command-lock`). A
  second `vite` for the same project stops at startup, even on another port.
  `vite preview` is not blocked by a dev server.
- `LIMITE`: the headers only apply to Vite. The container server of slice 13
  must copy `DOCUMENT_CSP`.
- `LIMITE` (H6, not observed): the Vite error overlay injects styles into a
  shadow DOM and could trigger a `style-src` violation in dev, only on a
  compilation error.

### Raw HTML sink guard

`no-v-html.spec.ts` now also scans `.js`, `.mjs`, `.jsx` and `.tsx`, and
forbids `document.write`, `setHTMLUnsafe`, `createContextualFragment` and
`srcdoc` (case-insensitive). The existing rule and list are unchanged.

- `LIMITE`: the guard is textual. An alias such as `const d = document;
  d.write()` or a computed access such as `el['inner' + 'HTML']` escapes it.
- `LIMITE`: `Document.parseHTMLUnsafe` and `DOMParser.parseFromString` are not
  forbidden.
- `LIMITE`: no mutation combines a new sink with a new extension.
- No file with the new extensions exists, so the proof that they are scanned
  comes only from the mutations that add one.

### Open points

- `POINT OUVERT`: `base-uri 'self'` and `form-action 'self'` in the document
  CSP, to revisit in slice 9 (see "Document CSP").
- `POINT OUVERT`: `CachingGithubApiClient` makes a blocking call on
  `commonPool`, reachable from the public endpoint. Not handled in slice 8; it
  moves to slice 8b "Resilience", to be done before slice 13.
- `POINT OUVERT`: HTTPS in production (slice 13). No code in slice 8. Slice 13
  must provide:
  - TLS terminated by a reverse proxy in front of the backend and the
    document server;
  - a redirection from HTTP to HTTPS;
  - HSTS, sent either by the proxy, or by Spring through
    `server.forward-headers-strategy`, with `X-Forwarded-*` headers accepted
    only from the proxy, so a client cannot claim a secure request;
  - a check with `curl` after deployment: the redirection, and the HSTS and
    security headers on the HTTPS responses.

  The `Secure` authentication cookie of slice 9 depends on it: a browser only
  sends a `Secure` cookie over HTTPS, so authentication in production requires
  HTTPS.

### Out of scope

`Permissions-Policy`, an HSTS test, a retry button and any styling change.

## Consequences

- Every API response, including 403, 500, 502 and 503, carries the same
  security headers, and errors expose no internal detail.
- CORS is closed by default; opening it is one environment variable with
  validated values.
- The document has a strict CSP in dev and preview. Any external resource,
  inline script or inline style needs a deliberate change of
  `security-headers.ts`, caught by the tests.
- Slice 9 must revisit `allowCredentials`, `changeOrigin`, `base-uri` and
  `form-action` in the document CSP, and 401/404 tests.
  Slice 13 must copy `DOCUMENT_CSP` and provide HTTPS in production, with
  HSTS, on which the `Secure` cookie of slice 9 depends. Slice 8b must
  handle the blocking call on `commonPool`.
- Two characterizations, the empty 200 preflight outside `/api/**` and the 403
  on `HEAD /api/health`, are frozen by tests.
