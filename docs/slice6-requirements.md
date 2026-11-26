# Slice 6 requirements traceability

Date: 2026-11-26
Slice: 6 G3 (Vue skeleton documentation and startup proof)

## Rule

Every row names an executable command, test, file check, or observed runtime proof.
Statuses: `OBSERVE` = directly observed by command/test/runtime output during
slice 6 G3 step A, or by a G1/G2 proof recorded in the Proofs section below;
`DEDUIT` = inferred from observed artifacts but not directly executed as a
dedicated proof; `LIMITE` = observed with an explicit limitation or deferred
verification. No row is marked `OBSERVE` without a real output from slice 6 G3
step A or a proof recorded in this file.

## Proofs

- **G1 create-vue template verification**: performed before commit `9f16813 feat(frontend): add vue 3 typescript skeleton`, on the six files copied without modification (`tsconfig.json`, `tsconfig.app.json`, `tsconfig.node.json`, `tsconfig.vitest.json`, `vitest.config.ts`, `env.d.ts`). Copies of the create-vue model were compared by diff; the retained skeleton matches the selected base structure (four tsconfig files and type packages kept; `vite-plugin-vue-devtools` and `npm-run-all2` removed).
- **M1 (rendering)**: performed on commit `9f16813`, from a clean working tree. In `frontend/src/App.vue`, `<h1>Portfolio</h1>` was replaced by `<h1>Projets</h1>`. `cd frontend && npm test` failed red with `Expected: "Portfolio"` / `Received: "Projets"`. The file was restored and `git diff` was empty.
- **M2 (type-check covers tests)**: performed on commit `9f16813`, from a clean working tree. `const typedNumber: number = 'not a number'` was added in `frontend/src/__tests__/App.spec.ts`. `cd frontend && npm run type-check` (`vue-tsc --build`) failed with `TS2322` at line 8, `EXIT_CODE=2`, while `cd frontend && npm test` (`vitest run`) stayed green. The file was restored and `git diff` was empty.

## Requirements

| Requirement | Proof | Status |
| --- | --- | --- |
| Reproducible install | Step A: `cd frontend && npm ci` (registry announced `https://registry.npmjs.org/`), final output `added 138 packages, and audited 139 packages in 3s`; `5 moderate severity vulnerabilities`; `frontend/package-lock.json` present. | OBSERVE |
| Title renders | `frontend/src/App.vue` contains `<h1>Portfolio</h1>`; `frontend/src/__tests__/App.spec.ts` mounts `App` and asserts `wrapper.get('h1').text()` = `Portfolio`; M1 proof: changing to `Projets` made `npm test` red with `Expected: "Portfolio"` / `Received: "Projets"`, then restored with empty `git diff`; Step A: `cd frontend && npm test` -> `Test Files 1 passed (1)`, `Tests 1 passed (1)`, `EXIT_CODE=0`. | OBSERVE |
| Type-check covers tests | `frontend/tsconfig.vitest.json` includes `src/**/__tests__/*`; Step A: `cd frontend && npm run type-check` -> `vue-tsc --build`, `EXIT_CODE=0`; M2 proof: type fault in `App.spec.ts` made `vue-tsc --build` fail with `TS2322`, line 8, `EXIT_CODE=2`, while `vitest run` stayed green, then restored with empty `git diff`. | OBSERVE |
| Build produces output and `dist` is ignored | Step A: `cd frontend && npm run build` -> `vue-tsc --build && vite build`, `✓ 11 modules transformed.`, `dist/index.html 0.31 kB`, `dist/assets/index-DvvuOcb8.js 62.52 kB`, `✓ built in 96ms`, `EXIT_CODE=0`; `git check-ignore -v frontend/dist` -> `.gitignore:20:dist/	frontend/dist`; `git ls-files frontend/dist` -> no output. | OBSERVE |
| Dev server responds | Step A, single bounded script: announced URL `http://127.0.0.1:5173/` (port 5173), `./node_modules/.bin/vite --host 127.0.0.1 --port 5173 --strictPort`; `PID=58594` proven by `ps -p 58594 -o pid,args` -> `node ./node_modules/.bin/vite --host 127.0.0.1 --port 5173 --strictPort`; `READY` after reading log; single `curl` -> `HTTP_CODE=200`, `TITLE_PORTFOLIO_LINES=1`; stopped PID 58594 only -> `PROCESS_GONE`; `ss -ltnH "( sport = :5173 )"` empty -> `PORT_5173_FREE`. | OBSERVE |
| Preview server responds | Step A, single bounded script after build: announced URL `http://127.0.0.1:4173/` (port 4173), `./node_modules/.bin/vite preview --host 127.0.0.1 --port 4173 --strictPort` on existing `dist/`; `PID=58667` proven by `ps -p 58667 -o pid,args` -> `node ./node_modules/.bin/vite preview --host 127.0.0.1 --port 4173 --strictPort`; `READY`; single `curl` -> `HTTP_CODE=200`, `TITLE_PORTFOLIO_LINES=1`; stopped PID 58667 only -> `PROCESS_GONE`; `ss -ltnH "( sport = :4173 )"` empty -> `PORT_4173_FREE`. | OBSERVE |
| No runtime dependency other than `vue` | `frontend/package.json` `dependencies` contains only `"vue": "^3.5.42"`. | OBSERVE |
| Node pinned | `frontend/.nvmrc` contains `26`; `frontend/package.json` contains `"engines": { "node": ">=26" }`. | OBSERVE |
| Backend still at 208 tests | Step A: `cd backend && ./mvnw test` (exact command from AGENTS.md section 4, output filtered on `Tests run:|BUILD|FAILURE|ERROR`) -> `Tests run: 208, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`. | OBSERVE |

## Limits

- The dev/preview startup tests prove that the HTML page is served, not that JavaScript executes or that Vue mounts in a real browser; browser-level proof belongs to Playwright later.
- The `curl` requests in the startup scripts had no explicit maximum time.
- `frontend/tsconfig.vitest.json` includes `src/**/__tests__/*`, so it covers only files placed directly inside a `__tests__` directory, not files in subdirectories of `__tests__`.
- The ADR statement that the root `tsconfig.json` has `"files": []` and only references, so without `--build` TypeScript would not check source files, is deduced from TypeScript project-reference behavior and was not executed as a dedicated proof.
- `npm audit` reported 5 moderate vulnerabilities in test tooling (`@vue/test-utils`, `js-beautify`, `editorconfig`, `glob`, `minimatch`), accepted for this slice; recheck in slice 13 and at each frontend dependency addition, with `npm audit` output in the plan.
- `@tsconfig/node24` is kept while runtime is Node 26; replacing it requires an explicit decision.
