# 0009 - Frontend project list

Date: 2026-11-27
Status: Accepted

## Context

Slice 7 delivered the typed projects service (group A) and the escaped
`ProjectCard` component (group B), but nothing connected them: the public page
showed only its heading. Group C needs to load the selected projects when the
page opens and show the result without leaking technical details.

## Decision

- `ProjectList.vue` loads and holds the list; `ProjectCard` stays a component
  that only renders its prop. `App.vue` only mounts `ProjectList` under its
  heading. The HTTP call stays in `services/projects.ts`, and the component never
  imports axios. Pinia is not added: only one component reads this state, and
  AGENTS.md section 10 keeps stores for shared state.
- `getProjects()` is called once, in `onMounted`. A top-level `await` in
  `<script setup>` was not used because it would require a `<Suspense>` parent.
- The four states (loading, error, empty, success) are one discriminated union
  stored in a single `ref`. Only the success variant carries the projects, so no
  combination such as "error with a list" can be represented or rendered.
- The texts are constant and in French, matching `lang="fr"`: loading
  `Chargement des projets…` with `role="status"`, error
  `Impossible de charger les projets pour le moment.` with `role="alert"`, empty
  `Aucun projet publié pour le moment.` with `role="status"`. The `catch` does not
  bind the error, so no message from it can reach the page.
- Success renders a `<ul>` with one `<li :key="project.id">` per project, in the
  order of the API. `id` is the GitHub repository id, already deduplicated by the
  backend.
- Mock boundary: component tests mock our own `../services/projects` module, with
  `importOriginal` so the real `ProjectsUnavailableError` class is kept. Axios is
  mocked only in the service tests. `App.spec.ts` stubs `ProjectList` through
  `global.stubs`, so mounting `App` never starts a request, and a test proves the
  stub is the one rendered.

## Consequences

- The proxy and the backend integration have a runtime proof only through the
  group C smoke test, which used the real GitHub API through the owner's backend;
  no automated test covers that path.
- A failed load is final until the page is reloaded: there is no retry button.
- A request still pending when the component unmounts is not cancelled; the
  late state update only touches a component that is gone. This is deduced from
  Vue's behaviour and is not covered by a test.
- `pushedAt` formatting remains deferred to slice 12, as recorded for group B.
