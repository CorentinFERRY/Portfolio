# 0004 - GitHub client design

Date: 2026-10-09
Review date: 2026-11-17
Status: Accepted

## Context
We need a GitHub client to list public repositories for a single owner via the GitHub REST API v3. It must respect strict security and testing rules (no real token calls, no leaking secrets, follow pagination with safeguards, map errors precisely).

## Decisions
- Interface `GithubApiClient` with no-arg `listPublicRepositories()`; implementation reads owner/token/base URL from validated configuration.
- Use Spring `RestClient` with `SimpleClientHttpRequestFactory`; set timeouts from properties and disable redirect following (`setInstanceFollowRedirects(false)`).
- Send headers: `Authorization: Bearer <token>` (only if token present), `User-Agent`, `X-GitHub-Api-Version: 2022-11-28`.
- Query defaults: `type=owner`, `sort=pushed`, `direction=desc`, `per_page=100`, `page=1`.
- Filter: `private` is mandatory and must be explicitly `false`; missing or wrong type -> `GithubUnexpectedResponseException` (fail closed). When `private` is `false`, keep the repo only if `visibility` is absent or `null`, or exactly `public` (case-sensitive). If `visibility` is present, non-null and non-textual -> `GithubUnexpectedResponseException`; any other visibility value is dropped.
- Pagination: parse `Link` header for `rel="next"`. Enforce same-origin (scheme/host/port, with defaults for http/https). Cap total pages at 10. If next exists after last allowed page -> `GithubPaginationViolationException`.
- Error mapping: 401 -> `GithubAuthenticationFailureException`, 403 -> `GithubRateLimitExceededException` if `x-ratelimit-remaining` == "0" or `Retry-After` present, else `GithubAccessDeniedException`, 404 -> `GithubOwnerNotFoundException`, 429 -> `GithubRateLimitExceededException`, 5xx -> `GithubUnavailableException`, 3xx -> `GithubUnexpectedResponseException`, other client errors -> `GithubUnexpectedResponseException`.
- Rate limit info: include `x-ratelimit-reset` epoch seconds in exception when available.
- Secrets: token redacted from `toString()`, never logged, never in exception messages.
- Tolerance: ignore unknown fields, accept nulls for optional fields, treat missing `topics` as empty list; wrong types/null where boolean/number/string required -> `GithubUnexpectedResponseException`, non-array root -> `GithubUnexpectedResponseException`.

## Verification notes (2026-11-17)

- GitHub documentation pages: non vérifié. No external documentation page is asserted as verified for this decision.
- WireMock choice: `org.wiremock:wiremock-standalone:3.13.2` is test-scoped (`backend/pom.xml:39-44`) and is used as the local HTTP server in the client tests (`backend/src/test/java/io/github/corentinferry/github/client/GithubApiClientTest.java:31-35`, `backend/src/test/java/io/github/corentinferry/github/client/GithubApiClientPaginationTest.java:29-33`, `backend/src/test/java/io/github/corentinferry/github/client/GithubApiClientOpenApiFixtureTest.java:39-43`). This simulates the GitHub API as required by `AGENTS.md:120`; the tests use fake credentials (`GithubApiClientTest.java:35`) and verify the Authorization header (`GithubApiClientDebugLoggingTest.java:96-104`).
- Error and pagination behaviour is proven by tests that need no real GitHub API: headers and filtering (`GithubApiClientTest.java:45-57`), pagination and origin rejection (`GithubApiClientPaginationTest.java:55-77`), error mapping (`GithubApiClientErrorTest.java:49-58`).
- Fixture provenance: the official description was downloaded on 2026-11-17 from `https://raw.githubusercontent.com/github/rest-api-description/main/descriptions/api.github.com/api.github.com.yaml`; endpoint `GET /users/{username}/repos`, 200 response example `minimal-repository-items` (test header `backend/src/test/java/io/github/corentinferry/github/client/GithubApiClientOpenApiFixtureTest.java:23-26`). The full dotted source path `components.examples.minimal-repository-items.value` is non vérifié from tracked sources. Source YAML SHA-256 `1852a9284fbd7b2d7eda16b96fac8115255b9ea84805d8bafffd227010270fd4` and `info.version` 1.1.4 are recorded in that test header (`GithubApiClientOpenApiFixtureTest.java:28-29`); the client API version is 2022-11-28 (`GithubApiClientOpenApiFixtureTest.java:29`).
- Fixture file: `backend/src/test/resources/github/openapi-user-repos-example.json`, loaded from the classpath by `GithubApiClientOpenApiFixtureTest.java:77-84`; SHA-256 `f9b4508f02a388fdf6bd7da37443f1728c213788fe706643fcf6edb12373f09e` recorded at `GithubApiClientOpenApiFixtureTest.java:32` and checked locally on 2026-11-17.
- License: the repository LICENSE file is non vérifié (`GithubApiClientOpenApiFixtureTest.java:31`). A 2026-11-17 GET to `https://raw.githubusercontent.com/github/rest-api-description/main/LICENSE` returned HTTP 404, 14 bytes (`404: Not Found`); no license text was stored in the repository. The declaration recorded in the downloaded description is not treated as a verified repository license (`GithubApiClientOpenApiFixtureTest.java:30-31`).
