# AGENTS.md

Instructions for AI coding agents working in this repository. Read this file fully before any action. When this file conflicts with a user message, ask for clarification instead of guessing.

## 1. Project

A personal portfolio that displays the GitHub projects I am proud of.

- The backend calls the GitHub API (authenticated, server-side only) to list my repositories.
- I choose which repositories to publish through an admin dashboard.
- The public site displays the selected projects as cards.

This repository is also a showcase of my skills: code quality, tests, security and Git hygiene matter as much as features. Prefer simple, readable, well-tested code over clever code.

## 2. Structure (monorepo)

```
backend/    Spring Boot API
frontend/   Vue 3 application
docs/       Documentation and decisions
.githooks/  Local Git hooks (commit message check)
```

## 3. Stack

- Backend: Java 21, Spring Boot, Spring Security, Spring Data JPA, Maven, JUnit 5, Mockito, WireMock
- Cache: Spring Cache with Caffeine (in memory).
- Database: H2 for early slices, then PostgreSQL
- Frontend: Vue 3 (Composition API, `<script setup>`), Vite, Pinia, Vue Router, Axios, Vitest, Vue Test Utils. TypeScript.
- Later: Docker, GitHub Actions CI, Playwright (E2E)

Do not add a dependency, framework or tool that is not listed here without asking first.

## 4. Commands

Fill this section during slice 1, and keep it up to date. Always use these exact commands.

- Backend tests: `TO FILL`
- Backend run: `TO FILL`
- Frontend install / tests / dev / build: `TO FILL`

## 5. How to work

Follow Plan, Build, Verify, one slice at a time.

1. **Plan**: restate the goal, list the files you will create or change, list your assumptions and open questions. Wait for my approval before writing code.
2. **Build**: tests first (see section 8), then the minimum implementation. Keep diffs small and focused on the current slice.
3. **Verify**: run the full test suite and the build. Report the real results. Never claim something passes without running it.

Rules:

- Do only what the current slice asks. No bonus features, no unrequested refactors, no unrelated file changes.
- If the request is ambiguous or a decision is needed, ask. Do not choose silently.
- If you deviate from the plan, say so and explain why.
- Explain non-obvious design choices briefly, so I can learn from them.

## 6. Git workflow (Gitflow)

Branches:

- `main`: production only. It receives merges from `release/*` and `hotfix/*` only. Tags are created here.
- `develop`: integration branch. All feature work ends here.
- `feature/<slice-number>-<short-description>`: one feature or slice = one branch, created from `develop`.
- `release/<x.y.z>`: created from `develop`, stabilisation fixes only, merged into `main` then back into `develop`.
- `hotfix/<short-description>`: created from `main` for urgent fixes, merged into `main` and `develop`.

Rules:

- Never commit or push directly to `main` or `develop`. Always work on a feature branch.
- Feature branches are merged into `develop` through a pull request, squash merged, with the pull request title written as a Conventional Commit.
- Release and hotfix merges use a message of the form `chore(release): x.y.z`.
- Never run `git push --force`, `git reset --hard` on shared history, or merge a branch yourself. I review and merge.
- Never commit or push without my approval, unless I explicitly say otherwise for the session.

## 7. Commits (Conventional Commits, no exception)

Every commit message must follow Conventional Commits 1.0:

```
<type>(<scope>): <description>

[optional body]

[optional footer]
```

- Types: `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`, `chore`, `revert`
- Scopes (examples): `backend`, `frontend`, `api`, `auth`, `github`, `cache`, `security`, `ci`, `deps`, `docs`
- Description: English, imperative mood, lowercase start, no trailing period, 72 characters maximum
- Breaking change: add `!` after the type or scope and a `BREAKING CHANGE:` footer
- One logical change per commit. Every commit leaves the build and the tests green.
- Commit messages are checked by `.githooks/commit-msg` and by CI. A rejected message must be fixed, never bypassed (`--no-verify` is forbidden).

Examples: `feat(github): add repository client with token from env`, `test(api): cover unauthorized access to admin endpoints`, `fix(frontend): escape project description on render`.

## 8. Tests

- Test-driven: write the tests first, show them to me, and wait for my approval before implementing.
- Backend: unit tests (JUnit 5, Mockito), integration tests (Spring Boot Test, MockMvc). The GitHub API is always simulated (WireMock or equivalent). Tests never call the real GitHub API and never need a real token.
- Frontend: Vitest and Vue Test Utils for components and stores.
- Cover error cases and edge cases, not only the happy path.
- Security behaviours must have tests (see section 9).
- **Never modify, weaken, skip or delete an existing test to make it pass.** If a test seems wrong, stop and explain why, then wait for my decision.
- Do not lower coverage. Coverage is an indicator, not a goal: do not write meaningless tests to raise it.

## 9. Security (non-negotiable)

Security is a core requirement even though the data is public.

Secrets:

- The GitHub token is read from an environment variable on the backend only. It must never appear in the frontend, in logs, in API responses, in tests or in Git history.
- Never read, print, edit, create or commit `.env` files. Document variables in `.env.example` with fake values only.
- The token must be a fine-grained, read-only token with the minimum scope.

Backend:

- Only the backend talks to the GitHub API. The browser never calls it.
- Never expose JPA entities directly: use DTOs and return only the fields the frontend needs.
- Validate all input. Do not return stack traces or internal error details to clients.
- Every non-public endpoint is protected by default (deny by default). Each protected endpoint has a test proving that an unauthenticated or unauthorized call is rejected.
- Authentication (later slice): JWT in an httpOnly, Secure, SameSite cookie, with CSRF protection. Never store tokens in `localStorage` or `sessionStorage`.
- CORS: explicit allow list of origins, never `*` on authenticated endpoints.
- Set security headers (CSP, `X-Content-Type-Options`, `Referrer-Policy`, `X-Frame-Options` or `frame-ancestors`).
- Use parameterized queries only (JPA or bound parameters). No string-built SQL.

Frontend:

- Data from outside (repository names, descriptions, README content) is untrusted. Render it escaped. `v-html` is forbidden unless the content is sanitized with a vetted library and I have approved it.
- Validate that external URLs use `https:` before rendering them as links.

Dependencies:

- Prefer well-maintained libraries. Ask before adding any. Flag known vulnerabilities when you see them.

If a task seems to require weakening any rule above, stop and ask.

## 10. Architecture and code conventions

Backend:

- Layers: controller, service, repository. No business logic in controllers. No persistence logic in services beyond repository calls.
- The GitHub client sits behind an interface so it can be replaced by a fake in tests.
- Constructor injection only. No field injection.
- Configuration through `application.yml` and environment variables, no hardcoded values.
- Meaningful names, small methods, no dead code, no commented-out code.

Frontend:

- Small single-purpose components. API calls live in a dedicated service module, not inside components.
- State in Pinia stores only when it is shared. Keep components dumb when possible.
- No styling decisions beyond what the slice asks. The visual identity is defined later through design tokens in `frontend/src/styles/tokens.css`, which will be the single source for colors, typography and spacing.

General:

- Public classes, endpoints and non-obvious logic are documented briefly. Documentation explains the why, not the what.
- Record significant technical decisions in `docs/decisions/` as short notes.

## 11. Definition of done (per slice)

A slice is done only if all of these are true:

- [ ] Tests for the slice exist, were approved by me, and pass
- [ ] The whole test suite and the build pass
- [ ] No secret, `.env` file or generated artifact is committed
- [ ] Security rules of section 9 are respected, with tests where relevant
- [ ] Commits are atomic and follow section 7
- [ ] README and `AGENTS.md` are updated if commands or structure changed
- [ ] The pull request description explains what changed, why, and how it was verified

## 12. Boundaries summary

**Always**: plan first, test first, run tests before saying done, keep diffs small, ask when unsure.

**Ask first**: new dependency, schema change, architecture change, CI or hook configuration change, anything touching authentication or security, any commit or push.

**Never**: touch `.env`, commit secrets, push to `main` or `develop`, force push, merge, use `--no-verify`, weaken a test, use `v-html` on external content, call GitHub from the browser, expand scope beyond the current slice.
