# Slice 3 requirements traceability

Date: 2026-11-20
Slice: 3 (Caffeine cache around the GitHub client)

## Rule

Every row is a testable requirement and names at least one executable test as
`Class#method`. Line numbers are deliberately not used because they drift when
tests change. "No recorded mutation proof" means no verifiable mutation proof
is recorded for that test; it is not a claim that the behaviour is untestable.
Mutation proofs are described by their effect only. Proofs marked `properties
only, independent of the cache implementation` were run before any cache existed;
proofs marked `synchronous` were run on the earlier synchronous cache, before
AsyncCache; proofs marked `final AsyncCache` were run on the final implementation.

## Requirements proved by tests

| Requirement | Test (`Class#method`) | Proof |
| --- | --- | --- |
| Cache TTL is rejected when it is not strictly positive at construction and startup | `GithubPropertiesTest#constructorRejectsNonPositiveCacheTtl`; `GithubCachePropertiesStartupValidationTest#invalidCacheTtlStopsStartup` | Removing TTL validation made the constructor test fail with "Expected java.lang.IllegalStateException to be thrown, but nothing was thrown." and the startup test fail with "context should have failed to start". `properties only, independent of the cache implementation` |
| Cache maximum size is rejected when it is not strictly positive at construction and startup | `GithubPropertiesTest#constructorRejectsNonPositiveCacheMaximumSize`; `GithubCachePropertiesStartupValidationTest#invalidCacheMaximumSizeStopsStartup` | Removing maximum-size validation produced the same two messages in those tests. `properties only, independent of the cache implementation` |
| Defaults are `300000` ms and `100` in YAML | `GithubCachePropertiesStartupValidationTest#applicationYmlDeclaresCacheDefaults` | Changing the YAML default to `300001` made the test fail with "expected: <300000> but was: <300001>". No recorded mutation proof for the constructor default, `GithubPropertiesTest#defaultsCacheConfiguration`. `properties only, independent of the cache implementation` |
| Valid cache properties and environment variables bind | `GithubCachePropertiesStartupValidationTest#validCachePropertiesBind`; `GithubCachePropertiesStartupValidationTest#envVarGITHUB_CACHE_TTL_MSBoundViaSystemEnvironmentSource`; `GithubCachePropertiesStartupValidationTest#envVarGITHUB_CACHE_MAXIMUM_SIZEBoundViaSystemEnvironmentSource` | Valid values and both environment variables bind. No recorded mutation proof. |
| The context exposes a single caching client and the delegate receives the validated configuration | `GithubApiClientWiringTest#contextExposesSingleGithubApiClientBean`; `GithubApiClientWiringTest#clientReceivesValidatedConfiguration` | Removing the decorator from the wiring made the bean test fail with "expected: <0> but was: <1>" for the default client and the configuration test fail with `NoSuchBeanDefinitionException`. `final AsyncCache` |
| The cache policy receives the validated TTL and maximum size | `GithubApiClientWiringTest#cacheReceivesValidatedConfiguration` | A hardcoded TTL of `300000` made the test fail with "expected: <PT12.345S> but was: <PT5M>"; a hardcoded size of `100` made it fail with "expected: <7> but was: <100>". `final AsyncCache` |
| Repeated calls reuse the repository list, including an empty result | `CachingGithubApiClientTest#secondCallDoesNotSendRequest`; `CachingGithubApiClientTest#emptyResultIsCached` | A direct call to the delegate made both tests fail with "Expected exactly 1 requests ... but received 2" on `/users/fictional-owner/repos`. `synchronous` |
| The cached list is immutable for callers | `CachingGithubApiClientTest#cachedListIsImmutable` | Removing the immutable copy made the test fail with "Expected java.lang.UnsupportedOperationException to be thrown, but nothing was thrown." `final AsyncCache` |
| The value is kept before the TTL and reloaded after it | `CachingGithubApiClientExpiryTest#doesNotExpireBeforeTtl`; `CachingGithubApiClientExpiryTest#expiresAfterTtl` | A hardcoded TTL of `500` ms made the first test fail with "received 2"; a hardcoded TTL of `300000`, removing the ticker, or using `Duration.ofSeconds` made the second fail with "received 1" instead of "2". `final AsyncCache` |
| An expired value is never served after a GitHub failure | `CachingGithubApiClientExpiryTest#expiredValueIsNotServedWhenGithubFails` | A stale-on-error fallback made the test fail with "Expected GithubUnavailableException to be thrown, but nothing was thrown." `final AsyncCache` |
| Failures keep the exact original exception type and are retried on the next call | `CachingGithubApiClientErrorTest#failuresAreRetriedOnNextCall` | The six cases are 401, 403, 404, 429, 500 and read timeout. Returning an empty list on failure or wrapping the exception made all six cases fail with the wrong type or no exception `final AsyncCache`; removing the `CompletionException` unwrapping made all six fail with `CompletionException` `final AsyncCache`. |
| Concurrent failures produce one HTTP request and all callers fail with the same exception type | `CachingGithubApiClientConcurrencyTest#concurrentFailuresTriggerOneRequestAndAllCallersFail` | The 20 repetitions use 8 callers. A direct future outside the cache made all repetitions fail with "received 8" on 429; removing the `CompletionException` unwrapping made all 20 repetitions fail with `CompletionException` instead of the expected exception type. The test verifies the same exception type; the same instance is expected from a shared future, not verified. `final AsyncCache` |
| Concurrent successes produce one HTTP request and the same list for all callers | `CachingGithubApiClientConcurrencyTest#concurrentSuccessesTriggerOneRequest` | The 20 repetitions use 8 callers. A direct future outside the cache made all repetitions fail with "received 8"; check-then-put made the test fail in three separate executions with counts of 8, 2 and 3, so that mutation is detected only probabilistically. `final AsyncCache` |
| Seven tests depend on the executor injected in the test constructors | `CachingGithubApiClientExpiryTest#expiresAfterTtl`; `CachingGithubApiClientErrorTest#failuresAreRetriedOnNextCall` | Removing the injected executor made 7 tests fail: one expiry case and six error cases, with "Expected exactly 2 requests ... but received 1". The internal mechanism is not described here. `final AsyncCache` |
| Non-regression: the whole suite stays green | All tests cited above | `Tests run: 146` and `BUILD SUCCESS`. The official command is `cd backend && ./mvnw clean verify` (AGENTS.md section 4); the local proof ran offline as `cd backend && ./mvnw -o -C clean verify` with output filtered on `Tests run` and `BUILD`. |

## Execution reconciliation

The table cites 21 methods covering 64 executions: 3 + 6 + 3 + 3 + 3 + 6 + 40.
Two `GithubApiClientWiringTest` methods predate the slice and were modified, so
they add no new execution. The slice therefore adds 62 executions on a baseline
of 84: 9 (properties and startup validation) + 4 (cache reuse and the new wiring
test) + 3 (expiry) + 6 (parameterised errors) + 40 (repeated concurrency). The
total is 146.

## Limits

- The maximum size is proved by the cache policy in
  `GithubApiClientWiringTest#cacheReceivesValidatedConfiguration`, not by an
  eviction: only one cache key is used.
- The tests verify the same exception type; the same instance is expected from
  a shared future, but is not verified.
- The check-then-put mutation is detected only probabilistically by
  `CachingGithubApiClientConcurrencyTest#concurrentSuccessesTriggerOneRequest`.
- The seven tests that fail when the injected executor is removed are reported
  as an observation; their internal mechanism is out of scope here.
- A blocking HTTP call on `ForkJoinPool.commonPool()` is a risk, not a measured
  effect and not a change made in this slice.
