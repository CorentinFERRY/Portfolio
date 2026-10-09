# 0004 - GitHub client design

Date: 2026-10-09
Status: Accepted

## Context
We need a GitHub client to list public repositories for a single owner via the GitHub REST API v3. It must respect strict security and testing rules (no real token calls, no leaking secrets, follow pagination with safeguards, map errors precisely).

## Decisions
- Interface `GithubApiClient` with no-arg `listPublicRepositories()`; implementation reads owner/token/base URL from validated configuration.
- Use Spring `RestClient` with `SimpleClientHttpRequestFactory`; set timeouts from properties and disable redirect following (`setInstanceFollowRedirects(false)`).
- Send headers: `Authorization: Bearer <token>` (only if token present), `User-Agent`, `X-GitHub-Api-Version: 2022-11-28`.
- Query defaults: `type=owner`, `sort=pushed`, `direction=desc`, `per_page=100`, `page=1`.
- Filter: keep only repos where `private` is explicitly `false`. If `private` missing/wrong type -> `GithubUnexpectedResponseException` (fail closed).
- Pagination: parse `Link` header for `rel="next"`. Enforce same-origin (scheme/host/port, with defaults for http/https). Cap total pages at 10. If next exists after last allowed page -> `GithubPaginationViolationException`.
- Error mapping: 401 -> `GithubAuthenticationFailureException`, 403 -> `GithubRateLimitExceededException` if `x-ratelimit-remaining` == "0" or `Retry-After` present, else `GithubAccessDeniedException`, 404 -> `GithubOwnerNotFoundException`, 429 -> `GithubRateLimitExceededException`, 5xx -> `GithubUnavailableException`, 3xx -> `GithubUnexpectedResponseException`, other client errors -> `GithubUnexpectedResponseException`.
- Rate limit info: include `x-ratelimit-reset` epoch seconds in exception when available.
- Secrets: token redacted from `toString()`, never logged, never in exception messages.
- Tolerance: ignore unknown fields, accept nulls for optional fields, treat missing `topics` as empty list; wrong types/null where boolean/number/string required -> `GithubUnexpectedResponseException`, non-array root -> `GithubUnexpectedResponseException`.
