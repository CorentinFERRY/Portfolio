# Slice 5 requirements traceability

Date: 2026-11-26
Slice: 5 (`GET /api/projects`)

## Rule

Every row names at least one executable test as `Classe#methode`. Test method
counts come from grep of `@Test` and `@ParameterizedTest` annotations. Executed
case counts come from the Maven filtered output with
`grep -E 'Tests run:|BUILD'`. Cases add the extra parameterized inputs; methods
do not. No commit hash is recorded here, and no tracked document refers to an
untracked file. Mutation rows use only mutations that were actually observed
red during slice 5; when a behaviour has no replayed mutation, the table says
so instead of inventing one.

## Counts

| File | `@Test` methods (grep) | `@ParameterizedTest` methods (grep) | Test methods (grep) | Executed cases (Maven) |
| --- | --- | --- | --- | --- |
| `ProjectServiceTest` | 8 | 2 | 10 | 19 |
| `ProjectsEndpointTest` | 9 | 1 | 10 | 14 |
| `ProjectsEndpointWireMockTest` | 2 | 0 | 2 | 2 |
| `GithubFailureLoggingTest` | 2 | 0 | 2 | 2 |
| `ProjectsSecurityConfigTest` | 1 | 1 | 2 | 4 |
| Slice 5 total | 22 | 4 | 26 | 41 |

Whole suite: 134 `@Test` methods plus 5 `@ParameterizedTest` methods equals 139
test methods by grep, and `Tests run: 208, Failures: 0, Errors: 0, Skipped: 0`
followed by `BUILD SUCCESS` by Maven. The slice 5 delta is 41 cases over the
measured slice 4 baseline of 167 cases.

## Requirements and replayed mutations

| Requirement | Test (`Classe#methode`) | Mutation constatee rouge | Note |
| --- | --- | --- | --- |
| `GET /api/projects` is anonymous; other methods stay forbidden | `ProjectsSecurityConfigTest#getProjectsWithoutAuthenticationIsAllowed`; `ProjectsSecurityConfigTest#mutatingProjectsRequestsWithoutAuthenticationAreForbidden` (3 cases) | M1: `requestMatchers` without `HttpMethod.GET` gave 405 instead of 403 in the 3 mutating cases | Deduced from the 4 executed cases and 3 failures: the anonymous GET case was not red under M1. |
| Two consecutive project requests use the cached GitHub client | `ProjectsEndpointWireMockTest#consecutiveGetProjectsRequestsUseCachedGithubClient` | M2: `DefaultGithubApiClient` without the cache made the test see 2 requests instead of 1 | The observed manual run with one repository did not prove the second call. |
| An empty selection returns an empty list without calling GitHub | `ProjectServiceTest#returnsEmptyListWithoutCallingGithubWhenSelectionIsEmpty`; `ProjectsEndpointTest#getProjectsReturnsEmptyJsonArrayWhenNothingIsSelected` | M3: removing the empty-selection short-circuit broke `verifyNoInteractions(githubApiClient)` | The endpoint test also checks the empty JSON array. |
| Duplicate GitHub ids produce one project | `ProjectServiceTest#duplicateGithubIdsProduceOneProject` | M4: removing the deduplication produced two projects | No endpoint-level duplicate case was added. |
| Null `pushedAt` sorts last | `ProjectServiceTest#sortsByPushedAtDescendingWithNullLast`; `ProjectsEndpointTest#getProjectsSortsByPushedAtDescending` | M5: `nullsLast` changed to `nullsFirst` made the null project come first | The endpoint test checks ids 1, 3, 2, 4. |
| `pushedAt` sorts descending | `ProjectServiceTest#returnsIntersectionOfSelectionAndGithubRepositories`; `ProjectServiceTest#sortsByPushedAtDescendingWithNullLast`; `ProjectsEndpointTest#getProjectsSortsByPushedAtDescending` | M6: `reverseOrder` changed to `naturalOrder` made the intersection and sorting service tests red | The intersection test was already sensitive to order; it does not isolate the intersection filter. |
| Equal timestamps keep GitHub order | `ProjectServiceTest#preservesGithubOrderForEqualPushedAt` | M12: adding `thenComparing(name)` made the expected GitHub order fail | No tie-breaker is present in the final code. |
| Uppercase `HTTPS` is accepted for `homepage` | `ProjectServiceTest#keepsUppercaseHttpsHomepageUnchanged` | M7: `equalsIgnoreCase` changed to `equals` made the uppercase homepage become null | The value is kept unchanged, not normalized. |
| A URI without host is rejected | `ProjectServiceTest#unsafeHomepagesBecomeNull` (cases 6 and 7); `ProjectServiceTest#unsafeHtmlUrlExcludesRepositoryButKeepsOthers` (cases 3 and 4) | M8: removing `getHost() == null` survived the first pass because `https:` alone throws `URISyntaxException` before the host check; it was killed after adding `https:/path` and `https:///path` | Both steps are recorded: first pass green, second pass red. |
| Unavailable and rate-limited GitHub failures return 503 with a fixed body | `ProjectsEndpointTest#githubUnavailableReturns503WithoutInternalDetails`; `ProjectsEndpointTest#githubRateLimitReturns503WithoutInternalDetails` | M9: the 503 handler returning 502 made both tests red | Both assert the exact body and the absence of internal details. |
| The 502 branch logs only the exception class name | `GithubFailureLoggingTest#warnLogsOnlyExceptionClassNameForOtherGithubFailureWithoutSentinel` | M10: removing `logFailure` from the 502 handler survived the first pass; it was killed by this named test | The first pass is recorded as green, the second as red. |
| An unsafe `htmlUrl` excludes the repository | `ProjectServiceTest#unsafeHtmlUrlExcludesRepositoryButKeepsOthers` (cases 1 and 2); `ProjectsEndpointTest#repositoryWithoutHttpsHtmlUrlIsAbsent` | M11: returning the raw `htmlUrl` without `httpsUrl` made cases 1 and 2 red | `ProjectServiceTest#nullHtmlUrlExcludesRepositoryWithoutException` and `ProjectsEndpointTest#nullFieldsAreSerializedAsJsonNull` cover null handling without a separate mutation. |
| The response DTO has exactly seven fields | `ProjectsEndpointTest#getProjectsWithoutAuthenticationReturns200WithOnlyDtoFields` | No mutation replayed for field count | The test asserts `jsonPath("$[0].*", hasSize(7))`. |
| Only selected public repositories are returned | `ProjectServiceTest#returnsIntersectionOfSelectionAndGithubRepositories`; `ProjectServiceTest#ignoresSelectedIdAbsentFromGithub`; `ProjectsEndpointTest#getProjectsIgnoresSelectedIdMissingFromGithub`; `ProjectsEndpointWireMockTest#selectedPrivateRepositoryIsNotExposed` | No dedicated mutation replayed | M6 made the intersection test red through order, not through the selection filter. |
| Upstream details are not serialized into error bodies | `ProjectsEndpointTest#githubUnavailableReturns503WithoutInternalDetails`; `ProjectsEndpointTest#githubRateLimitReturns503WithoutInternalDetails`; `ProjectsEndpointTest#otherGithubFailuresReturn502WithoutInternalDetails` (5 cases) | No mutation replayed for the body alone | M9 covers the 503 status branch; M10 covers logging, not the body. |

## OBSERVE

Manual jar run by the owner with a real `.env` and one selected repository:
HTTP 200 with one seven-field object, null description and homepage, an https
`htmlUrl`, and an ISO `pushedAt` ending in `Z`. Only one repository was present,
so neither sorting nor a second cached call is proven by that run. M2 and the
sorting tests prove those two behaviours. The smoke test was done by the owner,
outside the agent.

## DEDUIT

The `pushedAt` comparison is lexicographic because GitHub guarantees the ISO
string format; the local client does not parse or validate that format. This
reduces the timestamp-ordering claim to the upstream format plus the observed
test strings, rather than a general date-parsing guarantee.

## LIMITE

- URL authorities with exotic syntax are not tested; the cases cover scheme,
  whitespace, missing authority, missing host path forms, and uppercase `HTTPS`.
- Only one logging case is checked per error branch: `GithubUnavailableException`
  for 503 and `GithubAccessDeniedException` for 502.
- M8 and M10 needed two passes each; their first passes were green, and the
  final red results come from the added cases and the named 502 logging test.

## POINT OUVERT

- A non-GitHub error without a tested mapping remains open for slice 8 with a
  `RANDOM_PORT` test.
- The blocking GitHub call on `ForkJoinPool.commonPool()` is NON TRAITE and
  publicly exposed through `GET /api/projects`; it is deferred to slice 8.
- No real smoke test was run by an agent. The owner ran it with a private
  `.env`; no agent read, wrote, or printed that file.
