# Slice 7 requirements traceability

Date: 2026-11-26
Slice: 7, group A (Axios, typed service, Vite proxy)

## Rule

Every row names at least one executable test or command. Test names are written
`projects.spec.ts > <name>`; case counts come from `npm test` output. Statuses:
`OBSERVE` = executed by the agent during this group with a real output recorded
below; `DEDUIT` = inferred from an observed setup and not probed at socket level;
`LIMITE` = reported by the owner or deferred to group C. No row is marked
`OBSERVE` on the owner's word alone. Mutation rows use only mutations actually
replayed red in this group.

## Proofs

- **Version lookup and install**: `npm view axios version` -> `1.21.1`.
  `npm install axios@1.21.1 --save-exact` -> `added 27 packages, and audited 166
  packages in 1s`, `5 moderate severity vulnerabilities`. `npm ls axios --all`
  -> `portfolio-frontend@0.0.0` / `└── axios@1.21.1`.
  `frontend/package-lock.json` lists four direct dependencies for axios:
  `follow-redirects@1.16.3`, `form-data@4.0.6`, `https-proxy-agent@5.0.1`,
  `proxy-from-env@2.1.0`.
- **Owner verification**: the owner checked the publication date and the 2026
  security advisories for `1.21.1` on npm, approved the version, and confirmed
  that `https-proxy-agent` is an official axios dependency. The agent did not
  replay the date or advisory lookup.
- **Audit**: `npm audit` reported `glob` and `minimatch` in the chain
  `@vue/test-utils -> js-beautify -> editorconfig -> glob -> minimatch`, with
  `5 moderate severity vulnerabilities`, identical to the accepted baseline in
  ADR 0008. No alert is attributable to axios or its dependencies. No
  `npm audit fix` and no override were applied.
- **Green baseline**: targeted `npm test -- src/__tests__/projects.spec.ts` ->
  `Test Files 1 passed (1)`, `Tests 9 passed (9)`. Whole suite -> `Test Files 3
  passed (3)`, `Tests 11 passed (11)`. `npm run type-check` and `npm run build`
  exited 0 (`✓ 11 modules transformed.`, `✓ built in 96ms`).
  `cd backend && ./mvnw test` -> `Tests run: 208, Failures: 0, Errors: 0,
  Skipped: 0`, `BUILD SUCCESS`. The same three frontend commands stayed green
  after the proxy was added.
- **M1**: from a clean tree and empty `git diff`, `const API_BASE_URL = '/api'`
  became `'/v1'`. The URL test went red with `expected "vi.fn()" to be called
  with arguments: [ '/api/projects' ]` and received `'/v1/projects'`;
  `1 failed | 8 passed`. Restored with `git restore --worktree
  frontend/src/services/projects.ts`, then `git diff` empty.
- **M2**: `'/api'` became `'http://127.0.0.1:8080/api'`. The same test went red
  with received `'http://127.0.0.1:8080/api/projects'`; `1 failed | 8 passed`.
  Restored, `git diff` empty.
- **M4**: the `Array.isArray(response.data)` guard was removed. The four
  non-array cases went red, for example `expected { error:
  'SECRET_UPSTREAM_DETAIL' } to be an instance of ProjectsUnavailableError`;
  `4 failed | 5 passed`. Restored, `git diff` empty.
- **M5**: `catch { throw new ProjectsUnavailableError() }` became
  `catch (error) { throw error }`. The three upstream error tests went red, for
  example `expected Error: network SECRET_UPSTREAM_DETAIL to be an instance of
  ProjectsUnavailableError`; `3 failed | 6 passed`. Restored, `git diff` empty.
- **M6**: the constructor became `constructor(cause?: unknown)` with
  `super('Unable to load projects', { cause })`, and the catch became
  `catch (error) { throw new ProjectsUnavailableError(error) }`. Seven error
  tests went red on `expect(projectsError.cause).toBeUndefined()`, showing the
  upstream error or the inner `ProjectsUnavailableError` as `cause`;
  `7 failed | 2 passed`. Restored, `git diff` empty.
- **M3 removed**: the initially planned identity mutation was dropped at the
  owner's request. The success test uses `toEqual`, so no copy-detection claim
  is made and no mutation was replayed for that row.

## Requirements

| Requirement | Test / command | Mutation | Status |
| --- | --- | --- | --- |
| Axios 1.21.1 is pinned in production dependencies | `npm ls axios --all`, `frontend/package.json`, `frontend/package-lock.json` | none | OBSERVE |
| Registry lookup returned 1.21.1 and the install used `--save-exact` | `npm view axios version`, `npm install axios@1.21.1 --save-exact` | none | OBSERVE |
| Publication date and 2026 advisories were checked by the owner | owner report, not replayed by the agent | none | LIMITE |
| Axios has four accepted direct dependencies | `frontend/package-lock.json` axios entry | none | OBSERVE |
| Audit stays at the 5 accepted moderate findings | `npm audit` | none | OBSERVE |
| `getProjects` calls GET `/api/projects`, relative and exact | `projects.spec.ts > requests GET /api/projects with a relative url` | M1, M2 | OBSERVE |
| A successful response is returned with the same content | `projects.spec.ts > returns the response array unchanged` | none, M3 removed | OBSERVE |
| HTTP 503 becomes the generic error | `projects.spec.ts > rejects with the generic error on 503` | M5, M6 | OBSERVE |
| HTTP 502 becomes the generic error | `projects.spec.ts > rejects with the generic error on 502` | M5, M6 | OBSERVE |
| A network failure becomes the generic error | `projects.spec.ts > rejects with the generic error on a network failure` | M5, M6 | OBSERVE |
| A non-array payload becomes the generic error, for object, string, null and undefined | `projects.spec.ts > rejects with the generic error when data is not an array` (4 cases) | M4, M6 | OBSERVE |
| The error is a `ProjectsUnavailableError` with exact message `Unable to load projects`, no `SECRET_UPSTREAM_DETAIL` and no `cause` | assertion helper called by the four error tests above | M5, M6 | OBSERVE |
| Tests make no real HTTP request | `vi.mock('axios')` in `projects.spec.ts`, `npm test` | none | DEDUIT |
| Dev proxy maps `/api` to `PORTFOLIO_BACKEND_URL`, default `http://127.0.0.1:8080` | `frontend/vite.config.ts`, `npm run type-check`, `npm run build` | none | LIMITE |
| Whole frontend suite and build pass | `npm test`, `npm run type-check`, `npm run build` | none | OBSERVE |
| Backend stays at 208 passing tests | `cd backend && ./mvnw test` | none | OBSERVE |

## LIMITE

- The dev proxy is configured and type-checks, but no dev server or backend was
  started in group A. Its runtime proof is deferred to the group C smoke test.
- The publication date and the advisory details were supplied by the owner and
  were not independently fetched by the agent.
- `vi.mock('axios')` prevents requests through axios; no socket-level probe was
  executed, hence `DEDUIT` rather than `OBSERVE`.
- The success row asserts content with `toEqual`, not object identity.

## POINT OUVERT

- The group C smoke test must prove that `GET /api/projects` reaches the local
  backend through Vite, without any real GitHub call.
- The decision notes for groups B and C remain open for their own sessions.
