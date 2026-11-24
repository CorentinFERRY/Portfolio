# 0006 - Selection persistence

Date: 2026-11-24
Status: Accepted

## Context
The admin selection of published GitHub repositories must survive a local backend
restart. Early slices use H2 and PostgreSQL is planned later. The selection is
small and administered by one user, so simplicity is preferred over a migration
tool or a general-purpose ordering model.

## Decision
- The `repository_selection` table has one column, `github_id BIGINT NOT NULL`,
  used as the single-column primary key. The stable numeric GitHub id is the
  only persisted fact: no repository name and no display order are stored.
  `list()` sorts the ids in memory before returning them.
- `schema.sql` creates the table with `CREATE TABLE IF NOT EXISTS`, and
  `spring.jpa.hibernate.ddl-auto=validate` checks the entity mapping at startup.
  Flyway is deferred to the PostgreSQL migration.
- `spring.sql.init.mode=always` and `schema.sql` are temporary. They would also
  run against PostgreSQL, so they must be replaced when the database changes.
- `spring.jpa.open-in-view: false` is set because this is a REST API with no
  server-side view and no lazy loading to defer into a web layer.
- H2 stays in `runtime` scope, so it is also inside the production jar until
  PostgreSQL is introduced. The test profile uses in-memory H2, and the H2
  console is disabled.

## Limits assumed
- `add` uses `existsById` followed by `save`. Two concurrent additions of the
  same id can both pass the check and raise a primary-key violation. This is
  accepted for single-admin use; the error should be translated to HTTP 409 if
  concurrent administration becomes a requirement.
- `list` sorts in memory and has no pagination. This is accepted for a small
  administered selection; a repository-derived ordered query can replace it if
  the list grows.

## PROUVÉ

- Schema creation and mapping: `PersistenceConfigurationTest#schemaCreatesRepositorySelectionTable`,
  `RepositorySelectionRepositoryTest#savesSelectionAndReadsItBack`.
- Constraints: `RepositorySelectionRepositoryTest#duplicateGithubIdViolatesPrimaryKey`
  and `RepositorySelectionRepositoryTest#nullGithubIdViolatesNotNullConstraint`.
- No-op removal and input validation: `RepositorySelectionServiceTest#removingAbsentGithubIdIsNoOp`
  and `RepositorySelectionServiceTest#addRejectsNonPositiveGithubId`.
- Ordering: `RepositorySelectionServiceOrderingTest#listSortsByGithubIdAscending`
  uses Mockito without Spring and was replayed successfully after mutation M2
  removed the sort. `RepositorySelectionServiceTest#listIsSortedByGithubIdAscending`
  checks the end-to-end contract, but is not the proof of sorting on its own.
- `ddl-auto=validate` was checked in both directions by mutations M3 and M4:
  renaming `github_id` in `schema.sql` and renaming the `@Column` in the entity
  made context startup fail with schema validation. All repository tests then
  failed together; this was a context startup failure, not a clean red in one
  named method.

## NON PROUVÉ - reserves

- M2 removed `NOT NULL` and the `PRIMARY KEY` together because the primary key
  implies non-nullability under H2. It proves the absence of any non-null
  protection, not `NOT NULL` alone. `github_id` is protected by both `NOT NULL`
  and the primary key.
- The first ordering test, `RepositorySelectionServiceTest#listIsSortedByGithubIdAscending`,
  passed by accident under H2 primary-key order before the Mockito test was
  added. The proof of sorting is
  `RepositorySelectionServiceOrderingTest#listSortsByGithubIdAscending`.
- M4 removed `existsById` and `RepositorySelectionServiceTest#addingSameGithubIdTwiceKeepsOneRow`
  stayed green. Sequential idempotence comes from `save` merging by id and from
  the primary key, not from `existsById`.
- When validation is removed, `RepositorySelectionServiceTest#addRejectsNonPositiveGithubId`
  fails on the expected exception, but its `assertEquals(0, repository.count())`
  is not reached: `save` writes the row, `schema.sql` has no `CHECK > 0`, and
  the `@DataJpaTest` transaction rolls the row back. The test proves the
  exception, not database-level rejection in production.
- The `@Transactional` annotations on the service are not proven by
  `RepositorySelectionServiceTest`: those tests run inside the transaction
  managed by `@DataJpaTest`.

## Manual jar smoke test

This is a manual verification, not a repository test. Two successive starts of
the packaged jar were observed, one process at a time, with the PID captured,
that PID killed and waited for, and the port checked free between the starts.
No `ERROR` line appeared in the filtered startup output. `GET /api/health`
returned HTTP 200 on both starts, and `backend/data/portfolio.mv.db` was present
after each stop. Git ignores that path through the `*.mv.db` rule.

The file content was not read. The presence of `repository_selection` is deduced
from successful `ddl-auto=validate`, not read from the file. The second start did
not fail on an existing file while `spring.sql.init.mode=always` and
`CREATE TABLE IF NOT EXISTS` are configured; this is consistent with an
idempotent schema replay, but the script execution was not directly observed in
the filtered output.

No selection was written through the jar because no endpoint exposes that
operation. Survival of a selection across a restart is therefore NOT proven:
only startup on a file-backed URL and the non-failing second startup were
observed.
