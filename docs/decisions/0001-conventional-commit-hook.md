# 0001. Enforce commit messages with a POSIX shell hook

Date: 2026-10-05
Status: accepted

## Context

AGENTS.md section 7 requires every commit message to follow Conventional Commits
1.0, checked by `.githooks/commit-msg` and, once CI exists, by CI as well. The
check has to run on every machine that clones the repository, including a
contributor who only wants to commit and has neither Node nor the JDK installed.

## Decision

The check is a single POSIX `sh` script using only `sed`, `tr`, `grep` and `wc`.
It is enabled per clone with `git config core.hooksPath .githooks`, and it takes
the path of the proposed message file, so the same script validates a message
outside Git: `sh .githooks/commit-msg <file>`.

## Why

The hook is the one piece of tooling that must work before any toolchain is
installed. A shell script depends on nothing beyond a POSIX shell and `sed`,
`tr`, `grep` and `wc`, which are available wherever Git is normally installed and
are bundled with Git for Windows. It therefore behaves the same on a laptop and
in CI, and it pins its own locale (`LC_ALL=C`) so character ranges and byte
counts do not drift between machines.

Alternatives rejected:

- commitlint: accurate, but it would make a backend-only contributor install
  Node just to be allowed to commit.
- A framework such as pre-commit: adds a Python dependency and a second config
  file for one rule.
- CI-only validation: too late, the offending commit already exists and would
  have to be rewritten.

## Consequences

- `core.hooksPath` is local configuration, so every clone must run that one-line
  command. The README documents it as a setup step.
- Pointing `core.hooksPath` at `.githooks` disables `.git/hooks` for that clone.
- The enforced rules are the rules written in AGENTS.md section 7. Changing one
  means changing both files, which is why the script states the rules and not a
  private variant of them.
- Only the subject is validated. AGENTS.md section 7 sets no rule on the body or
  the footers, so a `BREAKING CHANGE:` footer without a `!` in the subject is
  accepted.
- Git generates message text that the rules reject, such as the default
  `Merge branch ...`. This is intentional: AGENTS.md forbids `--no-verify`, so
  the compliant procedure is documented instead of being whitelisted.
- A local hook can be bypassed, or never enabled in a clone at all. The CI check
  planned in AGENTS.md section 7 is therefore the enforcing gate, and the hook
  gives early feedback before the commit leaves the machine.
