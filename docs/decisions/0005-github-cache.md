# 0005 - GitHub client cache

Date: 2026-11-20
Status: Accepted

## Context
The GitHub client can be called repeatedly for the same owner. A synchronous
cache was first tried: a concurrency test on a 429 showed 8 HTTP requests for 8
callers, which is unacceptable under rate limiting. The cache must keep the
call server-side, never serve a stale value after an expired entry fails to
reload, and expose an immutable list.

## Decision
Use Caffeine `AsyncCache` in a `CachingGithubApiClient` decorator around the
default client. Build it with `maximumSize`, `expireAfterWrite`, `ticker` and
`executor`; use the single configured `owner` as the unique key.
`listPublicRepositories()` joins the shared future and returns `List.copyOf(...)`.
A `CompletionException` is unwrapped to its `RuntimeException` cause.

## Alternatives discarded
- Synchronous Caffeine cache: 8 requests for 8 callers on a 429 in
  `CachingGithubApiClientConcurrencyTest#concurrentFailuresTriggerOneRequestAndAllCallersFail`.
- `@Cacheable`: not measured. It would need a constant SpEL key on a
  no-argument method, a custom `CacheManager` to inject the `Ticker`, a less
  verifiable TTL specification, and a dependency on the Spring Cache
  implementation.
- Manual single-flight: a hand-written map of futures and locks would duplicate
  `AsyncCache` and add concurrency state to maintain.

## Consequences
- No stale-on-error: an expired entry that fails to reload raises the GitHub
  exception, as in `CachingGithubApiClientExpiryTest#expiredValueIsNotServedWhenGithubFails`.
- `join()` wraps failures in `CompletionException`; the original
  `RuntimeException` is rethrown unchanged, as in
  `CachingGithubApiClientErrorTest#failuresAreRetriedOnNextCall`.
- Concurrent callers share the same failed future, so they fail with the same
  exception type (verified by the test); the same instance is expected from a
  shared future, not verified, as in
  `CachingGithubApiClientConcurrencyTest#concurrentFailuresTriggerOneRequestAndAllCallersFail`.
- The unique `owner` key makes the maximum size a defensive bound only: no
  eviction is exercised. `GithubApiClientWiringTest#cacheReceivesValidatedConfiguration`
  checks the policy, not an eviction.
- Observation: removing the executor injected in tests makes 7 tests fail, one
  expiry case and six error cases. The internal mechanism is not described here.
- Risk, not a measure: in production the default executor is
  `ForkJoinPool.commonPool()`; a blocking HTTP call could occupy a common-pool
  thread. This slice neither changes nor measures that risk.
- The check-then-put mutation is detected only probabilistically by
  `CachingGithubApiClientConcurrencyTest#concurrentSuccessesTriggerOneRequest`.

## Verification
`CachingGithubApiClientTest#secondCallDoesNotSendRequest` and
`CachingGithubApiClientTest#cachedListIsImmutable` cover reuse and immutability;
the two concurrency tests cover one request for 8 callers. The official command
`cd backend && ./mvnw clean verify` (AGENTS.md section 4) was run offline as
`cd backend && ./mvnw -o -C clean verify` and reported 146 tests and
`BUILD SUCCESS`.
