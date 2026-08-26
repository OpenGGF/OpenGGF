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
wait_process_pair_gone() {
    local supervisor=$1 supervisor_start=$2 private_pid1=$3 private_pid1_start=$4
    local attempt current_supervisor_start current_private_pid1_start
    for ((attempt = 0; attempt < 200; attempt++)); do
        current_supervisor_start=$(process_start "$supervisor" || true)
        current_private_pid1_start=$(process_start "$private_pid1" || true)
        if [[ "$current_supervisor_start" != "$supervisor_start" \
            && "$current_private_pid1_start" != "$private_pid1_start" ]]; then return 0; fi
        sleep 0.05
    done
    return 76
}
marker_value() { sed -n "s/^$2=//p" "$1"; }
manifest_value() { sed -n "s/.*\"$2\": \"\([^\"]*\)\".*/\1/p" "$1" | head -1; }
authenticate_terminal_line() {
    local output=$1 expected_run_id=$2 expected_manifest=$3 line field key value
    local manifest_state manifest_valid run_id= manifest= state= valid=
    local run_count=0 manifest_count=0 state_count=0 valid_count=0
    local -a lines fields
    mapfile -t lines < <(printf '%s\n' "$output" | sed -n '/^OPENGGF_TEST_RUN_END /p')
    (( ${#lines[@]} == 1 )) || { (( ${#lines[@]} == 0 )) && return 1; return 77; }
    line=${lines[0]}
    read -r -a fields <<< "$line"
    [[ "${fields[0]:-}" == OPENGGF_TEST_RUN_END ]] || return 77
    for field in "${fields[@]:1}"; do
        [[ "$field" == *=* ]] || return 77
        key=${field%%=*}
        value=${field#*=}
        case "$key" in
            run_id) run_id=$value; run_count=$((run_count + 1)) ;;
            manifest) manifest=$value; manifest_count=$((manifest_count + 1)) ;;
            state) state=$value; state_count=$((state_count + 1)) ;;
            valid) valid=$value; valid_count=$((valid_count + 1)) ;;
        esac
    done
    (( run_count == 1 && manifest_count == 1 && state_count == 1 && valid_count == 1 )) || return 77
    [[ "$run_id" == "$expected_run_id" && "$manifest" == "$expected_manifest" \
        && "$state" =~ ^(PASSED|FAILED|ABORTED|INVALID_IDENTITY_CHANGED)$ \
        && "$valid" =~ ^(true|false)$ ]] || return 77
    manifest_state=$(sed -n 's/.*"state": "\([^"]*\)".*/\1/p' "$expected_manifest" | head -1)
    case "$manifest_state" in
        PASSED|FAILED) manifest_valid=true ;;
        ABORTED|INVALID_IDENTITY_CHANGED) manifest_valid=false ;;
        *) return 77 ;;
    esac
    [[ "$state" == "$manifest_state" && "$valid" == "$manifest_valid" ]] || return 77
    terminal_run_id=$run_id
    terminal_manifest=$manifest
    terminal_state=$state
    terminal_valid=$valid
    return 0
}
authenticate_test_seam() {
    local diagnostics=$1 run_id=$2 marker="$1/frozen-next-test-seam.env" mode variables
    if [[ -z "$launcher_test_seam_variables" ]]; then
        [[ ! -e "$marker" && ! -L "$marker" ]] || return 77
        recovery_test_seam=0
        return 0
    fi
    recovery_test_seam=1
    [[ -f "$marker" && ! -L "$marker" ]] || return 77
    mode=$(marker_value "$marker" mode)
    variables=$(marker_value "$marker" variables)
    [[ "$(marker_value "$marker" run_id)" == "$run_id" \
        && ( "$mode" == exact-self-test-v1 || "$mode" == rejected-unmodeled ) \
        && "$variables" == "$launcher_test_seam_variables" ]] || return 77
    if [[ "$mode" == exact-self-test-v1 ]]; then
        [[ "${OPENGGF_FROZEN_NEXT_SELF_TEST_MODE:-}" == 1 ]] || return 77
    else
        [[ "${OPENGGF_FROZEN_NEXT_SELF_TEST_MODE:-}" != 1 ]] || return 77
    fi
    printf 'frozen-next launcher: authenticated non-admissible adapter test seam: mode=%s variables=%s\n' \
        "$mode" "$variables" >&2
}
authenticate_tripwire() {
    local diagnostics=$1 run_id=$2 report=$3 trigger evidence reason child adapter_status mode
    local report_hash report_length current_hash current_length
    trigger="$diagnostics/frozen-next-safety-failure.env"
    evidence="$diagnostics/frozen-next-identity-tripwire.env"
    if [[ ! -e "$trigger" && ! -L "$trigger" && ! -e "$evidence" && ! -L "$evidence" ]]; then
        recovery_tripwire=false
        return 0
    fi
    recovery_tripwire=true
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
        && ( "$child" == not-started || "$child" =~ ^[0-9]+$ ) \
        && "$adapter_status" =~ ^[0-9]+$ ]] || return 77
    case "$reason" in
        namespace-teardown)
            [[ "$adapter_status" == 75 ]] || return 77
            ;;
        target-cleanup)
            [[ "$adapter_status" == 73 || "$adapter_status" == 76 ]] || return 77
            ;;
        adapter-safety-*) ;;
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

launcher_test_seam_variables=
for test_seam_variable in \
    OPENGGF_FROZEN_NEXT_SELF_TEST_MODE OPENGGF_ADAPTER_TEST_SHIM \
    OPENGGF_TEST_CLEANUP_IDENTITY_PID OPENGGF_TEST_CLEANUP_IDENTITY_ACTUAL_START \
    OPENGGF_TEST_CLEANUP_IDENTITY_RECORDED_START OPENGGF_TEST_PUBLISHED_PID_NAMESPACE_OVERRIDE \
    OPENGGF_TEST_READY_SUPERVISOR_START_OVERRIDE OPENGGF_TEST_READY_PRIVATE_PID1_START_OVERRIDE \
    OPENGGF_TEST_READY_COMMON_MOUNT_OVERRIDE OPENGGF_TEST_TRIPWIRE_ARM_FAILURE \
    OPENGGF_TEST_TRIPWIRE_REASON_OVERRIDE OPENGGF_TEST_FAIL_PREFLIGHT OPENGGF_TEST_FAIL_BIND \
    OPENGGF_TEST_WRONG_MOUNT_IDENTITY OPENGGF_TEST_PROPAGATION_LEAK \
    OPENGGF_TEST_REAL_UNSHARE OPENGGF_TEST_REAL_MOUNT OPENGGF_TEST_REAL_STAT \
    OPENGGF_TEST_REAL_MOUNTPOINT OPENGGF_TEST_RMDIR_FAIL_TARGET OPENGGF_TEST_REAL_RMDIR \
    OPENGGF_TEST_FAIL_GENERATED_ARCHIVE OPENGGF_TEST_CORRUPT_GENERATED_ARCHIVE \
    OPENGGF_TEST_FAIL_RESTORE \
    OPENGGF_TEST_REAL_CP OPENGGF_TEST_REAL_MV; do
    [[ -v "$test_seam_variable" ]] || continue
    launcher_test_seam_variables="${launcher_test_seam_variables:+$launcher_test_seam_variables,}$test_seam_variable"
done

recover_report() {
    local authority=$1 manifest=$2 run_id=$3 diagnostics recorded_head recorded_worktree recorded_report
    local recorded_relative archive archive_hash archive_length archive_blob canonical_archive tripwire_status=0
    [[ "$(marker_value "$authority" authority_version)" == 1 ]] || return 74
    recorded_worktree=$(marker_value "$authority" worktree)
    recorded_head=$(marker_value "$authority" frozen_head)
    recorded_report=$(marker_value "$authority" report)
    recorded_relative=$(marker_value "$authority" report_relative)
    archive=$(marker_value "$authority" preimage_archive)
    archive_hash=$(marker_value "$authority" preimage_hash)
    archive_length=$(marker_value "$authority" preimage_length)
    archive_blob=$(marker_value "$authority" preimage_blob)
    diagnostics="$(dirname -- "$manifest")/diagnostics"
    [[ "$(marker_value "$authority" run_id)" == "$run_id" && "$recorded_worktree" == "$worktree" \
        && "$(marker_value "$authority" diagnostics_root)" == "$diagnostics" ]] || return 74
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
    recovery_report_authenticated=1
    return "$tripwire_status"
}

recover() {
    local output=$1 manifest run_id marker authority diagnostics target kind device inode expected_empty expected_mount
    local supervisor_pid supervisor_start private_pid1_pid private_pid1_start private_pid1_nspid parent_identity
    local private_pid_namespace common_mount_namespace build tmp surefire trace artifacts distribution recovery_status=0
    local outer_uid authenticated_home marker_phase terminal_status=0
    manifest=$(printf '%s\n' "$output" | sed -n 's/.*manifest=\([^ ]*\).*/\1/p' | tail -1)
    [[ -n "$manifest" && -f "$manifest" ]] || return 0
    run_id=$(manifest_value "$manifest" run_id)
    recovery_run_id=$run_id
    diagnostics="$(dirname -- "$manifest")/diagnostics"
    authority="$diagnostics/frozen-next-report-authority.env"
    [[ -n "$run_id" && -f "$authority" && ! -L "$authority" ]] || return 77
    authenticate_test_seam "$diagnostics" "$run_id" || recovery_status=$?
    authenticate_terminal_line "$output" "$run_id" "$manifest" || terminal_status=$?
    if (( terminal_status == 0 )); then
        recovery_terminal_authenticated=1
    elif (( terminal_status != 1 )); then
        recovery_status=$terminal_status
        printf 'frozen-next launcher: coordinator terminal line is duplicate or identity-invalid\n' >&2
    fi
    recover_report "$authority" "$manifest" "$run_id" || recovery_status=$?
    marker="$diagnostics/frozen-next-session-recovery.env"
    [[ -f "$marker" && ! -L "$marker" ]] || return "$recovery_status"
    [[ "$(marker_value "$marker" run_id)" == "$run_id" \
        && "$(marker_value "$marker" worktree)" == "$worktree" \
        && "$(marker_value "$marker" report_authority)" == "$authority" ]] || return 74
    marker_phase=$(marker_value "$marker" marker_phase)
    target=$(marker_value "$marker" target)
    kind=$(marker_value "$marker" target_kind)
    device=$(marker_value "$marker" target_device)
    inode=$(marker_value "$marker" target_inode)
    expected_empty=$(marker_value "$marker" parent_expected_empty)
    expected_mount=$(marker_value "$marker" parent_expected_mount)
    supervisor_pid=$(marker_value "$marker" supervisor_pid)
    supervisor_start=$(marker_value "$marker" supervisor_start)
    private_pid1_pid=$(marker_value "$marker" private_pid1_pid)
    private_pid1_start=$(marker_value "$marker" private_pid1_start)
    private_pid1_nspid=$(marker_value "$marker" private_pid1_nspid)
    private_pid_namespace=$(marker_value "$marker" private_pid_namespace_inode)
    common_mount_namespace=$(marker_value "$marker" common_mount_namespace_inode)
    parent_identity=$(marker_value "$marker" parent_process_identity)
    build=$(marker_value "$marker" build_root)
    tmp=$(marker_value "$marker" tmp_root)
    surefire=$(marker_value "$marker" surefire_reports)
    trace=$(marker_value "$marker" trace_reports)
    artifacts=$(marker_value "$marker" artifact_root)
    distribution=$(marker_value "$marker" distribution_root)
    outer_uid=$(marker_value "$marker" outer_uid)
    authenticated_home=$(marker_value "$marker" authenticated_home)
    [[ "$target" == "$worktree/target" && "$kind" == directory && "$device" =~ ^[0-9]+$ \
        && "$inode" =~ ^[0-9]+$ && "$expected_empty" == true && "$expected_mount" == false ]] \
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

    case "$marker_phase" in
        ready-verified)
            [[ "$supervisor_pid" =~ ^[0-9]+$ && "$supervisor_start" =~ ^[0-9]+$ \
                && "$private_pid1_pid" =~ ^[0-9]+$ && "$private_pid1_start" =~ ^[0-9]+$ \
                && "$private_pid1_nspid" == *$'\t1' \
                && "$private_pid_namespace" =~ ^[0-9]+$ && "$common_mount_namespace" =~ ^[0-9]+$ ]] \
                || return 74
            [[ "$parent_identity" == "$diagnostics/frozen-next-parent-process-identity.env" \
                && -f "$parent_identity" && ! -L "$parent_identity" \
                && "$(marker_value "$parent_identity" supervisor_pid)" == "$supervisor_pid" \
                && "$(marker_value "$parent_identity" supervisor_start)" == "$supervisor_start" \
                && "$(marker_value "$parent_identity" private_pid1_pid)" == "$private_pid1_pid" \
                && "$(marker_value "$parent_identity" private_pid1_start)" == "$private_pid1_start" \
                && "$(marker_value "$parent_identity" private_pid1_nspid)" == "$private_pid1_nspid" \
                && "$(marker_value "$parent_identity" private_pid_namespace_inode)" == "$private_pid_namespace" \
                && "$(marker_value "$parent_identity" common_mount_namespace_inode)" == "$common_mount_namespace" ]] \
                || return 74
            wait_process_pair_gone "$supervisor_pid" "$supervisor_start" \
                "$private_pid1_pid" "$private_pid1_start" || return 76
            recovery_full_marker=1
            ;;
        target-authenticated)
            [[ "$supervisor_pid" == pending && "$supervisor_start" == pending \
                && "$private_pid1_pid" == pending && "$private_pid1_start" == pending \
                && "$private_pid_namespace" == pending && "$common_mount_namespace" == pending \
                && -z "$parent_identity" ]] \
                || return 74
            ;;
        *) return 74 ;;
    esac

    if [[ -e "$target" || -L "$target" ]]; then
        [[ "$marker_phase" == ready-verified ]] || return 73
        [[ "$(lstat_kind "$target")" == "$kind" && "$(lstat_device "$target")" == "$device" \
            && "$(lstat_inode "$target")" == "$inode" ]] || return 73
        mountpoint -q -- "$target" && return 73
        directory_empty "$target" || return 73
        rmdir -- "$target" || return 73
    fi
    recovery_target_clean=1
    return "$recovery_status"
}

capture_file= wrapper_pid= finalized=0 finalize_status=0 final_output= final_run_id=
recovery_report_authenticated=0 recovery_full_marker=0 recovery_target_clean=0 recovery_tripwire=false
recovery_terminal_authenticated=0 recovery_test_seam=0 recovery_run_id=
terminal_run_id= terminal_manifest= terminal_state= terminal_valid=
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
        final_output=$output
        final_run_id=$recovery_run_id
        printf '%s\n' "$output"
        unlink -- "$capture_file" || true
    fi
    finalize_status=$cleanup_status
    return "$finalize_status"
}
emit_launcher_outcome() {
    local adapter_status=$1 cleanup_status=$2 admissible=false authenticated=false
    (( recovery_report_authenticated == 1 )) && authenticated=true
    if (( cleanup_status == 0 && recovery_report_authenticated == 1 \
        && recovery_full_marker == 1 && recovery_target_clean == 1 \
        && recovery_terminal_authenticated == 1 && recovery_test_seam == 0 )) \
        && [[ "$recovery_tripwire" == false \
            && "$terminal_run_id" == "$final_run_id" && "$terminal_manifest" != '' \
            && "$terminal_valid" == true ]]; then
        admissible=true
    fi
    printf 'OPENGGF_FROZEN_NEXT_LAUNCH_END run_id=%s adapter_status=%s cleanup_status=%s authenticated=%s admissible=%s\n' \
        "${final_run_id:-unknown}" "$adapter_status" "$cleanup_status" "$authenticated" "$admissible"
}
on_exit() {
    local status=$? cleanup_status=0
    trap - EXIT
    finalize || cleanup_status=$?
    emit_launcher_outcome "$status" "$cleanup_status"
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
