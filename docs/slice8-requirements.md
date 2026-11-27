# Slice 8 requirements traceability

Date: 2026-11-27
Slice: 8, groups A (API headers and error dispatch), B (CORS), C (document
headers and raw HTML sink guard) and D1 (characterizations)

## Rule

Every row names at least one executable test, or states that none exists.
Backend tests are written `Class#method`; frontend tests are written
`file > describe > it`. Statuses:

- `OBSERVE`: executed by the agent with a real output.
- `DEDUIT`: inferred from the bytecode or the source of a dependency, not
  executed.
- `MANUAL`: checked by the owner by hand, not replayed by the agent.
- `LIMITE`: known gap, accepted for now.

Every mutation was played one at a time from a clean tree and an empty
`git diff`, edited with the edit tool, with only the named test classes or
files run, then restored with `git restore --worktree <path>` (or `rm` of the
exact path for a file created by the mutation) and an empty `git diff`
checked. The red sets below are the observed ones. Decisions and their
reasons are in `docs/decisions/0010-security-hardening.md`.

## Counts

Backend, from `cd backend && ./mvnw test` (`Tests run: 254, Failures: 0,
Errors: 0, Skipped: 0`, `BUILD SUCCESS`), executions per class:

| Class | Executions |
| --- | --- |
| `SecurityHeadersTest` | 6 |
| `SecurityHeadersContainerTest` | 2 |
| `ErrorDispatchContainerTest` | 2 |
| `CorsPropertiesTest` | 26 (6 methods, two parameterized with 4 and 18 values) |
| `CorsConfigTest` | 7 |
| `CorsDefaultConfigTest` | 2 |
| `SecurityConfigTest` | 4 (1 added by this slice) |

Frontend, from `cd frontend && npm test` (`Test Files 6 passed (6)`,
`Tests 42 passed (42)`): `vite-security-headers.spec.ts` has 5 `it`,
`no-v-html.spec.ts` has 3 `it` (2 added by this slice), counted by grep.

## Group A - API security headers and error dispatch

API CSP: `default-src 'none'; frame-ancestors 'none'; base-uri 'none';
form-action 'none'`. "Header tests" below means the 8 tests of
`SecurityHeadersTest` (`healthResponseCarriesSecurityHeaders`,
`projectsResponseCarriesSecurityHeaders`,
`forbiddenUnknownRouteCarriesSecurityHeaders`,
`forbiddenPostHealthCarriesSecurityHeaders`,
`githubUnavailableCarriesSecurityHeaders`,
`githubErrorCarriesSecurityHeaders`) and `SecurityHeadersContainerTest`
(`unexpectedFailureCarriesSecurityHeaders`,
`forbiddenRouteCarriesSecurityHeaders`).

| Requirement | Test | Mutation observed red | Status |
| --- | --- | --- | --- |
| Exact API CSP on 200, 403, 500, 502 and 503 | header tests | MA1 (CSP removed), MA2 (`frame-ancestors 'self'`): the 8 tests | OBSERVE |
| `Referrer-Policy: no-referrer` | header tests | MA3 (`referrerPolicy` removed): the 8 tests | OBSERVE |
| `X-Frame-Options: DENY` | header tests | MA4 (`sameOrigin`): the 8 tests, `SAMEORIGIN` instead of `DENY` | OBSERVE |
| `X-Content-Type-Options: nosniff` | header tests | MA5 (`contentTypeOptions(disable)`): the 8 tests | OBSERVE |
| A failure in a real container returns 500, not 403 | `ErrorDispatchContainerTest#nonGithubFailureReturns500WithoutDetails` | MF1 (`ERROR` dispatch no longer permitted): `expected: <500> but was: <403>` | OBSERVE |
| A direct request to `/error` stays 403 | `ErrorDispatchContainerTest#directErrorRequestIsForbidden` | MF2 (`/error` permitted): `expected: <403> but was: <500>` | OBSERVE |
| A 500 exposes no message and no stack trace | `ErrorDispatchContainerTest#nonGithubFailureReturns500WithoutDetails` | MA6b (`spring.web.error.include-message: always`): sentinel in `"message"`; MA7 (`spring.web.error.include-stacktrace: always`): sentinel in `"trace"` | OBSERVE |
| `server.error.include-*` has no effect in Boot 4 | none | MA6 (`server.error.include-message: always`) survived: the property is ignored | OBSERVE |
| Headers present on error responses only | none | no mutation removes headers on errors only; covered by MA1 to MA5 | LIMITE |
| `X-XSS-Protection: 0` | none | none | MANUAL (owner `curl` through the dev proxy), DEDUIT (`XXssProtectionHeaderWriter` defaults to `DISABLED` in spring-security-web 7.1.1) |
| HSTS | none | none | LIMITE (Spring default, not tested) |

## Group B - CORS

| Requirement | Test | Mutation observed red | Status |
| --- | --- | --- | --- |
| HTTPS origins and loopback HTTP origins with a port are accepted | `CorsPropertiesTest#acceptsHttpsOriginsAndLoopbackHttpOriginsWithPort` (4 values) | MB13 (`127.0.0.1` removed from the loopback hosts): value `[4]` `http://127.0.0.1:4173` only, in error with `IllegalStateException: portfolio.cors.allowed-origins contains an invalid origin`; 25 other executions green | OBSERVE |
| Other origins stop startup with a fixed message that does not echo them | `CorsPropertiesTest#rejectsOriginsOutsideThePolicyWithoutEchoingThem` (18 values) | MB5 (`http` accepted for any host): `localhost.evil...`, `127.0.0.1.evil...`, `http://[::1]:5173`; MB11 (host check removed): `https://*.portfolio.example`, `https://portfolio.example:*` (the `*` value stays green: it is also rejected by the path and scheme rules) | OBSERVE |
| A missing list becomes an empty list | `CorsPropertiesTest#missingListBecomesEmpty` | MB12 (`null` no longer replaced by `List.of()`): `NullPointerException` | OBSERVE |
| The variable is split on `,` and trimmed | `CorsPropertiesTest#commaSeparatedEnvironmentVariableBindsTrimmedOrigins` | MB6 (placeholder renamed) | OBSERVE |
| An invalid origin in the variable stops startup without echoing it | `CorsPropertiesTest#invalidOriginInEnvironmentVariableStopsStartupWithoutEchoingIt` | MB6 | OBSERVE |
| The yml default is an empty list | `CorsPropertiesTest#applicationYmlDefaultsToAnEmptyListWithoutTheVariable` | MB10 (default `https://evil.example`): `expected: <[]> but was: <[https://evil.example]>` | OBSERVE |
| A listed origin GET echoes that origin, never `*`, without credentials | `CorsConfigTest#allowedOriginGetEchoesThatOriginWithoutCredentials` | MB1 (`.cors` removed), MB3 (`allowCredentials(true)`), MB8 (`*` allowed), MB9 (registered on `/api/health`) | OBSERVE |
| A listed origin preflight for GET allows GET only | `CorsConfigTest#allowedOriginPreflightForGetAllowsGetOnly` | MB1, MB2 (`setAllowedMethods` removed): `expected:<GET> but was:<GET,HEAD>`, MB3, MB7 (`CorsFilter` before `HeaderWriterFilter`), MB8, MB9 | OBSERVE |
| An unlisted origin GET is rejected by CORS, with the security headers | `CorsConfigTest#unlistedOriginGetIsRejectedByCors` | MB1, MB7, MB8, MB9 | OBSERVE |
| A preflight for POST is rejected by CORS | `CorsConfigTest#allowedOriginPreflightForPostIsRejectedByCors` | MB1, MB7, MB9 | OBSERVE |
| An unlisted origin preflight for GET is rejected by CORS | `CorsConfigTest#unlistedOriginPreflightForGetIsRejectedByCors` | MB1, MB7, MB8, MB9 | OBSERVE |
| An empty list still rejects every cross-origin request with a 403 and the headers, in Tomcat | `CorsDefaultConfigTest#crossOriginGetIsRejectedWithSecurityHeaders` | MB1, MB4 (registered only if the list is not empty): `expected: <403> but was: <200>`, MB7, MB8, MB9 | OBSERVE |
| A request without `Origin` is not treated as CORS | `CorsConfigTest#requestWithoutOriginIsNotTreatedAsCors`, `CorsDefaultConfigTest#requestWithoutOriginIsNotTreatedAsCors` | none: regression guards, red under no mutation | OBSERVE |
| `allowCredentials(false)` is explicit | none | removing it is equivalent (`false` and `null` emit nothing), not played | DEDUIT |

## Group C - Document headers and raw HTML sink guard

Document CSP: `default-src 'self'; script-src 'self'; style-src 'self';
font-src 'self'; img-src 'self'; connect-src 'self'; object-src 'none';
base-uri 'self'; form-action 'self'; frame-ancestors 'none'`.

| Requirement | Test | Mutation observed red | Status |
| --- | --- | --- | --- |
| Preview serves exactly the production CSP | `vite-security-headers.spec.ts > vite document security headers > serves the production document csp from vite preview` | MC1 (external `font-src`), MC4 (`'unsafe-inline'` in `script-src`) | OBSERVE |
| Dev serves exactly the document CSP | `vite-security-headers.spec.ts > vite document security headers > serves the dev document csp from vite dev server` | MC1, MC3 (`server.headers` removed), MC4 | OBSERVE |
| Self-hosted fonts only, dev and preview | `vite-security-headers.spec.ts > vite document security headers > allows only self-hosted fonts` | MC1, MC3 | OBSERVE |
| No inline or eval script, no `*` and no `data:` in production | `vite-security-headers.spec.ts > vite document security headers > forbids inline and eval scripts in the production csp` | MC4 | OBSERVE |
| Referrer, nosniff and DENY from both servers | `vite-security-headers.spec.ts > vite document security headers > sends referrer, nosniff and frame options from both servers` | MC3, MC5 (dev assertion), MC5b (preview assertion) | OBSERVE |
| `preview.headers` is explicit | none possible today | MC2 (`preview.headers` removed) survived: equivalent while the dev and production policies are identical | DEDUIT |
| Raw HTML sinks are rejected in `.js`, `.mjs`, `.jsx` and `.tsx` | `no-v-html.spec.ts > frontend source guard > rejects raw html sinks in .js, .mjs, .jsx and .tsx files` | MG5 to MG8 (one file per extension with `innerHTML`) | OBSERVE |
| `document.write`, `setHTMLUnsafe`, `createContextualFragment` and `srcdoc` are rejected | `no-v-html.spec.ts > frontend source guard > rejects document.write, setHTMLUnsafe, createContextualFragment and srcdoc` | MG1 to MG4 (one sink each) | OBSERVE |
| The existing `.vue` and `.ts` guard is unchanged | `no-v-html.spec.ts > frontend source guard > rejects raw html sinks in .vue and .ts files` | stayed green under MG1 to MG8 | OBSERVE |
| Preview and dev serve the four headers | smoke test: `GET /` on preview 4173, `GET /` and `GET /src/main.ts` on dev 5173 | none | OBSERVE |
| Only one CSP through the dev proxy, the API's | none | none | MANUAL (owner) |
| No CSP violation in dev and preview, HMR working | none | none | MANUAL (owner, browser console) |
| Aliased or computed sinks, `parseHTMLUnsafe`, `DOMParser` | none | none | LIMITE |

## Group D1 - Characterizations

| Requirement | Test | Mutation observed red | Status |
| --- | --- | --- | --- |
| A preflight outside `/api/**` gets an empty 200 with the security headers and without CORS headers | `CorsConfigTest#preflightOutsideApiPathsIsAnsweredEmptyWithoutCorsHeaders` | MD1 (registered on `/**`): this test only, `Response should not contain header 'Access-Control-Allow-Origin'`; `CorsConfigTest` 6 others and `CorsDefaultConfigTest` 2 green | OBSERVE |
| `HEAD /api/health` is denied: only GET is permitted | `SecurityConfigTest#headHealthIsRejectedBecauseOnlyGetIsPermitted` | MD2 (`HEAD` permitted): this test only, `Status expected:<403> but was:<200>`; MD2b (method removed from the matcher): this test (`expected:<403> but was:<200>`), `SecurityConfigTest#postHealthWithoutAuthenticationIsRejected` and `SecurityHeadersTest#forbiddenPostHealthCarriesSecurityHeaders` (`Status expected:<403> but was:<405>`); `HealthEndpointTest` and `CorsDefaultConfigTest` green | OBSERVE |
