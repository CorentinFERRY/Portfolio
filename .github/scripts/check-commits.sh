#!/bin/sh
#
# Checks commit messages and, for pull requests, the squashed title against
# the Conventional Commits rules of .githooks/commit-msg. The rules live in
# exactly one place: this script only decides which messages to feed the
# hook.
#
# Inputs, as environment variables because they come from the GitHub
# Actions event payload and are passed through env: in the workflow:
#   CHECK_BASE   first commit of the range to check (exclusive); exactly
#                40 hexadecimal characters, or the 40 zeroes a push that
#                creates a branch reports as the previous head
#   CHECK_HEAD   last commit of the range to check (inclusive); exactly
#                40 hexadecimal characters
#   PR_TITLE     pull request title, checked as "<title> (#<number>)"
#   PR_NUMBER    pull request number, digits only, required with PR_TITLE
#   COMMIT_MSG_HOOK  path to the hook (tests point it elsewhere)
#
# Exit status: 0 every message passes, 1 at least one violation,
# 2 usage or infrastructure error (missing or malformed input,
# unreadable commit).

set -u

# Pin byte counts and character classes to the C locale, as the hook does.
LC_ALL=C
export LC_ALL

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
hook=${COMMIT_MSG_HOOK:-$script_dir/../../.githooks/commit-msg}

usage() {
	printf 'usage: CHECK_BASE=<sha> CHECK_HEAD=<sha> [PR_TITLE=<title> PR_NUMBER=<number>] %s\n' "$0" >&2
}

# is_hex40 <revision>: succeeds when the revision is exactly 40
# hexadecimal characters, the shape of a full git object name.
is_hex40() {
	[ "${#1}" -eq 40 ] || return 1
	case $1 in
		*[!0-9a-fA-F]*) return 1 ;;
	esac
	return 0
}

base=${CHECK_BASE:-}
head=${CHECK_HEAD:-}
title=${PR_TITLE:-}
pr_number=${PR_NUMBER:-}

if [ -z "$base" ] || [ -z "$head" ]; then
	printf 'check-commits: CHECK_BASE and CHECK_HEAD are required\n' >&2
	usage
	exit 2
fi

if { [ -n "$title" ] && [ -z "$pr_number" ]; } ||
	{ [ -z "$title" ] && [ -n "$pr_number" ]; }; then
	printf 'check-commits: PR_TITLE and PR_NUMBER must be set together\n' >&2
	usage
	exit 2
fi

# The revisions and the number come from the event payload: reject any
# malformed value before a git command ever sees it.
if ! is_hex40 "$base"; then
	printf 'check-commits: CHECK_BASE must be exactly 40 hexadecimal characters\n' >&2
	exit 2
fi

if ! is_hex40 "$head"; then
	printf 'check-commits: CHECK_HEAD must be exactly 40 hexadecimal characters\n' >&2
	exit 2
fi

if [ -n "$title" ]; then
	case $pr_number in
		*[!0-9]*)
			printf 'check-commits: PR_NUMBER must contain digits only\n' >&2
			exit 2
			;;
	esac
fi

if [ ! -f "$hook" ] || [ ! -r "$hook" ]; then
	printf 'check-commits: hook not readable: %s\n' "$hook" >&2
	exit 2
fi

message_file=$(mktemp) || {
	printf 'check-commits: cannot create a temporary file\n' >&2
	exit 2
}
trap 'rm -f "$message_file"' 0 1 2 3 15

violations=0

# check_message <label>: run the hook on $message_file and record the result.
check_message() {
	if sh "$hook" "$message_file"; then
		return 0
	fi
	printf 'check-commits: %s rejected\n' "$1" >&2
	violations=1
}

# The push event reports before as 40 zeroes when a branch is created;
# there is no base to compare against, so only the pushed head is checked.
# The end-of-options marker keeps a revision from being read as an option;
# it exists since git 2.24. Failures are redirected so an unknown revision
# reads as an infrastructure error (exit 2) rather than as a violation.
zeroes=0000000000000000000000000000000000000000
if [ "$base" = "$zeroes" ]; then
	if ! commits=$(git rev-list --max-count=1 --end-of-options "$head" \
		2>/dev/null); then
		printf 'check-commits: cannot read revision %s\n' "$head" >&2
		exit 2
	fi
else
	if ! commits=$(git rev-list --end-of-options "$base..$head" \
		2>/dev/null); then
		printf 'check-commits: cannot list commits in %s..%s\n' \
			"$base" "$head" >&2
		exit 2
	fi
fi

for sha in $commits; do
	if ! git log -1 --format=%B "$sha" >"$message_file" 2>/dev/null; then
		printf 'check-commits: cannot read commit %s\n' "$sha" >&2
		exit 2
	fi
	check_message "commit $sha"
done

if [ -n "$title" ]; then
	# The subject GitHub writes after a squash merge: <title> (#<number>).
	printf '%s (#%s)\n' "$title" "$pr_number" >"$message_file"
	check_message "pull request title $title (#$pr_number)"
fi

if [ "$violations" -ne 0 ]; then
	printf 'check-commits: see AGENTS.md section 7\n' >&2
	exit 1
fi

exit 0
