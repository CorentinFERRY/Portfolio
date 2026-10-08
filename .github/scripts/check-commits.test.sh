#!/bin/sh
#
# Tests for .github/scripts/check-commits.sh. The checker runs git commands
# in the current directory, so the tests build a throwaway repository with
# git init under /tmp, with no hook enabled, and run the checker inside it:
#   sh .github/scripts/check-commits.test.sh
#
# The checker reads CHECK_BASE and CHECK_HEAD (the commit range) and, for
# pull requests, PR_TITLE and PR_NUMBER. On pull requests it checks the
# subject GitHub writes after a squash merge: <title> (#<number>).
#
# Exit status 0 when every case behaves as expected, 1 when a case fails,
# 2 when the test itself cannot run.

set -u

# Pin the locale so byte counts behave exactly as in the checker and hook.
LC_ALL=C
export LC_ALL

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
checker=$script_dir/check-commits.sh

if [ ! -f "$checker" ] || [ ! -r "$checker" ]; then
	printf 'check-commits.test.sh: checker not found: %s\n' "$checker" >&2
	exit 2
fi

if ! command -v git >/dev/null 2>&1; then
	printf 'check-commits.test.sh: git is required\n' >&2
	exit 2
fi

workdir=$(mktemp -d) || exit 2
trap 'rm -rf "$workdir"' 0 1 2 3 15

repo=$workdir/repo
checker_out=$workdir/checker.out

passed=0
failed=0
checker_status=0

abort() {
	printf 'check-commits.test.sh: test infrastructure error: %s\n' "$1" >&2
	exit 2
}

# repeat <count> <character>: print the character count times.
repeat() {
	i=0
	out=''
	while [ "$i" -lt "$1" ]; do
		out=$out$2
		i=$((i + 1))
	done
	printf '%s' "$out"
}

# bytes <text>: print the byte length of the text.
bytes() {
	printf '%s' "$1" | wc -c | tr -d ' '
}

# The sandbox must never run a hook, whatever the machine's global
# configuration says, and commits must never need a signing key.
init_repo() {
	git -c init.defaultBranch=main init -q "$repo" || abort 'git init failed'
	mkdir "$workdir/no-hooks"
	git -C "$repo" config core.hooksPath "$workdir/no-hooks"
	git -C "$repo" config commit.gpgsign false
	git -C "$repo" config user.name 'commit check sandbox'
	git -C "$repo" config user.email 'sandbox@example.invalid'
}

commit() {
	git -C "$repo" commit -q --allow-empty -m "$1" ||
		abort "cannot create commit: $1"
}

head_sha() {
	git -C "$repo" rev-parse HEAD
}

# run_checker [NAME=value ...]: run the checker inside the sandbox and
# capture its combined output and exit status. The checker's inputs are
# removed from the inherited environment first, so every case depends only
# on the arguments given here and never on the caller's environment.
run_checker() {
	if (cd "$repo" && env -u CHECK_BASE -u CHECK_HEAD -u PR_TITLE \
		-u PR_NUMBER -u COMMIT_MSG_HOOK "$@" sh "$checker") \
		>"$checker_out" 2>&1; then
		checker_status=0
	else
		checker_status=$?
	fi
}

# expect_status <wanted exit> <label>
expect_status() {
	if [ "$checker_status" -eq "$1" ]; then
		passed=$((passed + 1))
	else
		failed=$((failed + 1))
		printf 'FAIL (expected exit %s, got %s): %s\n' \
			"$1" "$checker_status" "$2" >&2
		sed 's/^/  checker: /' "$checker_out" >&2
	fi
}

# expect_output_contains <fixed string> <label>
expect_output_contains() {
	if grep -qF -- "$1" "$checker_out"; then
		passed=$((passed + 1))
	else
		failed=$((failed + 1))
		printf 'FAIL (output does not contain "%s"): %s\n' "$1" "$2" >&2
		sed 's/^/  checker: /' "$checker_out" >&2
	fi
}

# --- Cases ------------------------------------------------------------------

init_repo

commit 'chore(repo): base'
base=$(head_sha)

# Case 1: a range of all valid commits is accepted.
commit 'feat: add the alpha'
commit 'fix(api): repair the beta'
commit 'docs: update the gamma'
run_checker "CHECK_BASE=$base" "CHECK_HEAD=$(head_sha)"
expect_status 0 'a range of all valid commits'

# Case 2: one invalid commit fails the range and is named in the output.
commit 'this message is not conventional'
invalid=$(head_sha)
run_checker "CHECK_BASE=$base" "CHECK_HEAD=$invalid"
expect_status 1 'a range containing one invalid commit'
expect_output_contains "$invalid" 'the offending commit is named'

# Case 3: a title too long once the suffix is added is rejected. The
# suffix is exactly what GitHub writes after a squash merge: " (#<n>)".
# The title is built from a computed length and the result is verified.
suffix=' (#7)'
title_prefix='ci: '
intended=73
title_length=$((intended - $(bytes "$suffix")))
filler_length=$((title_length - $(bytes "$title_prefix")))
if [ "$filler_length" -lt 0 ]; then
	abort 'long title: intended length leaves no room for filler'
fi
long_title=$title_prefix$(repeat "$filler_length" a)
generated=$(bytes "$long_title$suffix")
if [ "$generated" -ne "$intended" ]; then
	abort "long title: intended $intended characters, generated $generated"
fi
run_checker "CHECK_BASE=$base" "CHECK_HEAD=$base" \
	"PR_TITLE=$long_title" "PR_NUMBER=7"
expect_status 1 'a title of 73 characters once the suffix is added'
expect_output_contains '73 characters long' 'the length violation names the length'

# Case 4: a valid title that fits exactly is accepted (72 with the suffix).
intended=72
title_length=$((intended - $(bytes "$suffix")))
filler_length=$((title_length - $(bytes "$title_prefix")))
if [ "$filler_length" -lt 0 ]; then
	abort 'valid title: intended length leaves no room for filler'
fi
valid_title=$title_prefix$(repeat "$filler_length" a)
generated=$(bytes "$valid_title$suffix")
if [ "$generated" -ne "$intended" ]; then
	abort "valid title: intended $intended characters, generated $generated"
fi
run_checker "CHECK_BASE=$base" "CHECK_HEAD=$base" \
	"PR_TITLE=$valid_title" "PR_NUMBER=7"
expect_status 0 'a valid title of exactly 72 characters with the suffix'

# Case 5: merge commits are checked like any other commit. A merge with
# the message GitHub generates fails the range and is named in the output.
git -C "$repo" checkout -q -b feature-case "$base"
commit 'feat: add the feature work'
git -C "$repo" checkout -q -b merge-case "$base"
git -C "$repo" merge -q --no-ff \
	-m 'Merge pull request #7 from sandbox/feature-case' feature-case ||
	abort 'the non-conforming merge could not be created'
bad_merge=$(head_sha)
run_checker "CHECK_BASE=$base" "CHECK_HEAD=$bad_merge"
expect_status 1 'a range containing a non-conforming merge commit'
expect_output_contains "$bad_merge" 'the merge commit is named'

# A merge with a conforming message is accepted.
git -C "$repo" checkout -q -b side-ok "$base"
commit 'feat: add more feature work'
git -C "$repo" checkout -q -b merge-ok "$base"
commit 'docs: adjust the documentation'
git -C "$repo" merge -q --no-ff \
	-m 'chore(repo): merge branch side-ok' side-ok ||
	abort 'the conforming merge could not be created'
good_merge=$(head_sha)
run_checker "CHECK_BASE=$base" "CHECK_HEAD=$good_merge"
expect_status 0 'a range containing a conforming merge commit'

# --- Input validation and the zero base -------------------------------------

# Case 6: no inputs at all.
run_checker
expect_status 2 'no inputs'
expect_output_contains 'CHECK_BASE and CHECK_HEAD are required' \
	'the reason for the missing inputs'

# Case 7: a title and a number must be given together.
run_checker "CHECK_BASE=$base" "CHECK_HEAD=$(head_sha)" \
	'PR_TITLE=feat: add a thing'
expect_status 2 'PR_TITLE without PR_NUMBER'
expect_output_contains 'must be set together' \
	'the reason for a title without a number'

run_checker "CHECK_BASE=$base" "CHECK_HEAD=$(head_sha)" 'PR_NUMBER=7'
expect_status 2 'PR_NUMBER without PR_TITLE'
expect_output_contains 'must be set together' \
	'the reason for a number without a title'

# Case 8: a revision that is not hexadecimal, on both sides. Forty z's
# keep the hexadecimal rule separate from the length rule.
not_hex=$(repeat 40 z)
run_checker "CHECK_BASE=$not_hex" "CHECK_HEAD=$(head_sha)"
expect_status 2 'CHECK_BASE that is not hexadecimal'
expect_output_contains 'CHECK_BASE must be exactly 40 hexadecimal characters' \
	'the reason for a non hexadecimal CHECK_BASE'

run_checker "CHECK_BASE=$base" "CHECK_HEAD=$not_hex"
expect_status 2 'CHECK_HEAD that is not hexadecimal'
expect_output_contains 'CHECK_HEAD must be exactly 40 hexadecimal characters' \
	'the reason for a non hexadecimal CHECK_HEAD'

# Case 9: well formed but unknown revision.
run_checker 'CHECK_BASE=deadbeefdeadbeefdeadbeefdeadbeefdeadbeef' \
	"CHECK_HEAD=$(head_sha)"
expect_status 2 'well formed but unknown revision'
expect_output_contains 'cannot list commits' \
	'the reason for the unknown revision'

# Case 10: a non numeric PR_NUMBER.
run_checker "CHECK_BASE=$base" "CHECK_HEAD=$(head_sha)" \
	'PR_TITLE=feat: add a thing' 'PR_NUMBER=seven'
expect_status 2 'non numeric PR_NUMBER'
expect_output_contains 'digits only' 'the reason for a non numeric PR_NUMBER'

# Case 11: with a zero base only the head is checked. The head's history
# contains the invalid commit of case 2, so checking the whole history
# would fail.
zeroes=0000000000000000000000000000000000000000
git -C "$repo" checkout -q -b zero-base-case "$invalid"
commit 'docs: continue the work'
run_checker "CHECK_BASE=$zeroes" "CHECK_HEAD=$(head_sha)"
expect_status 0 'zero base: only the head is checked'

# Case 11b: with a zero base an invalid head still fails the check and is
# named in the output. Case 11 alone would also pass if the checker
# checked nothing at all.
run_checker "CHECK_BASE=$zeroes" "CHECK_HEAD=$invalid"
expect_status 1 'zero base: an invalid head is rejected'
expect_output_contains "$invalid" 'the invalid head is named'

# --- Summary ----------------------------------------------------------------

printf 'check-commits.test.sh: %s passed, %s failed\n' "$passed" "$failed"
if [ "$failed" -ne 0 ]; then
	exit 1
fi
exit 0
