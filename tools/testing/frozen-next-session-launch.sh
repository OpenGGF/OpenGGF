#!/usr/bin/env bash
# Launches the frozen-next adapter through an authenticated frozen-develop harness.
set -euo pipefail

die() { printf 'frozen-next launcher: %s\n' "$*" >&2; exit 2; }
canonical_file() { [[ -f "$1" ]] || die "not a regular file: $1"; readlink -f -- "$1"; }
canonical_dir() { [[ -d "$1" ]] || die "not a directory: $1"; (CDPATH= cd -- "$1" && pwd -P); }
inside() { [[ "$1" == "$2"/* ]]; }
sha256() { sha256sum -- "$1" | awk '{print $1}'; }
byte_length() { wc -c < "$1" | tr -d '[:space:]'; }
lstat_kind() { stat -c '%F' -- "$1" 2>/dev/null; }
lstat_device() { stat -c '%d' -- "$1" 2>/dev/null; }
lstat_inode() { stat -c '%i' -- "$1" 2>/dev/null; }
process_start() { sed 's/^[^)]*) //' "/proc/$1/stat" 2>/dev/null | awk '{print $20}'; }
directory_empty() {
    [[ -d "$1" && ! -L "$1" ]] || return 1
    [[ -z "$(find "$1" -mindepth 1 -maxdepth 1 -print -quit 2>/dev/null)" ]]
}
namespace_holders() {
    local expected=$1 link inode pid
    for link in /proc/[0-9]*/ns/mnt; do
        inode=$(stat -Lc '%i' -- "$link" 2>/dev/null) || continue
        [[ "$inode" == "$expected" ]] || continue
        pid=${link#/proc/}
        printf '%s\n' "${pid%%/*}"
    done
}
wait_namespace_gone() {
    local pid=$1 start=$2 namespace=$3 attempt current_start holders
    for ((attempt = 0; attempt < 200; attempt++)); do
        current_start=$(process_start "$pid" || true)
        holders=$(namespace_holders "$namespace")
        if [[ "$current_start" != "$start" && -z "$holders" ]]; then return 0; fi
        sleep 0.05
    done
    return 1
}
marker_value() { sed -n "s/^$2=//p" "$1"; }
manifest_value() { sed -n "s/.*\"$2\": \"\([^\"]*\)\".*/\1/p" "$1" | head -1; }
authenticate_tripwire() {
    local diagnostics=$1 run_id=$2 report=$3 trigger evidence reason child adapter_status mode
    local report_hash report_length current_hash current_length
    trigger="$diagnostics/frozen-next-safety-failure.env"
    evidence="$diagnostics/frozen-next-identity-tripwire.env"
    if [[ ! -e "$trigger" && ! -L "$trigger" && ! -e "$evidence" && ! -L "$evidence" ]]; then
        return 0
    fi
    if [[ ! -f "$trigger" || -L "$trigger" || ! -f "$evidence" || -L "$evidence" ]]; then
        printf 'frozen-next launcher: authenticated identity tripwire evidence is incomplete\n' >&2
        return 77
    fi
    [[ "$(marker_value "$trigger" run_id)" == "$run_id" \
        && "$(marker_value "$evidence" run_id)" == "$run_id" ]] || return 77
    reason=$(marker_value "$trigger" reason)
    child=$(marker_value "$trigger" child_status)
    adapter_status=$(marker_value "$trigger" adapter_status)
    [[ "$reason" == "$(marker_value "$evidence" reason)" \
        && "$child" == "$(marker_value "$evidence" child_status)" \
        && "$adapter_status" == "$(marker_value "$evidence" adapter_status)" \
        && "$child" =~ ^[0-9]+$ && "$adapter_status" =~ ^[0-9]+$ ]] || return 77
    case "$reason:$adapter_status" in
        namespace-teardown:75|target-cleanup:73|target-cleanup:76) ;;
        *)
            printf 'frozen-next launcher: rejected unrelated identity tripwire trigger: reason=%s status=%s\n' \
                "$reason" "$adapter_status" >&2
            return 77
            ;;
    esac
    mode=$(marker_value "$evidence" mode)
    report_hash=$(marker_value "$evidence" report_hash)
    report_length=$(marker_value "$evidence" report_length)
    [[ "$mode" == deterministic-marker || "$mode" == existing-dirty-report ]] || return 77
    [[ "$report_hash" =~ ^[0-9a-f]{64}$ && "$report_length" =~ ^[0-9]+$ \
        && -f "$report" && ! -L "$report" ]] || return 77
    current_hash=$(sha256 "$report")
    current_length=$(byte_length "$report")
    [[ "$current_hash" == "$report_hash" && "$current_length" == "$report_length" ]] || return 77
    printf 'frozen-next launcher: authenticated identity tripwire: reason=%s child=%s adapter=%s mode=%s\n' \
        "$reason" "$child" "$adapter_status" "$mode" >&2
}
atomic_restore_report() {
    local archive=$1 destination=$2 expected_hash=$3 expected_length=$4 temporary
    [[ -f "$archive" && ! -L "$archive" && -f "$destination" && ! -L "$destination" ]] || return 74
    [[ "$(sha256 "$archive")" == "$expected_hash" && "$(byte_length "$archive")" == "$expected_length" ]] || return 74
    temporary=$(mktemp "$(dirname -- "$destination")/.rewind-round-trip-gaps.outer-restore.XXXXXX") || return 74
    if ! cp -- "$archive" "$temporary" || ! chmod --reference="$archive" "$temporary" \
        || ! mv -fT -- "$temporary" "$destination"; then
        [[ ! -e "$temporary" && ! -L "$temporary" ]] || unlink -- "$temporary" 2>/dev/null || true
        return 74
    fi
    [[ -f "$destination" && ! -L "$destination" ]] || return 74
    [[ "$(sha256 "$destination")" == "$expected_hash" && "$(byte_length "$destination")" == "$expected_length" ]] || return 74
}

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
report="$worktree/docs/status/rewind-round-trip-gaps.md"
[[ -f "$report" && ! -L "$report" ]] || die "historical generated report preimage is not a regular file"
[[ "$(git -C "$worktree" hash-object --no-filters "$report")" == d83614ec3a32abd1d6636d2be247ade01331bf3c ]] \
    || die "historical generated report preimage blob is invalid"
git -C "$harness_worktree" symbolic-ref -q HEAD >/dev/null && die "harness worktree must be detached"
[[ "$(git -C "$harness_worktree" rev-parse HEAD)" == "$expected_harness_head" ]] || die "unexpected harness HEAD"
[[ -z "$(git -C "$harness_worktree" status --porcelain --untracked-files=all)" ]] || die "harness worktree is not clean"
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

config_count=${GIT_CONFIG_COUNT:-0}
[[ "$config_count" =~ ^[0-9]+$ ]] || die "GIT_CONFIG_COUNT is not numeric"
config_key="GIT_CONFIG_KEY_$config_count"
config_value="GIT_CONFIG_VALUE_$config_count"
printf -v "$config_key" '%s' core.excludesFile
printf -v "$config_value" '%s' "$exclude"
export "$config_key" "$config_value"
export GIT_CONFIG_COUNT=$((config_count + 1))

recover_report() {
    local marker=$1 manifest=$2 run_id=$3 diagnostics recorded_head recorded_worktree recorded_report
    local recorded_relative archive archive_hash archive_length archive_blob canonical_archive tripwire_status=0
    recorded_worktree=$(marker_value "$marker" worktree)
    recorded_head=$(marker_value "$marker" frozen_head)
    recorded_report=$(marker_value "$marker" report)
    recorded_relative=$(marker_value "$marker" report_relative)
    archive=$(marker_value "$marker" preimage_archive)
    archive_hash=$(marker_value "$marker" preimage_hash)
    archive_length=$(marker_value "$marker" preimage_length)
    archive_blob=$(marker_value "$marker" preimage_blob)
    diagnostics="$(dirname -- "$manifest")/diagnostics"
    [[ "$(marker_value "$marker" run_id)" == "$run_id" && "$recorded_worktree" == "$worktree" ]] || return 74
    [[ "$recorded_head" == "$expected_head" && "$(git -C "$worktree" rev-parse HEAD)" == "$expected_head" ]] || return 74
    git -C "$worktree" symbolic-ref -q HEAD >/dev/null && return 74
    [[ "$recorded_relative" == docs/status/rewind-round-trip-gaps.md \
        && "$recorded_report" == "$worktree/$recorded_relative" ]] || return 74
    [[ -f "$archive" && ! -L "$archive" ]] || return 74
    canonical_archive=$(readlink -f -- "$archive") || return 74
    [[ "$canonical_archive" == "$diagnostics"/* && "$archive_blob" == d83614ec3a32abd1d6636d2be247ade01331bf3c ]] \
        || return 74
    [[ "$(git -C "$worktree" hash-object --no-filters "$archive")" == "$archive_blob" \
        && "$(sha256 "$archive")" == "$archive_hash" && "$(byte_length "$archive")" == "$archive_length" ]] \
        || return 74
    [[ -f "$recorded_report" && ! -L "$recorded_report" ]] || return 74
    authenticate_tripwire "$diagnostics" "$run_id" "$recorded_report" || tripwire_status=$?
    if [[ "$(sha256 "$recorded_report")" != "$archive_hash" \
        || "$(byte_length "$recorded_report")" != "$archive_length" ]]; then
        atomic_restore_report "$archive" "$recorded_report" "$archive_hash" "$archive_length" || return $?
        printf 'frozen-next launcher: restored authenticated historical generated report after non-certifying child outcome\n' >&2
    fi
    return "$tripwire_status"
}

recover() {
    local output=$1 manifest run_id marker diagnostics target kind device inode expected_empty expected_mount
    local leader_pid leader_start namespace build tmp surefire trace artifacts distribution recovery_status=0
    local outer_uid authenticated_home
    manifest=$(printf '%s\n' "$output" | sed -n 's/.*manifest=\([^ ]*\).*/\1/p' | tail -1)
    [[ -n "$manifest" && -f "$manifest" ]] || return 0
    run_id=$(manifest_value "$manifest" run_id)
    diagnostics="$(dirname -- "$manifest")/diagnostics"
    marker="$diagnostics/frozen-next-session-recovery.env"
    [[ -n "$run_id" && -f "$marker" && ! -L "$marker" ]] || return 0
    [[ "$(marker_value "$marker" run_id)" == "$run_id" \
        && "$(marker_value "$marker" worktree)" == "$worktree" ]] || return 74
    target=$(marker_value "$marker" target)
    kind=$(marker_value "$marker" target_kind)
    device=$(marker_value "$marker" target_device)
    inode=$(marker_value "$marker" target_inode)
    expected_empty=$(marker_value "$marker" parent_expected_empty)
    expected_mount=$(marker_value "$marker" parent_expected_mount)
    leader_pid=$(marker_value "$marker" leader_pid)
    leader_start=$(marker_value "$marker" leader_start)
    namespace=$(marker_value "$marker" mount_namespace_inode)
    build=$(marker_value "$marker" build_root)
    tmp=$(marker_value "$marker" tmp_root)
    surefire=$(marker_value "$marker" surefire_reports)
    trace=$(marker_value "$marker" trace_reports)
    artifacts=$(marker_value "$marker" artifact_root)
    distribution=$(marker_value "$marker" distribution_root)
    outer_uid=$(marker_value "$marker" outer_uid)
    authenticated_home=$(marker_value "$marker" authenticated_home)
    [[ "$target" == "$worktree/target" && "$kind" == directory && "$device" =~ ^[0-9]+$ \
        && "$inode" =~ ^[0-9]+$ && "$expected_empty" == true && "$expected_mount" == false \
        && "$leader_pid" =~ ^[0-9]+$ && "$leader_start" =~ ^[0-9]+$ && "$namespace" =~ ^[0-9]+$ ]] \
        || return 74
    [[ "$outer_uid" == "$(id -u)" && "$authenticated_home" == "$(readlink -f -- "$HOME")" ]] \
        || return 74
    [[ "$build" == "$(manifest_value "$manifest" build_root)" \
        && "$tmp" == "$(manifest_value "$manifest" tmp_root)" \
        && "$surefire" == "$(manifest_value "$manifest" surefire_reports)" \
        && "$trace" == "$(manifest_value "$manifest" trace_reports)" \
        && "$(marker_value "$marker" diagnostics_root)" == "$diagnostics" \
        && "$artifacts" == "$(manifest_value "$manifest" artifact_root)" \
        && "$distribution" == "$(manifest_value "$manifest" distribution_root)" ]] || return 74

    wait_namespace_gone "$leader_pid" "$leader_start" "$namespace" || return 76
    [[ -z "$(namespace_holders "$namespace")" ]] || return 76
    recover_report "$marker" "$manifest" "$run_id" || recovery_status=$?

    if [[ -e "$target" || -L "$target" ]]; then
        [[ "$(lstat_kind "$target")" == "$kind" && "$(lstat_device "$target")" == "$device" \
            && "$(lstat_inode "$target")" == "$inode" ]] || return 73
        mountpoint -q -- "$target" && return 73
        directory_empty "$target" || return 73
        rmdir -- "$target" || return 73
    fi
    return "$recovery_status"
}

capture_file= wrapper_pid= finalized=0 finalize_status=0
finalize() {
    (( finalized == 0 )) || return "$finalize_status"
    finalized=1
    local output= cleanup_status=0
    if [[ -n "$capture_file" && -f "$capture_file" && ! -L "$capture_file" ]]; then
        output=$(<"$capture_file")
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
    if (( status != 0 )); then exit "$status"; fi
    exit "$cleanup_status"
}
on_signal() {
    local signal=$1 status=$2 child_signal=$1
    trap - INT TERM
    [[ "$signal" == INT ]] && child_signal=TERM
    if [[ -n "$wrapper_pid" ]]; then
        kill -"$child_signal" "$wrapper_pid" 2>/dev/null || true
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
