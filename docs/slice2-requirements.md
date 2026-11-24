# Slice 2 and 2b requirements traceability

Date: 2026-11-18
Reference commit: f29012f

## Rule

Every row below is a testable requirement and names at least one executable test
as `Class#method`. Line numbers are deliberately not used because they drift
when tests change. `aucune mutation connue` means no mutation proof with a
verifiable, line-addressable source in a tracked file was found; it is not a
claim that the behaviour is untestable. When a mutation is recorded, the
Mutation column states what was broken, which test failed, and the commit that
contains the proof.

## Requirements proved by tests

| Requirement | Tests (`Class#method`) | Mutation | Proof |
| --- | --- | --- | --- |
| Configuration is validated at construction and startup | `GithubPropertiesTest#constructorValidatesOwnerNotBlank`; `GithubPropertiesTest#constructorValidatesOwnerFormat`; `GithubPropertiesTest#constructorValidatesTokenNotBlank`; `GithubPropertiesStartupValidationTest#missingOwnerStopsStartup`; `GithubPropertiesStartupValidationTest#blankOwnerStopsStartup`; `GithubPropertiesStartupValidationTest#blankTokenStopsStartup`; `GithubPropertiesStartupValidationTest#invalidOwnerStopsStartupWithoutEchoingPropertyValues`; `GithubPropertiesStartupValidationTest#validValuesStartContextAndExposeBoundProperties` | aucune mutation connue | Invalid owner or token stops startup with a fixed message; valid values bind; no sentinel value appears in the failure messages. |
| `toString()` redacts the token | `GithubPropertiesTest#toStringRedactsToken` | aucune mutation connue | The rendered properties contain `[REDACTED]` and not the fake token. |
| Missing and null topics become an empty list | `GithubRepositoryTest#createsWithDefaultsForNullTopics`; `GithubApiClientTest#missingTopicsGivesEmptyList` | aucune mutation connue | The model default and the client mapping both yield an empty topics list. |
| Topics are an immutable copy | `GithubRepositoryTest#topicsAreImmutableCopy`; `GithubRepositoryTest#topicsAreDetachedFromSource` | Replacing `List.copyOf(topics)` with `topics` made `GithubRepositoryTest#topicsAreImmutableCopy` fail with `UnsupportedOperationException` not thrown and `GithubRepositoryTest#topicsAreDetachedFromSource` fail with expected `<[a]>` but was `<[a, b]>`; replacing it with `Collections.unmodifiableList(topics)` left the immutability test green but made `GithubRepositoryTest#topicsAreDetachedFromSource` fail with expected `<[a]>` but was `<[a, b]>`. Commit `57daefe`. | Mutation of the source list does not change the repository, and adding to `topics()` throws. |
| Baseline request sends headers and query defaults and maps fields | `GithubApiClientTest#listsPublicRepositoriesFiltersAndMapsFields` | aucune mutation connue | WireMock only serves the request when `type=owner`, `sort=pushed`, `direction=desc`, `per_page=100`, `page=1`, Authorization, User-Agent and API version headers are present; name, id and stars are mapped. |
| User-Agent is the exact expected value | `GithubApiClientTest#sendsExactUserAgentHeader` | aucune mutation connue | The stub requires `User-Agent: portfolio-backend`. |
| Private repositories are dropped | `GithubApiClientTest#dropsPrivateRepos` | aucune mutation connue | Only the repository with `private: false` is returned. |
| Missing `private` fails closed | `GithubApiClientTest#missingPrivateFieldThrows`; `GithubApiClientTest#missingPrivateFieldWithPublicVisibilityThrows` | aucune mutation connue | Both payloads raise `GithubUnexpectedResponseException`, even when `visibility` is `public`. |
| Optional nulls are accepted, wrong types and non-array roots fail, empty arrays stay empty | `GithubApiClientTest#nullFieldsAccepted`; `GithubApiClientTest#wrongTypeThrows`; `GithubApiClientTest#notArrayThrows`; `GithubApiClientTest#emptyArrayReturnsEmpty` | aucune mutation connue | Null optional fields are tolerated; wrong id type and object root raise `GithubUnexpectedResponseException`; an empty array returns no repositories. |
| Non-public visibility is dropped | `GithubApiClientTest#dropsRepositoriesWithNonPublicVisibility`; `GithubApiClientTest#keepsRepositoryWithPublicVisibility`; `GithubApiClientTest#nullVisibilityFallsBackToPrivateFlag`; `GithubApiClientTest#dropsPrivateRepositoryEvenWithPublicVisibility`; `GithubApiClientTest#visibilityIsCaseSensitive` | Removing the non-public visibility drop made `GithubApiClientTest#dropsRepositoriesWithNonPublicVisibility` and `GithubApiClientTest#visibilityIsCaseSensitive` fail. Commit `65c3d62`. | `internal` and `private` are dropped; `public` is kept; null falls back to `private`; a private repository stays dropped even with `visibility: public`. |
| Visibility comparison is case-sensitive | `GithubApiClientTest#visibilityIsCaseSensitive` | Replacing the exact `public` comparison with a case-insensitive comparison made `GithubApiClientTest#visibilityIsCaseSensitive` fail. Commit `65c3d62`. | `Public` is not treated as `public`. |
| Non-textual visibility fails closed | `GithubApiClientTest#nonTextualVisibilityThrows` | Replaying the mutation on the final code — replacing `if (!visibility.isString())` with `if (false)` — made `GithubApiClientTest#nonTextualVisibilityThrows` fail with `Expected io.github.corentinferry.github.exception.GithubUnexpectedResponseException to be thrown, but nothing was thrown`. | Numeric visibility raises `GithubUnexpectedResponseException`. |
| Pagination follows `Link rel="next"` | `GithubApiClientPaginationTest#followsPagination` | aucune mutation connue | Two pages are fetched and both repositories are returned. |
| Same-origin is enforced for next links | `GithubApiClientPaginationTest#throwsWhenNextLinkHasDifferentHost`; `GithubApiClientPaginationTest#throwsWhenNextLinkHostStartsWithBaseHost`; `GithubApiClientPaginationTest#throwsWhenNextLinkHasDifferentPort`; `GithubApiClientPaginationTest#throwsWhenNextLinkHasDifferentScheme`; `GithubApiClientPaginationTest#throwsWhenNextLinkIsInvalidUri`; `GithubApiClientPaginationTest#throwsWhenNextLinkHasCredentials`; `GithubApiClientPaginationTest#throwsWhenNextLinkHostHasTrailingDot`; `GithubApiClientSecondServerTest#nextLinkToSecondServerIsRejectedWithoutRequest` | Removing `ensureSameOrigin(baseUri, uri)` made `GithubApiClientSecondServerTest#nextLinkToSecondServerIsRejectedWithoutRequest` fail: expected `GithubPaginationViolationException` was not thrown and the second server received 1 request instead of 0. Commit `f29012f`. | Every origin variant is rejected before a request is sent; the malformed URL is not echoed. |
| Page cap is 10 with no request beyond it | `GithubApiClientPaginationTest#throwsWhenPageCapExceededAndMakesExactlyTenRequests` | aucune mutation connue | The client throws after exactly 10 requests and never requests page 11. |
| 3xx redirects are mapped to unexpected response and not followed | `GithubApiClientErrorTest#throwsUnexpectedOn3xx`; `GithubApiClientSecondServerTest#redirectToSecondServerIsRejectedWithoutRequest` | Replacing `setInstanceFollowRedirects(false)` with `true` made `GithubApiClientSecondServerTest#redirectToSecondServerIsRejectedWithoutRequest` fail: expected `GithubUnexpectedResponseException` was not thrown and the second server received 1 request instead of 0. Commit `f29012f`. | A 3xx raises `GithubUnexpectedResponseException`; a Location pointing at a second local server causes zero requests to it. |
| Error statuses map to the expected exceptions and carry rate-limit reset | `GithubApiClientErrorTest#throwsAuthenticationFailureOn401`; `GithubApiClientErrorTest#throwsAccessDeniedOn403Other`; `GithubApiClientErrorTest#throwsRateLimitOn429`; `GithubApiClientErrorTest#throwsRateLimitOn403WithRemainingZero`; `GithubApiClientErrorTest#throwsRateLimitOn403WithRetryAfter`; `GithubApiClientErrorTest#throwsOwnerNotFoundOn404`; `GithubApiClientErrorTest#throwsUnavailableOn500`; `GithubApiClientErrorTest#throwsUnexpectedOnMalformedJson` | aucune mutation connue | 401, 403 variants, 404, 429, 5xx and malformed JSON produce the documented exception types; reset values 123 and 456 are exposed. |
| Malformed responses do not leak body or WARN logs | `GithubApiClientErrorTest#malformedJsonDoesNotLogWarning`; `GithubApiClientErrorTest#nonJacksonUnexpectedFailureLogsClassNameOnly` | aucune mutation connue | No WARN or body text is logged; a non-Jackson failure logs only the exception class name. |
| Timeouts and refused connections map to unavailable | `GithubApiClientErrorTest#throwsUnavailableOnReadTimeout`; `GithubApiClientErrorTest#throwsUnavailableOnConnectionRefused` | aucune mutation connue | Read timeout and repeated connection refusal raise `GithubUnavailableException` without logging the fake token. |
| DEBUG capture covers Spring and the client and never contains the token | `GithubApiClientDebugLoggingTest#debugCaptureCoversSpringAndClientLoggers`; `GithubApiClientDebugLoggingTest#debugLogsNeverContainTheToken` | Replacing the owner-only DEBUG line with a log that also included the token made `GithubApiClientDebugLoggingTest#debugLogsNeverContainTheToken` fail with `token leaked into client log events at level DEBUG`. Commit `8113f29`. | Both loggers capture DEBUG events, the fake Authorization header reaches WireMock, and no event message, argument or throwable contains the token. |
| Exception messages do not contain the token | `GithubApiClientErrorTest#throwsAuthenticationFailureOn401`; `GithubApiClientErrorTest#throwsRateLimitOn429`; `GithubApiClientSecondServerTest#nextLinkToSecondServerIsRejectedWithoutRequest`; `GithubApiClientSecondServerTest#redirectToSecondServerIsRejectedWithoutRequest` | aucune mutation connue | Exception messages from injected failures, pagination violations and redirects do not contain the fake token. |
| Explicit environment markers, defaults, malformed URL and variable-name binding | `GithubPropertiesStartupValidationTest#applicationYmlDeclaresApiBaseUrlDefault`; `GithubPropertiesStartupValidationTest#defaultsFromApplicationYmlWhenNoEnvVar`; `GithubPropertiesStartupValidationTest#malformedBaseUrlStopsStartupWithoutEchoingValue`; `GithubPropertiesStartupValidationTest#envVarGITHUB_API_BASE_URLBoundViaSystemEnvironmentSource`; `GithubPropertiesStartupValidationTest#envVarGITHUB_CONNECT_TIMEOUT_MSBoundViaSystemEnvironmentSource`; `GithubPropertiesStartupValidationTest#envVarGITHUB_READ_TIMEOUT_MSBoundViaSystemEnvironmentSource`; `GithubPropertiesStartupValidationTest#envVarGITHUB_TOKENAndOwnerBoundWithoutProperties`; `GithubPropertiesStartupValidationTest#envVarGITHUB_API_TOKENDoesNotBind` | aucune mutation connue | The YAML default is visible, no-env and blank-env cases fall back safely, malformed URLs stop startup without echoing the value, and only the documented variable names bind. |
| URLs use URI template variables instead of concatenation | `GithubApiClientTest#encodesQuestionMarkInOwnerAsUriTemplateVariable`; `GithubApiClientTest#encodesSlashInOwnerAsUriTemplateVariable`; `GithubApiClientTest#keepsBasePathPrefixWithoutTrailingSlash`; `GithubApiClientTest#keepsBasePathPrefixWithTrailingSlash` | aucune mutation connue | Owner characters are encoded and a configured base path is preserved with or without a trailing slash. |
| The client is exposed as one Spring bean with validated configuration | `GithubApiClientWiringTest#contextExposesSingleGithubApiClientBean`; `GithubApiClientWiringTest#clientReceivesValidatedConfiguration` | Removing the `@Bean` method from `GithubPropertiesConfig` made `GithubApiClientWiringTest#contextExposesSingleGithubApiClientBean` fail with expected `<1>` but was `<0>` and `GithubApiClientWiringTest#clientReceivesValidatedConfiguration` fail with `NoSuchBeanDefinitionException: No qualifying bean of type GithubApiClient`. Commit `0f2e99c`. | The context exposes exactly one `DefaultGithubApiClient` and it receives the bound `GithubProperties`. |
| The official OpenAPI example is mapped and unknown fields are ignored | `GithubApiClientOpenApiFixtureTest#deserializesOfficialExampleAndIgnoresUnknownFields` | Changing `"stargazers_count": 80` to `81` in the fixture made `GithubApiClientOpenApiFixtureTest#deserializesOfficialExampleAndIgnoresUnknownFields` fail with expected `<80>` but was `<81>`. Commit `c260162`. | The tracked fixture is loaded from the classpath and 12 mapped values are asserted while source-only fields are ignored. |
| A second WireMock server receives zero requests | `GithubApiClientSecondServerTest#nextLinkToSecondServerIsRejectedWithoutRequest`; `GithubApiClientSecondServerTest#redirectToSecondServerIsRejectedWithoutRequest` | Removing `ensureSameOrigin(baseUri, uri)` made `GithubApiClientSecondServerTest#nextLinkToSecondServerIsRejectedWithoutRequest` fail with no `GithubPaginationViolationException` and 1 second-server request; enabling redirects made `GithubApiClientSecondServerTest#redirectToSecondServerIsRejectedWithoutRequest` fail with no `GithubUnexpectedResponseException` and 1 second-server request. Commit `f29012f`. | The first server receives exactly one authenticated GET and the second server has no matching requests and no serve events in both scenarios. |

## Testable gaps not closed

- Conditional Authorization header: the plan asks for the header only when a
  token is configured, but no test proves the absent-token branch. The current
  `GithubProperties#validate` rejects a null or blank token, so that branch is
  not reachable through validated configuration.
- Exhaustive existence of github-prefixed exceptions: tests exercise several
  exception types, but no test enumerates every required class.
- Fake values in `backend/src/test/resources/application-test.yml`: tests use
  the test profile, but no assertion proves the contents of that file.

The WireMock dependency, documentation, this table and the full build are
build or documentation activities, not testable requirements; they are not
listed as gaps.

## Slice 2c string type checks

The gap is narrowed: eight usages are proven, four are deduced unreachable and
remain unproven.

OBSERVED: 8 of the 12 `isString` usages now have a test with a non-null
invalid type (`42`): seven cases in `GithubApiClientTest#nonStringFieldsThrow`
(`name`, `html_url`, `description`, `homepage`, `language`, `pushed_at` and the
first `topics` element) and `GithubApiClientTest#nonTextualVisibilityThrows`
for `visibility`. The 8 mutations were replayed on the final code; each turned
exactly one test red with `Expected io.github.corentinferry.github.exception.GithubUnexpectedResponseException to be thrown, but nothing was thrown`.

DEDUCED: the four extraction usages (`description`, `homepage`, `language` and
`pushed_at` in the extraction part of the mapping method) only receive `null`
or a string, because the preceding validation throws first. They are not
covered by a test; no reflection-based test was written deliberately.

LIMIT: only one invalid type is tested (a number). The tests were green as soon
as they were written; their value comes from the mutations.

OPEN POINT: the four extraction lines look like unreachable defensive code.
AGENTS.md section 10 forbids dead code. Address this later, outside this
slice, because "do only what the slice asks".
