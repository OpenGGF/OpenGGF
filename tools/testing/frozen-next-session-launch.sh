#!/usr/bin/env bash
# Launches the frozen-next adapter through an authenticated frozen-develop harness.
set -euo pipefail

die() { printf 'frozen-next launcher: %s\n' "$*" >&2; exit 2; }
canonical_file() { [[ -f "$1" ]] || die "not a regular file: $1"; readlink -f -- "$1"; }
canonical_dir() { [[ -d "$1" ]] || die "not a directory: $1"; (CDPATH= cd -- "$1" && pwd -P); }
inside() { [[ "$1" == "$2"/* ]]; }

worktree= expected_head= harness_worktree= expected_harness_head= wrapper= coordinator= adapter=
while (($#)); do
    case "$1" in
        --worktree) worktree=$(canonical_dir "${2:-}"); shift 2 ;;
        --expected-head) expected_head=${2:-}; shift 2 ;;
        --harness-worktree) harness_worktree=$(canonical_dir "${2:-}"); shift 2 ;;
        --expected-harness-head) expected_harness_head=${2:-}; shift 2 ;;
        --wrapper) wrapper=$(canonical_file "${2:-}"); shift 2 ;;
        --coordinator) coordinator=$(canonical_file "${2:-}"); shift 2 ;;
        --adapter) adapter=$(canonical_file "${2:-}"); shift 2 ;;
        --) shift; break ;;
        *) die "unknown option: $1" ;;
    esac
done
(( $# > 0 )) || die "Maven command required after --"
[[ -n "$worktree$expected_head$harness_worktree$expected_harness_head$wrapper$coordinator$adapter" ]] \
    || die "all launcher options are required"

git -C "$worktree" symbolic-ref -q HEAD >/dev/null && die "worktree must be detached"
[[ "$(git -C "$worktree" rev-parse HEAD)" == "$expected_head" ]] || die "unexpected frozen-next HEAD"
[[ -z "$(git -C "$worktree" status --porcelain --untracked-files=all)" ]] || die "frozen-next worktree is not clean"
git -C "$harness_worktree" symbolic-ref -q HEAD >/dev/null && die "harness worktree must be detached"
[[ "$(git -C "$harness_worktree" rev-parse HEAD)" == "$expected_harness_head" ]] \
    || die "unexpected harness HEAD"
[[ -z "$(git -C "$harness_worktree" status --porcelain --untracked-files=all)" ]] \
    || die "harness worktree is not clean"
inside "$wrapper" "$harness_worktree" || die "wrapper is outside harness worktree"
inside "$coordinator" "$harness_worktree" || die "coordinator is outside harness worktree"
cmp -s "$wrapper" <(git -C "$harness_worktree" show "$expected_harness_head:tools/testing/test-session.sh") \
    || die "wrapper bytes differ from expected harness blob"
cmp -s "$coordinator" <(git -C "$harness_worktree" show "$expected_harness_head:tools/testing/TestSessionCoordinator.java") \
    || die "coordinator bytes differ from expected harness blob"

launcher=$(canonical_file "$0")
exclude=$(canonical_file "$(dirname -- "$launcher")/frozen-next-session.exclude")
old_inputs=${OPENGGF_RUNTIME_INPUTS:-}
new_inputs="$launcher:$exclude:$adapter:$wrapper:$coordinator"
export OPENGGF_RUNTIME_INPUTS="${old_inputs:+$old_inputs:}$new_inputs"

# This is process-local: inherited entries remain intact and no Git config is written.
config_count=${GIT_CONFIG_COUNT:-0}
[[ "$config_count" =~ ^[0-9]+$ ]] || die "GIT_CONFIG_COUNT is not numeric"
config_key="GIT_CONFIG_KEY_$config_count"
config_value="GIT_CONFIG_VALUE_$config_count"
printf -v "$config_key" '%s' core.excludesFile
printf -v "$config_value" '%s' "$exclude"
export "$config_key" "$config_value"
export GIT_CONFIG_COUNT=$((config_count + 1))

recover() {
    local output=$1 manifest run_id marker recorded_worktree recorded_link recorded_target linked actual_target
    manifest=$(printf '%s\n' "$output" | sed -n 's/.*manifest=\([^ ]*\).*/\1/p' | tail -1)
    [[ -n "$manifest" && -f "$manifest" ]] || return 0
    run_id=$(sed -n 's/.*"run_id": "\([^"]*\)".*/\1/p' "$manifest" | head -1)
    marker="$(dirname -- "$manifest")/diagnostics/frozen-next-session-recovery.env"
    [[ -n "$run_id" && -f "$marker" && ! -L "$marker" ]] || return 0
    recorded_worktree=$(sed -n 's/^worktree=//p' "$marker")
    recorded_link=$(sed -n 's/^target_link=//p' "$marker")
    recorded_target=$(sed -n 's/^target=//p' "$marker")
    [[ "$(sed -n 's/^run_id=//p' "$marker")" == "$run_id" ]] || return 0
    [[ "$recorded_worktree" == "$worktree" && "$recorded_link" == "$worktree/target" ]] || return 0
    [[ "$recorded_target" == "$OPENGGF_RECOVERY_BUILD_ROOT" ]] || return 0
    [[ -L "$recorded_link" ]] || return 0
    linked=$(readlink -- "$recorded_link")
    [[ "$linked" == "$recorded_target" ]] || return 0
    actual_target=$(readlink -f -- "$recorded_link")
    [[ "$actual_target" == "$recorded_target" ]] || return 0
    unlink -- "$recorded_link"
}

capture_file=
wrapper_pid=
finalized=0
finalize_status=0
finalize() {
    (( finalized == 0 )) || return "$finalize_status"
    finalized=1
    local output= cleanup_status=0
    if [[ -n "$capture_file" && -f "$capture_file" && ! -L "$capture_file" ]]; then
        output=$(<"$capture_file")
        OPENGGF_RECOVERY_BUILD_ROOT=
        manifest=$(printf '%s\n' "$output" | sed -n 's/.*manifest=\([^ ]*\).*/\1/p' | tail -1)
        if [[ -n "$manifest" && -f "$manifest" ]]; then
            OPENGGF_RECOVERY_BUILD_ROOT=$(sed -n 's/.*"build_root": "\([^"]*\)".*/\1/p' "$manifest" | head -1)
        fi
        recover "$output" || cleanup_status=$?
        if (( cleanup_status != 0 )); then
            printf 'frozen-next launcher: authenticated target cleanup failed: status=%s target=%s\n' \
                "$cleanup_status" "$worktree/target" >&2
        fi
        printf '%s\n' "$output"
        unlink -- "$capture_file" || true
    fi
    finalize_status=$cleanup_status
    return "$finalize_status"
}
on_exit() {
    local status=$? cleanup_status=0
    trap - EXIT
    finalize || cleanup_status=$?
    if (( status != 0 )); then
        exit "$status"
    fi
    exit "$cleanup_status"
}
on_signal() {
    local signal=$1 status=$2
    trap - INT TERM
    if [[ -n "$wrapper_pid" ]]; then
        kill -"$signal" "$wrapper_pid" 2>/dev/null || true
        wait "$wrapper_pid" 2>/dev/null || true
    fi
    exit "$status"
}

capture_file=$(mktemp "${TMPDIR:-/tmp}/openggf-frozen-next-launch.XXXXXX")
trap on_exit EXIT
trap 'on_signal INT 130' INT
trap 'on_signal TERM 143' TERM
set +e
(cd "$worktree" && exec "$wrapper" -- "$adapter" "$@") > "$capture_file" 2>&1 &
wrapper_pid=$!
wait "$wrapper_pid"
status=$?
wrapper_pid=
set -e
exit "$status"
