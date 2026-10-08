# 0003. Run commit checks and the backend build in GitHub Actions

Date: 2026-10-08
Status: accepted

## Context

Decision 0001 names CI as the enforcing gate for commit messages, because a
local hook can be bypassed or never enabled in a clone. Feature branches now
reach `develop` through pull requests, so the repository needs an automated
check for commit messages, pull request titles and the backend build. The
workflow is `.github/workflows/ci.yml`.

## Decision

- Two jobs run on pushes and pull requests to `develop` and `main`:
  `check-commits` runs the script test suites and the message checks,
  `backend-build` runs `./mvnw clean verify` in `backend/`.
- `.github/scripts/check-commits.sh` holds no rules of its own: it feeds the
  messages to `.githooks/commit-msg`, so the rules live in exactly one place.
- A pull request is checked as the subject GitHub writes after the squash
  merge, `<title> (#<number>)`. The suffix is not an assumption: the
  repository history already contains squash merges written that way, `(#1)`
  and `(#2)`.
- The `pull_request` trigger lists the `edited` activity type, so correcting
  the pull request title re-runs the check.
- Merge commits are checked like any other commit: a range containing a
  default merge message fails.

### Security posture

- The actions are pinned to a full commit SHA, with the version in a comment.
- The workflow grants only `contents: read`.
- There is no `pull_request_target` and no `secrets.*`: the workflow needs no
  secret, and the untrusted values of the event reach the script only through
  `env:` blocks.
- A concurrency group cancels superseded pull request runs, and never
  cancels a run triggered by a push.
- Every job sets `timeout-minutes`.

## Consequences

- Pinned actions are updated manually; the version comment is what tells a
  reader which release a pin corresponds to.
- The check names `check-commits` and `backend-build` must stay stable,
  because branch protection will require them.
- The pull request workflow and the scripts run from the pull request branch,
  so a change under `.github/` or `.githooks/` can weaken the check that
  judges it and deserves closer review; a CODEOWNERS file can enforce that
  later.
- The commit range of a pull request is computed from the base commit at the
  time of the event, so once the base branch moves it can include commits
  that are not the author's own, which is a reason to rebase.
- The Update branch button of GitHub creates a merge commit that the check
  rejects, so the branch is rebased locally instead.
- The `edited` event also re-runs the checks when the description changes.
