# Portfolio

A personal portfolio that displays the GitHub projects I am proud of. The
backend calls the GitHub API from the server side only, I choose which
repositories to publish through an admin dashboard, and the public site shows
the selected projects as cards.

This repository is also a showcase of my skills, so code quality, tests,
security and Git hygiene matter as much as features.

## Stack

- Backend: Java 21, Spring Boot, Spring Security, Spring Data JPA, Maven
- Frontend: Vue 3 (Composition API, `<script setup>`), Vite, Pinia, Vue Router,
  Axios, TypeScript
- Tests: JUnit 5, Mockito, WireMock, Vitest, Vue Test Utils

The full stack is listed in AGENTS.md section 3.

## Structure

```
backend/    Spring Boot API
frontend/   Vue 3 application
docs/       Documentation and decisions
.githooks/  Git hooks (commit message check)
.github/    GitHub Actions workflow
AGENTS.md   Conventions and rules for AI coding agents working in this repository
```

`AGENTS.md` holds the conventions and rules that any AI coding agent working in
this repository must follow.

`backend/` is the Spring Boot skeleton and `frontend/` is still a placeholder.
Technical decisions are recorded as short notes in `docs/decisions/`.

## Prerequisites

- Java 21. Maven is not needed: the Maven wrapper is committed, so `./mvnw` in
  `backend/` downloads and runs the required Maven version.

## Commands

The commands are kept in AGENTS.md section 4; always use the exact commands
recorded there.

## Getting started

```sh
git clone <url>
cd portfolio

# Required once per clone: the hook is not activated by cloning.
git config core.hooksPath .githooks

cp .env.example .env
```

Then fill in `.env` with real values. `.env` is ignored by Git and must never be
committed; `.env.example` holds fake values only.

## Git workflow

Gitflow, described in full in AGENTS.md section 6:

- `main`: production, receives merges from `release/*` and `hotfix/*` only
- `develop`: integration branch, all feature work ends here
- `feature/<slice-number>-<short-description>`: one slice, one branch, from
  `develop`
- `release/<x.y.z>` and `hotfix/<short-description>` for stabilisation and fixes

Feature branches reach `develop` through a pull request that is squash merged,
with the pull request title written as a Conventional Commit. I review and
merge; the agent never commits or pushes without my approval.

## Commit rules

Every message follows Conventional Commits 1.0, as specified in AGENTS.md
section 7:

```
<type>[(scope)][!]: <description>

[optional body]

[optional footer]
```

The scope is optional but must be a lowercase token when present. The whole
subject line is limited to 72 characters. Add `!` after the type or the scope
for a breaking change. A rejected message must be fixed, never bypassed:
`--no-verify` is forbidden.

## Git hooks

`.githooks/commit-msg` rejects any subject that does not follow the rules above.
It needs only a POSIX shell and the standard utilities `sed`, `tr`, `grep` and
`wc`, which are available wherever Git is normally installed. The same script
checks a message file outside Git, which is how CI reuses it.

Enable it once per clone:

```sh
git config core.hooksPath .githooks
```

`core.hooksPath` is local configuration, so a fresh clone has no hook until you
run that command. Check it at any time with:

```sh
git config --get core.hooksPath
git rev-parse --git-path hooks/commit-msg
```

The hook is not the only gate. A clone can have it disabled or bypassed, so the
CI check in AGENTS.md section 7 is the enforcing gate and the hook gives
early feedback. The reasoning is recorded in
`docs/decisions/0001-conventional-commit-hook.md`.

Two things the hook does not cover, both documented in AGENTS.md section 6:

- `git revert -e` does not run the hook, so a revert message written in the
  editor is only checked by CI. Revert with `git revert --no-commit` followed by
  `git commit` when you want the local check.
- A plain `git pull` may refuse on diverged branches or merge and produce a
  message the hook rejects. Use `git pull --rebase`.

## Continuous integration

`.github/workflows/ci.yml` runs on pushes and pull requests to `develop` and
`main`, with two jobs:

- `check-commits` runs both script test suites, then checks every commit
  message of the event range with `.github/scripts/check-commits.sh`, which
  reuses the hook above. On a pull request it also checks the title as GitHub
  will write it after the squash merge.
- `backend-build` runs `./mvnw clean verify` in `backend/` with Java 21.

Both suites run locally with:

```sh
sh .githooks/commit-msg.test.sh
sh .github/scripts/check-commits.test.sh
```

The workflow uses no repository secret, only the automatic read-only token
used by the checkout and the Java setup actions; the values of the event
reach the script through environment variables only. The reasoning is recorded in
`docs/decisions/0003-ci-workflow.md`.

## Security

- The GitHub token is read from an environment variable on the backend only. It
  must never appear in the frontend, in logs, in API responses, in tests or in
  Git history. Use a fine-grained, read-only token with the minimum scope.
- `.env` holds real values and is ignored. `.env.example` is the only env file
  that is committed.
- The browser never calls the GitHub API. Secrets and key files, local
  databases and `application-local.*` are ignored by `.gitignore`.

The full security rules are in AGENTS.md section 9.
