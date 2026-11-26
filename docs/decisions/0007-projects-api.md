# 0007 - Projects API

Date: 2026-11-26
Status: Accepted

## Context

The public site needs a list of projects chosen from the local selection of
published repository ids. The repository data comes from the GitHub API through
a server-side client. That external data is untrusted: URLs can be unsafe and
upstream error messages can contain internal details. The endpoint must expose
only the fields the frontend needs, must not leak upstream details, and must
stay simple while the backend remains synchronous.

## Decision

- `GET /api/projects` is mapped by `ProjectController` and is public. All other
  methods on that path stay denied by default through `requestMatchers` with
  `HttpMethod.GET`, and all other routes stay denied by default.
- `ProjectService` computes the intersection of the selected ids and the public
  repositories returned by `GithubApiClient`: unselected repositories are not
  returned, selected ids absent from GitHub are ignored, duplicate GitHub ids
  produce one project, and an empty selection returns an empty list without
  calling GitHub.
- `ProjectResponse` has exactly seven fields: `id`, `name`, `description`,
  `htmlUrl`, `homepage`, `language`, `pushedAt`. No JPA entity or upstream field
  is exposed directly, and null values stay null in JSON.
- Projects are sorted by `pushedAt` descending with `Comparator.nullsLast` and
  `Comparator.reverseOrder()`. The comparison is lexicographic on the string
  returned by GitHub. The sort is stable because no tie-breaker is added, so
  repositories with equal `pushedAt` keep their GitHub order.
- URL filtering is done in the backend with `java.net.URI`. A value is accepted
  only when its scheme is `https` by case-insensitive comparison and it has a
  host. An invalid `homepage` becomes `null`. An invalid or missing `htmlUrl`
  excludes the repository.
- `GithubUnavailableException` and `GithubRateLimitExceededException` produce
  HTTP 503 with the fixed body `{"error":"github_unavailable"}`. Every other
  `GithubApiException` produces HTTP 502 with the fixed body
  `{"error":"github_error"}`. No upstream message, URL, token, exception class
  name, stack trace, or `Retry-After` header is returned.
- Both error handlers log at WARN level only `exception.getClass().getSimpleName()`.
  No message and no throwable is passed to the logger.
- The call from ProjectService to the GitHub client stays synchronous.
  No async annotation is added in this slice.

## Consequences

- The frontend receives a small DTO and never sees private repositories, even
  if their ids are present in the local selection.
- The GitHub token remains server-side; error bodies and logs do not carry
  upstream details. Logging only the class name keeps the 503 and 502 branches
  diagnosable without copying secrets into log files.
- A stable sort preserves a predictable order for equal timestamps: `ProjectServiceTest#preservesGithubOrderForEqualPushedAt` fails if a name tie-breaker is added (mutation M12).
- Rejecting an unsafe `htmlUrl` removes the whole project, while rejecting an
  unsafe `homepage` only clears that field. This reflects the different roles:
  the repository link is required, the homepage is optional.
- OBSERVE: a manual jar run by the owner with a real `.env` and one selected id
  returned HTTP 200 with one seven-field object, null description and homepage,
  an https `htmlUrl`, and an ISO `pushedAt` ending in `Z`. That manual run had a
  single repository, so it proves neither sorting nor a second cached request.
  M2 and the sorting tests prove those two behaviours.
- DEDUIT: the sort is lexicographic because the ISO-8601 `Z` format is produced
  by GitHub. It is not validated by the local client, so the string comparison
  is a deduction from the upstream format.
- LIMITE: exotic URL authorities are not tested, and only one case per error
  branch is checked for logging.
- POINT OUVERT: a non-GitHub error without a tested mapping remains for slice 8
  with a `RANDOM_PORT` test. The blocking call on `ForkJoinPool.commonPool()` is
  NON TRAITE and publicly exposed through this endpoint; it is deferred to
  slice 8. No real smoke test was run by an agent; the owner ran it with a
  private `.env`.
