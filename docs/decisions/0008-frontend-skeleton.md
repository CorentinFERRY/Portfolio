# 0008 - Frontend skeleton

Date: 2026-11-26
Status: Accepted

## Context

Slice 6 needs a minimal Vue 3 frontend skeleton before any visual identity,
backend integration, or CI work. The repository already has strict rules for
tests, security, and dependency control. The skeleton must be small, typed, and
provably buildable, without introducing unapproved dependencies or claiming
guarantees that were not verified.

## Decision

- The frontend is a Vue 3 application using Vite, Vitest, Vue Test Utils, and
  TypeScript. The Composition API and `<script setup>` are the convention for
  components that will have a script; `App.vue` does not have a script yet.
  Pinia, Vue Router, and Axios are not added in this slice because no shared
  state, routing, or backend call is needed yet.
- Node 26 is pinned with `frontend/.nvmrc` containing `26` and
  `frontend/package.json` declaring `"engines": { "node": ">=26" }`. Installation
  uses `npm ci`; `npm install` is reserved for adding an approved dependency.
- The create-vue base structure is kept, including the four tsconfig files and
  type packages it provides. `vite-plugin-vue-devtools` and `npm-run-all2` were
  removed because they are not required by this slice.
- Type checking uses `vue-tsc --build`. The root `tsconfig.json` has `"files": []`
  and contains only references, so without `--build` TypeScript would not check
  any source file. This is deduced from the behavior of TypeScript project
  references and was not executed as a dedicated proof. `npm run build` also runs
  `vue-tsc --build` before `vite build`, so a type error fails the build.
- Vitest is not sufficient for TypeScript type checking. Mutation proof M2
  (recorded in `docs/slice6-requirements.md`, Proofs section) showed that adding
  `const typedNumber: number = 'not a number'` in `App.spec.ts` made
  `vue-tsc --build` fail with `TS2322` at line 8 and exit code 2, while
  `vitest run` stayed green. The proof was then reverted.
- Tests live in `frontend/src/__tests__/`. The initial page is minimal:
  `index.html` declares `lang="fr"` and title `Portfolio`, and `App.vue` renders
  an `<h1>Portfolio</h1>`.
- `npm audit` reported 5 moderate vulnerabilities in the test-tooling chain
  (`@vue/test-utils`, `js-beautify`, `editorconfig`, `glob`, `minimatch`). They
  are accepted for this slice because they affect test tooling only, are absent
  from the production build output, and the reported issues require
  attacker-controlled patterns. No `npm audit fix` or override was applied. The
  audit will be rechecked in slice 13 and at each frontend dependency addition,
  with the `npm audit` output included in the plan.
- `@tsconfig/node24` is kept while the project runs under Node 26. This is a known
  limitation of the generated base configuration; it does not change the runtime
  requirement, which remains Node 26.

## Consequences

- The frontend has a reproducible, typed baseline with one passing component test
  and no runtime dependency beyond `vue`.
- Type safety depends on `vue-tsc --build`, not on Vitest alone. Future slices
  must keep type-checking in build and verification commands.
- The accepted npm audit vulnerabilities create a tracked follow-up: recheck in
  slice 13 and at each frontend dependency addition, with `npm audit` output in
  the plan.
- The `@tsconfig/node24` / Node 26 mismatch is visible technical debt; replacing
  it requires an explicit decision when a Node 26 tsconfig baseline is adopted.
- Deferred work is explicit: `v-html` guard and frontend CI job in slice 6b; Vite
  proxy, API calls, and Axios in slice 7; visual identity and design tokens in
  slice 12; Vue Router and Pinia when a slice needs them, with owner approval.
