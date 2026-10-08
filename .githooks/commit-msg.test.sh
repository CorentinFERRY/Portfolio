#!/bin/sh
#
# Tests for the commit message hook. Every case writes a message file in a
# temporary directory and runs the hook on it, so the script needs no
# repository, no hook configuration and no network:
#   sh .githooks/commit-msg.test.sh
#
# Exit status 0 when every case behaves as expected, 1 when a case fails,
# 2 when the test itself cannot run.

set -u

# Pin the locale so byte counts behave exactly as in the hook.
LC_ALL=C
export LC_ALL

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
hook=$script_dir/commit-msg

if [ ! -f "$hook" ] || [ ! -r "$hook" ]; then
	printf 'commit-msg.test.sh: hook not found: %s\n' "$hook" >&2
	exit 2
fi

workdir=$(mktemp -d) || exit 2
trap 'rm -rf "$workdir"' 0 1 2 3 15

message_file=$workdir/message
hook_out=$workdir/hook.out
hook_err=$workdir/hook.err

passed=0
failed=0
hook_status=0

abort() {
	printf 'commit-msg.test.sh: test infrastructure error: %s\n' "$1" >&2
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

run_hook() {
	if sh "$hook" "$message_file" >"$hook_out" 2>"$hook_err"; then
		hook_status=0
	else
		hook_status=$?
	fi
}

# accept <label>: the hook must accept the message file written beforehand.
accept() {
	run_hook
	if [ "$hook_status" -eq 0 ]; then
		passed=$((passed + 1))
	else
		failed=$((failed + 1))
		printf 'FAIL (expected exit 0, got %s): %s\n' "$hook_status" "$1" >&2
		sed 's/^/  hook: /' "$hook_err" >&2
	fi
}

# reject <label>: the hook must reject the message file written beforehand.
reject() {
	run_hook
	if [ "$hook_status" -ne 0 ]; then
		passed=$((passed + 1))
	else
		failed=$((failed + 1))
		printf 'FAIL (expected a non-zero exit): %s\n' "$1" >&2
	fi
}

# length_case <label> <expected exit 0|1> <subject prefix> <intended length>
#
# Builds <prefix> followed by filler characters so the whole subject has
# exactly <intended length> bytes. Aborts if the generated length differs
# from the intended one, then expects the hook to accept (0) or reject (1).
length_case() {
	intended=$4
	prefix_length=$(bytes "$3")
	filler_length=$((intended - prefix_length))
	if [ "$filler_length" -lt 0 ]; then
		abort "$1: intended length $intended is shorter than the prefix"
	fi
	subject=$3$(repeat "$filler_length" a)
	generated=$(bytes "$subject")
	if [ "$generated" -ne "$intended" ]; then
		abort "$1: intended $intended characters, generated $generated"
	fi
	printf '%s\n' "$subject" >"$message_file"
	if [ "$2" -eq 0 ]; then
		accept "$1"
	else
		reject "$1"
	fi
}

# --- Valid messages ---------------------------------------------------------

for type in feat fix docs style refactor perf test build ci chore revert; do
	printf '%s\n' "$type: add a thing" >"$message_file"
	accept "$type without a scope"
	printf '%s\n' "$type(api): add a thing" >"$message_file"
	accept "$type with a scope"
done

printf '%s\n' 'feat!: drop the old endpoint' >"$message_file"
accept 'exclamation mark after the type'

printf '%s\n' 'feat(api)!: drop the old endpoint' >"$message_file"
accept 'exclamation mark after the scope'

length_case 'subject of exactly 72 characters' 0 'feat: ' 72

printf '%s\n' 'docs: update  the readme  and  the agents' >"$message_file"
accept 'spaces inside the description'

# --- Invalid messages -------------------------------------------------------

printf '%s\n' 'wip: add a thing' >"$message_file"
reject 'unknown type'

printf '%s\n' 'feat add a thing' >"$message_file"
reject 'missing colon'

printf '%s\n' 'feat:add a thing' >"$message_file"
reject 'missing space after the colon'

printf '%s\n' 'feat(API): add a thing' >"$message_file"
reject 'uppercase scope'

printf '%s\n' 'feat: Add a thing' >"$message_file"
reject 'uppercase first letter of the description'

printf '%s\n' 'feat: add a thing.' >"$message_file"
reject 'trailing period'

printf '%s\n' 'feat: add a thing ' >"$message_file"
reject 'trailing space'

printf '%s\n' 'feat:  add a thing' >"$message_file"
reject 'several spaces after the colon'

printf '%s\n' 'feat: ' >"$message_file"
reject 'empty description'

length_case 'subject of 73 characters' 1 'feat: ' 73

printf '%s\n' "Merge branch 'main' into develop" >"$message_file"
reject 'default git merge message'

: >"$message_file"
reject 'empty file'

if sh "$hook" >"$hook_out" 2>"$hook_err"; then
	hook_status=0
else
	hook_status=$?
fi
if [ "$hook_status" -ne 0 ]; then
	passed=$((passed + 1))
else
	failed=$((failed + 1))
	printf 'FAIL (expected a non-zero exit): %s\n' 'no argument' >&2
fi

# --- Edge cases -------------------------------------------------------------

cr=$(printf '\r')
printf '%s\n' "feat: add a thing$cr" >"$message_file"
accept 'CRLF line endings'

printf '\n%s\n' 'feat: add a thing' >"$message_file"
reject 'leading blank line'

printf '%s\n' 'feat: add a thing' '' 'Explain the change in the body.' '' \
	'BREAKING CHANGE: the old endpoint is gone' >"$message_file"
accept 'body and footer ignored'

# --- Summary ----------------------------------------------------------------

printf 'commit-msg.test.sh: %s passed, %s failed\n' "$passed" "$failed"
if [ "$failed" -ne 0 ]; then
	exit 1
fi
exit 0
