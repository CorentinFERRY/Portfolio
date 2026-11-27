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

## Slice 7 - Group B (ProjectCard)

Scope: one presentational `ProjectCard.vue` with no style. `App.vue`, the
projects service and dependency files are unchanged. No new dependency. The
`data-test` attributes are test hooks only; they add no styling or behaviour.

### Decisions

1. The repository link opens in a new tab with `target="_blank"` and carries
   both `noopener` and `noreferrer`. Homepage links use the same attributes.
   Tests: `ProjectCard.spec.ts > renders the name as a repository link with target blank and both rel tokens`
   and `ProjectCard.spec.ts > renders homepage as a link with target blank and both rel tokens`.
2. Nullable fields (`description`, `homepage`, `language`, `pushedAt`) render
   nothing: no `null` text, no label and no empty element. One test per field:
   `renders nothing for a null description`, `renders nothing for a null homepage`,
   `renders nothing for a null language` and `renders nothing for a null pushedAt`.
3. `id` is never rendered, neither in the text nor in the HTML. Test:
   `ProjectCard.spec.ts > never renders the project id`.
4. `pushedAt` is copied unchanged into a `<time>` element and its `datetime`
   attribute. Test: `ProjectCard.spec.ts > renders pushedAt unchanged in a time element with its datetime attribute`.
   Local formatting is a debt deferred to slice 12.
5. External text is rendered by interpolation only. A name and a description
   containing `<img src=x onerror=alert(1)>` stay literal, create no `img`
   element and create no `onerror` attribute. Test:
   `ProjectCard.spec.ts > escapes an img onerror payload in name and description`.
   The existing `no-v-html.spec.ts` guard stays green in the whole suite.
6. A URL is linked only after `new URL(value)` succeeds and `protocol` is
   `https:`. A rejected `htmlUrl` leaves the name as plain text; a rejected
   `homepage` is not rendered at all, exactly like a `null` homepage. Tests
   cover `javascript:`, `http:` and unparseable values for both fields:
   `renders the name as text when htmlUrl uses javascript:`,
   `renders the name as text when htmlUrl uses http:`,
   `renders the name as text when htmlUrl cannot be parsed` and
   `renders nothing for homepage when it is not a valid https URL` (3 cases).

### Status

| Requirement | Test | Status |
| --- | --- | --- |
| Repository and homepage links use `_blank` and both rel tokens | the two dedicated link tests above | OBSERVE |
| Null fields render nothing | the four dedicated null tests above | OBSERVE |
| `id` is absent | `never renders the project id` | OBSERVE |
| Raw `pushedAt` in text and `datetime` | `renders pushedAt unchanged in a time element with its datetime attribute` | OBSERVE |
| Escaped external text | `escapes an img onerror payload in name and description` | OBSERVE |
| HTTPS-only links and no rendering for invalid homepage | the four URL test names above | OBSERVE |

### LIMITE

- `pushedAt` formatting is deferred to slice 12; the component passes the ISO
  string through without any locale or timezone conversion.
- Mutation proofs are replayed only after the owner commits, from a clean tree
  with an empty `git diff`; no mutation result is claimed in this section.

## Slice 7 - Group C (project list in App)

Scope: `ProjectList.vue`, mounted by `App.vue` under the heading, loads the
projects through `getProjects()` and renders one `ProjectCard` per project. No
new dependency, no style, no retry button. Decisions are recorded in
`docs/decisions/0009-frontend-project-list.md`.

Test names are written `ProjectList.spec.ts > <name>` or `App.spec.ts > <name>`.
Component tests mock `../services/projects` (with `importOriginal`); `App.spec.ts`
stubs `ProjectList` through `global.stubs`. The existing heading assertion is
unchanged.

### Proofs

- **Red first**: `npm test` -> `Test Files 2 failed | 3 passed (5)`, `Tests 1
  failed | 27 passed (28)`. `ProjectList.spec.ts` failed on
  `Failed to resolve import "../components/ProjectList.vue"`, and
  `App.spec.ts > mounts the project list` failed with `expected false to be
  true`. The 27 existing tests stayed green.
- **Green**: `npm test` -> `Test Files 5 passed (5)`, `Tests 35 passed (35)`.
  `npm run type-check` exited 0. `npm run build` exited 0
  (`✓ 74 modules transformed.`). The backend was measured at the start of the
  group: `cd backend && ./mvnw test` -> `Tests run: 208, Failures: 0, Errors: 0,
  Skipped: 0`, `BUILD SUCCESS`; it was not modified.
- **Mutations**: after the owner's commit, one by one from a clean tree with
  empty `git status --short` and `git diff`, edited with the edit tool, only the
  concerned spec file run, restored with `git restore --worktree <path>`, then
  `git status --short` and `git diff` empty again. Test numbers: 1 loading, 2
  single request, 3 cards in order, 4 loading hidden after success, 5 empty, 6
  error, 7 secret, 8 App mounts the list.

| Id | Exact change | Expected red | Actual red | Main failure message |
| --- | --- | --- | --- | --- |
| M1 | loading `<p>` made unconditional, error `v-else-if` turned into `v-if` | 1, 4, 5, 6 | 1, 4, 5, 6 | test 4: `expected 'Chargement des projets…gammaalphabeta' not to contain 'Chargement des projets…'` |
| M2 | `catch (error)` stores `(error as Error).message` and the alert shows it instead of the constant text | 6, 7 | 6, 7 | `expected 'SECRET_UPSTREAM_DETAIL' to be 'Impossible de charger les projets pou…'` |
| M3 | success state set even for an empty array | 5 | 5 | `Unable to get [role="status"] within: <ul></ul>` |
| M4 | `catch` sets `{ kind: 'loading' }` | 6, 7 | 6, 7 | `Unable to get [role="alert"] within: <p role="status">Chargement des projets…</p>` |
| M5 | `role="alert"` replaced by `role="status"` on the error | 6, 7 | 6, 7 | `Unable to get [role="alert"] within: <p role="status">Impossible de charger les projets pour le moment.</p>` |
| M6 | an extra `await getProjects()` before the existing call | 2 | 2 | `expected "vi.fn()" to be called 1 times, but got 2 times` |
| M7 | `projects: [...projects].reverse()` | 3 | 3 | `expected [ { id: 2, name: 'beta', …(5) }, …(2) ] to deeply equal [ Array(3) ]` |
| M8 | `<ProjectList />` removed from `App.vue` | 8 | 8 | `expected false to be true` (heading test still green) |

M1 was first planned to turn only test 4 red. It also turned tests 1, 5 and 6
red: tests 5 and 6 assert that the loading text is absent, and breaking the
`v-if` chain renders the `<ul v-else>` during loading. The owner accepted the
wider expected set, and the same reasoning set the expectations of M2, M4 and M5
before they were played. Every actual set matched its expected set.

- **Smoke test**: the owner started the backend from their own terminal with
  their `.env`; the agent did not touch it. `ss -ltn 'sport = :8080'` showed
  `LISTEN`. In one bounded command the agent started
  `./node_modules/.bin/vite --host 127.0.0.1 --port 5173 --strictPort`, captured
  its PID and checked it with `ps -p`, then: `http://127.0.0.1:5173/` ->
  `200`; `http://127.0.0.1:5173/api/projects` -> `200`, an array of 1 element
  (body not printed). `kill` then `wait` returned 143 (SIGTERM), the process
  was gone and `ss -ltn 'sport = :5173'` listed no socket. This request made the
  owner's backend call the real GitHub API, as decided by the owner for this
  group.
- **Browser check (manual, by the owner)**: the page showed 1 project card; the
  Network tab showed one `GET /api/projects` request on port 5173 and no request
  to `api.github.com`; the console showed no error. Status `MANUAL`: a check
  performed by the owner and not replayed by the agent.

### Requirements

| Requirement | Test / command | Mutation | Status |
| --- | --- | --- | --- |
| Loading status is shown while the request is pending, with no list and no alert | `ProjectList.spec.ts > shows the loading status while projects are pending` | M1 | OBSERVE |
| Projects are requested exactly once on mount | `ProjectList.spec.ts > requests projects exactly once on mount` | M6 | OBSERVE |
| One `ProjectCard` per project, in API order, with its `project` prop | `ProjectList.spec.ts > renders one ProjectCard per project in api order with its project prop` | M7 | OBSERVE |
| Loading status and alert are gone after a successful load | `ProjectList.spec.ts > hides the loading status after a successful load` | M1 | OBSERVE |
| Empty array shows the empty message, with no list, no alert and no loading text | `ProjectList.spec.ts > shows the empty message and no list for an empty array` | M1, M3 | OBSERVE |
| A failure shows the generic alert, with no list, no status and no loading text | `ProjectList.spec.ts > shows the generic alert and no list when loading fails` | M1, M2, M4, M5 | OBSERVE |
| The rejection message never reaches the rendered HTML | `ProjectList.spec.ts > never displays the error message from the rejection` | M2, M4, M5 | OBSERVE |
| `App` mounts `ProjectList`, stubbed in its test | `App.spec.ts > mounts the project list` | M8 | OBSERVE |
| The heading is still rendered | `App.spec.ts > renders the portfolio heading` | none in this group | OBSERVE |
| Vite serves the page and proxies `/api/projects` to the backend | smoke test above | none | OBSERVE |
| The page renders the projects in a browser, calls only `/api/projects` on port 5173, never `api.github.com`, with no console error | manual browser check by the owner, described above | none | MANUAL |

### LIMITE

- The smoke test returned a single project, so it proves the proxy path, not the
  ordering or several cards; those are proven by the tests above.
- A request still pending when `ProjectList` unmounts is not cancelled, and no
  test covers it.
- The group A open point asked for a smoke test without any real GitHub call; the
  owner changed this for group C and the real API was used.
