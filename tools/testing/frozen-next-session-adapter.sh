#!/usr/bin/env bash
# Historical child adapter: route frozen next through a private mount namespace.
set -euo pipefail

readonly FROZEN_NEXT_HEAD=84d9a3761f618035dd1caa40a3d5fc72a1019693
readonly REPORT_RELATIVE=docs/status/rewind-round-trip-gaps.md
readonly REPORT_BLOB=d83614ec3a32abd1d6636d2be247ade01331bf3c
readonly PROBE_CLASS=com.openggf.game.rewind.coverage.TestRewindRoundTripProbe
readonly PROBE_METHOD=probeReportIsWrittenToDisk

die() { printf 'frozen-next adapter: %s\n' "$*" >&2; exit 2; }
canonical_dir() { [[ -d "$1" ]] || die "not a directory: $1"; (CDPATH= cd -- "$1" && pwd -P); }
sha256() { sha256sum -- "$1" | awk '{print $1}'; }
byte_length() { wc -c < "$1" | tr -d '[:space:]'; }
lstat_kind() { stat -c '%F' -- "$1" 2>/dev/null; }
lstat_device() { stat -c '%d' -- "$1" 2>/dev/null; }
lstat_inode() { stat -c '%i' -- "$1" 2>/dev/null; }
mounted_identity() { stat -Lc '%d:%i' -- "$1" 2>/dev/null; }
process_start() { sed 's/^[^)]*) //' "/proc/$1/stat" 2>/dev/null | awk '{print $20}'; }
mount_namespace_inode() { stat -Lc '%i' -- "/proc/$1/ns/mnt" 2>/dev/null; }
pid_namespace_inode() { stat -Lc '%i' -- "/proc/$1/ns/pid" 2>/dev/null; }
process_nspid() { sed -n 's/^NSpid:[[:space:]]*//p' "/proc/$1/status" 2>/dev/null; }
process_children() { sed -n '1p' "/proc/$1/task/$1/children" 2>/dev/null; }
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
xml_property() {
    local report=$1 property=$2
    sed -n "s/.*<property name=\"$property\" value=\"\([^\"]*\)\"\/>.*/\1/p" "$report" | head -1
}
atomic_restore() {
    local archive=$1 destination=$2 expected_hash=$3 expected_length=$4 temporary
    [[ -f "$archive" && ! -L "$archive" && -f "$destination" && ! -L "$destination" ]] || return 1
    [[ "$(sha256 "$archive")" == "$expected_hash" && "$(byte_length "$archive")" == "$expected_length" ]] || return 1
    temporary=$(mktemp "$(dirname -- "$destination")/.rewind-round-trip-gaps.restore.XXXXXX") || return 1
    if ! cp -- "$archive" "$temporary" || ! chmod --reference="$archive" "$temporary" \
        || ! mv -fT -- "$temporary" "$destination"; then
        [[ ! -e "$temporary" && ! -L "$temporary" ]] || unlink -- "$temporary" 2>/dev/null || true
        return 1
    fi
    [[ -f "$destination" && ! -L "$destination" ]] || return 1
    [[ "$(sha256 "$destination")" == "$expected_hash" && "$(byte_length "$destination")" == "$expected_length" ]]
}
probe_outcome() {
    perl -0777 -e '
        my ($class, $method, @files) = @ARGV;
        my @matches;
        for my $file (@files) {
            open my $fh, "<", $file or next;
            local $/; my $xml = <$fh>;
            while ($xml =~ m{<testcase\b([^>]*)(?:/>|>(.*?)</testcase>)}sg) {
                my ($attrs, $body) = ($1, defined($2) ? $2 : "");
                next unless $attrs =~ /\bclassname="\Q$class\E"/;
                next unless $attrs =~ /\bname="\Q$method\E"/;
                my $outcome = $body =~ /<failure\b/ ? "failed"
                    : $body =~ /<error\b/ ? "error"
                    : $body =~ /<skipped\b/ ? "skipped" : "passed";
                push @matches, "$file\t$outcome";
            }
        }
        exit 1 unless @matches == 1;
        print "$matches[0]\n";
    ' "$PROBE_CLASS" "$PROBE_METHOD" "$@"
}

namespace_leader() {
    local token=$1
    shift
    [[ -n "${OPENGGF_FROZEN_NAMESPACE_TOKEN:-}" && "$token" == "$OPENGGF_FROZEN_NAMESPACE_TOKEN" ]] \
        || die "invalid namespace-leader token"
    local worktree=$OPENGGF_TEST_WORKTREE target=$OPENGGF_FROZEN_TARGET
    local build=$OPENGGF_BUILD_DIRECTORY tmp=$OPENGGF_TEST_TMP_ROOT
    local authenticated_home=$OPENGGF_FROZEN_AUTHENTICATED_HOME outer_uid=$OPENGGF_FROZEN_OUTER_UID
    local session_root=$OPENGGF_FROZEN_SESSION_ROOT diagnostics=$OPENGGF_TEST_DIAGNOSTICS
    local ready_fifo=$OPENGGF_FROZEN_READY_FIFO go_fifo=$OPENGGF_FROZEN_GO_FIFO
    local leader_info=$OPENGGF_FROZEN_LEADER_INFO mount_evidence=$OPENGGF_FROZEN_MOUNT_EVIDENCE
    local path_evidence=$OPENGGF_FROZEN_PATH_EVIDENCE status_file=$OPENGGF_FROZEN_STATUS_FILE
    local phase_file="$diagnostics/frozen-next-namespace-phase.env"
    local surefire_reports="$session_root/surefire-reports" trace_reports="$session_root/trace-reports"
    local private_pid1_host= private_pid1_start private_pid1_nspid= private_pid_namespace
    local supervisor_host= common_mount_namespace
    local maven_pid= child_status=70 path_failure=0 cleanup_failure=0
    local -a mount_sources=("$build" "$tmp" "$surefire_reports" "$trace_reports" \
        "$diagnostics" "$OPENGGF_ARTIFACT_ROOT" "$OPENGGF_DISTRIBUTION_ROOT")
    local -a mount_targets=("$target" "$target/test-tmp" "$target/surefire-reports" \
        "$target/trace-reports" "$target/diagnostics" "$target/artifacts" "$target/distribution")

    teardown_mounts() {
        local index
        for ((index = ${#mount_targets[@]} - 1; index >= 0; index--)); do
            mountpoint -q -- "${mount_targets[index]}" || continue
            umount -- "${mount_targets[index]}" || cleanup_failure=75
        done
    }
    leader_signal() {
        local signal_status=$1
        trap - INT TERM HUP
        if [[ -n "$maven_pid" ]]; then
            kill -TERM "$maven_pid" 2>/dev/null || true
            wait "$maven_pid" 2>/dev/null || true
        fi
        teardown_mounts
        exit "$signal_status"
    }
    trap 'leader_signal 130' INT
    trap 'leader_signal 143' TERM HUP

    local status_key status_value
    local -a nspid_parts
    while IFS=: read -r status_key status_value; do
        status_value=${status_value#[$' \t']}
        case "$status_key" in
            NSpid)
                private_pid1_nspid=$status_value
                read -r -a nspid_parts <<< "$status_value"
                private_pid1_host=${nspid_parts[0]:-}
                ;;
            PPid) supervisor_host=$status_value ;;
        esac
    done < /proc/self/status
    [[ "$private_pid1_host" =~ ^[0-9]+$ && "$supervisor_host" =~ ^[0-9]+$ \
        && "$private_pid1_nspid" == *$'\t1' ]] \
        || die "could not publish private PID 1 identity"

    mount --make-rprivate / || die "could not make mount namespace recursively private"
    : > "$mount_evidence"
    printf 'make_rprivate=passed\n' >> "$mount_evidence"
    local index source mounted source_identity target_identity
    for ((index = 0; index < ${#mount_targets[@]}; index++)); do
        source=${mount_sources[index]}
        mounted=${mount_targets[index]}
        mount --bind "$source" "$mounted" || die "bind mount failed: $source -> $mounted"
        mountpoint -q -- "$mounted" || die "bind target is not a mountpoint: $mounted"
        source_identity=$(mounted_identity "$source")
        target_identity=$(mounted_identity "$mounted")
        [[ -n "$source_identity" && "$source_identity" == "$target_identity" ]] \
            || die "bind identity mismatch: $source -> $mounted"
        printf 'mount_source=%s\nmount_target=%s\nmount_identity=%s\n' \
            "$source" "$mounted" "$source_identity" >> "$mount_evidence"
        awk -v target="$mounted" '$5 == target {print "mountinfo=" $0}' /proc/self/mountinfo >> "$mount_evidence"
    done

    private_pid1_start=$(process_start "$private_pid1_host")
    private_pid_namespace=$(pid_namespace_inode "$private_pid1_host")
    common_mount_namespace=$(mount_namespace_inode "$private_pid1_host")
    [[ -n "$private_pid1_start" && -n "$private_pid_namespace" && -n "$common_mount_namespace" ]] \
        || die "could not record private PID 1 namespaces"
    [[ -z "${OPENGGF_TEST_PUBLISHED_PID_NAMESPACE_OVERRIDE:-}" ]] \
        || private_pid_namespace=$OPENGGF_TEST_PUBLISHED_PID_NAMESPACE_OVERRIDE
    printf 'supervisor_host_pid=%s\nprivate_pid1_host_pid=%s\nprivate_pid1_start=%s\n' \
        "$supervisor_host" "$private_pid1_host" "$private_pid1_start" > "$leader_info"
    printf 'private_pid1_nspid=%s\nprivate_pid_namespace_inode=%s\ncommon_mount_namespace_inode=%s\n' \
        "$private_pid1_nspid" "$private_pid_namespace" "$common_mount_namespace" >> "$leader_info"
    printf 'ready\n' > "$ready_fifo"
    local barrier_value=
    IFS= read -r barrier_value < "$go_fifo"
    [[ "$barrier_value" == go ]] || die "ready/go barrier was not released"

    local -a maven=("$@")
    local arg_line arg_line_status effective_maven_opts
    effective_maven_opts="${MAVEN_OPTS:+$MAVEN_OPTS }-Duser.home=$authenticated_home"
    printf 'phase=before-arg-evaluate\nmaven=%s\nfake_maven=%s\nouter_uid=%s\nauthenticated_home=%s\n' \
        "${maven[0]}" "${OPENGGF_FAKE_MAVEN:-unset}" "$outer_uid" "$authenticated_home" > "$phase_file"
    set +e
    arg_line=$(MAVEN_OPTS="$effective_maven_opts" "${maven[0]}" -q -Dmse=off \
        help:evaluate -Dexpression=surefire.argLine -DforceStdout)
    arg_line_status=$?
    set -e
    printf 'phase=after-arg-evaluate\narg_line_status=%s\narg_line=%s\n' \
        "$arg_line_status" "$arg_line" > "$phase_file"
    (( arg_line_status == 0 )) || die "could not evaluate surefire.argLine"
    [[ -n "$arg_line" && "$arg_line" != *'${'* ]] || die "could not resolve surefire.argLine"
    [[ " $arg_line " != *-Duser.home=* ]] \
        || die "resolved surefire.argLine user.home override is forbidden"
    arg_line="$arg_line -Duser.home=$authenticated_home"
    arg_line="$arg_line -Dorg.lwjgl.system.SharedLibraryExtractPath=$tmp/lwjgl-\${surefire.forkNumber}"
    printf 'phase=before-child\n' > "$phase_file"
    set +e
    MAVEN_OPTS="$effective_maven_opts" "${maven[0]}" -Dmse=off \
        "-Dsurefire.argLine=$arg_line" "${maven[@]:1}" &
    maven_pid=$!
    wait "$maven_pid"
    child_status=$?
    maven_pid=
    set -e
    printf 'phase=after-child\nchild_status=%s\n' "$child_status" > "$phase_file"

    shopt -s nullglob
    local -a reports=("$target/surefire-reports"/TEST-*.xml)
    shopt -u nullglob
    {
        printf 'run_id=%s\nworktree=%s\ntarget=%s\n' "$OPENGGF_TEST_RUN_ID" "$worktree" "$target"
        printf 'namespace_pid1_host_pid=%s\nnamespace_pid1_start=%s\nmount_namespace_inode=%s\n' \
            "$private_pid1_host" "$private_pid1_start" "$common_mount_namespace"
        printf 'pid_namespace_inode=%s\nprivate_pid1_nspid=%s\n' \
            "$private_pid_namespace" "$private_pid1_nspid"
        printf 'outer_uid=%s\nauthenticated_home=%s\neffective_maven_opts=%s\n' \
            "$outer_uid" "$authenticated_home" "$effective_maven_opts"
        printf 'effective_arg_line=%s\nreport_jvm_properties:\n' "$arg_line"
        local xml_report temp_lexical temp_canonical lwjgl_lexical lwjgl_canonical fork_home
        for xml_report in "${reports[@]}"; do
            [[ -f "$xml_report" && ! -L "$xml_report" ]] || { path_failure=1; continue; }
            temp_lexical=$(xml_property "$xml_report" java.io.tmpdir)
            lwjgl_lexical=$(xml_property "$xml_report" org.lwjgl.system.SharedLibraryExtractPath)
            fork_home=$(xml_property "$xml_report" user.home)
            temp_canonical=$(readlink -f -- "$temp_lexical" 2>/dev/null || true)
            lwjgl_canonical=$(readlink -f -- "$lwjgl_lexical" 2>/dev/null || true)
            printf 'report=%s\njava_io_tmpdir_lexical=%s\njava_io_tmpdir_canonical=%s\n' \
                "$(basename -- "$xml_report")" "$temp_lexical" "$temp_canonical"
            printf 'fork_user_home=%s\nlwjgl_extract_lexical=%s\nlwjgl_extract_canonical=%s\n' \
                "$fork_home" "$lwjgl_lexical" "$lwjgl_canonical"
            [[ "$fork_home" == "$authenticated_home" ]] || path_failure=1
            [[ "$temp_lexical" == "$target/test-tmp" && "$temp_canonical" == "$target/test-tmp" ]] \
                || path_failure=1
            [[ "$lwjgl_lexical" == "$tmp"/lwjgl-* && "$lwjgl_canonical" == "$tmp"/lwjgl-* ]] \
                || path_failure=1
            [[ "$(dirname -- "$lwjgl_canonical")" == "$tmp" \
                && "$(basename -- "$lwjgl_canonical")" != 'lwjgl-${surefire.forkNumber}' ]] || path_failure=1
        done
    } > "$path_evidence"
    printf 'phase=after-path-evidence\nchild_status=%s\npath_failure=%s\n' \
        "$child_status" "$path_failure" > "$phase_file"
    printf 'child_status=%s\npath_failure=%s\n' "$child_status" "$path_failure" > "$status_file"

    teardown_mounts
    (( cleanup_failure == 0 )) || exit "$cleanup_failure"
    (( path_failure == 0 )) || { (( child_status != 0 )) && exit "$child_status"; exit 69; }
    exit "$child_status"
}

if [[ "${1:-}" == --namespace-leader ]]; then
    shift
    namespace_leader "$@"
    exit $?
fi

(( $# > 0 )) || die "Maven command required"
for argument in "$@"; do
    case "$argument" in
        clean|clean@*|*:clean|*:clean@*) die "clean is forbidden for the frozen adapter" ;;
        --namespace-leader|*surefire.argLine*|*argLine=*|*java.io.tmpdir*|*user.home*|*org.lwjgl.system.SharedLibraryExtractPath*)
            die "caller fork-JVM argument override is forbidden: $argument" ;;
    esac
done

worktree=$(canonical_dir "${OPENGGF_TEST_WORKTREE:-}")
[[ "$(git -C "$worktree" rev-parse HEAD)" == "$FROZEN_NEXT_HEAD" ]] || die "unexpected frozen-next HEAD"
git -C "$worktree" symbolic-ref -q HEAD >/dev/null && die "frozen-next worktree must be detached"
[[ -z "$(git -C "$worktree" status --porcelain --untracked-files=all)" ]] || die "frozen-next worktree is not clean"

launcher= exclude= adapter=
IFS=: read -r -a runtime_inputs <<< "${OPENGGF_RUNTIME_INPUTS:-}"
for input in "${runtime_inputs[@]}"; do
    [[ -f "$input" ]] || continue
    case "$(basename -- "$input")" in
        frozen-next-session-launch.sh) launcher=$(readlink -f -- "$input") ;;
        frozen-next-session.exclude) exclude=$(readlink -f -- "$input") ;;
        frozen-next-session-adapter.sh) adapter=$(readlink -f -- "$input") ;;
    esac
done
[[ -n "$launcher$exclude$adapter" ]] || die "required runtime inputs are missing"
[[ "$(git -C "$worktree" config --get core.excludesFile)" == "$exclude" ]] || die "external exclude is not inherited"
[[ "$(<"$exclude")" == "/target" \
    && "$(sha256 "$exclude")" == "$(printf '/target\n' | sha256sum | awk '{print $1}')" ]] \
    || die "external exclude hash is invalid"
for command in unshare mount umount mountpoint stat; do command -v "$command" >/dev/null || die "missing mount tool: $command"; done
[[ "$(uname -s)" == Linux && -e /proc/self/ns/mnt ]] || die "private mount namespaces require Linux procfs"
[[ " ${MAVEN_OPTS:-} " != *-Duser.home=* ]] || die "preexisting MAVEN_OPTS user.home override is forbidden"
[[ " ${JAVA_TOOL_OPTIONS:-} " != *-Duser.home=* ]] || die "preexisting JAVA_TOOL_OPTIONS user.home override is forbidden"
outer_uid=$(id -u)
passwd_entry=$(getent passwd "$outer_uid")
[[ -n "$passwd_entry" && "$(printf '%s\n' "$passwd_entry" | wc -l)" == 1 ]] \
    || die "outer UID passwd identity is missing or ambiguous"
passwd_home=$(printf '%s\n' "$passwd_entry" | awk -F: '{print $6}')
[[ -n "$passwd_home" && -n "${HOME:-}" ]] || die "outer passwd home or HOME is missing"
authenticated_home=$(canonical_dir "$passwd_home")
canonical_env_home=$(canonical_dir "$HOME")
[[ "$authenticated_home" == "$canonical_env_home" ]] || die "outer passwd home does not match canonical HOME"

target="$worktree/target"
[[ ! -e "$target" && ! -L "$target" ]] || die "target already exists; refusing to replace it"
for variable in OPENGGF_BUILD_DIRECTORY OPENGGF_TEST_TMP_ROOT OPENGGF_TEST_DIAGNOSTICS \
    OPENGGF_TEST_RUN_ID OPENGGF_TEST_MANIFEST OPENGGF_ARTIFACT_ROOT OPENGGF_DISTRIBUTION_ROOT; do
    [[ -n "${!variable:-}" ]] || die "missing coordinator variable: $variable"
done
build=$(canonical_dir "$OPENGGF_BUILD_DIRECTORY")
tmp=$(canonical_dir "$OPENGGF_TEST_TMP_ROOT")
diagnostics=$(canonical_dir "$OPENGGF_TEST_DIAGNOSTICS")
session_root=$(canonical_dir "$(dirname -- "$OPENGGF_TEST_MANIFEST")")
surefire_reports=$(canonical_dir "$session_root/surefire-reports")
trace_reports=$(canonical_dir "$session_root/trace-reports")
artifacts=$(canonical_dir "$OPENGGF_ARTIFACT_ROOT")
distribution=$(canonical_dir "$OPENGGF_DISTRIBUTION_ROOT")
marker="$diagnostics/frozen-next-session-recovery.env"
[[ ! -e "$marker" && ! -L "$marker" ]] || die "recovery marker already exists"

report="$worktree/$REPORT_RELATIVE"
[[ -f "$report" && ! -L "$report" ]] || die "historical generated report preimage is not a regular file"
[[ "$(git -C "$worktree" hash-object --no-filters "$report")" == "$REPORT_BLOB" ]] \
    || die "historical generated report preimage blob is invalid"
normalization_dir="$diagnostics/frozen-next-generated-report"
mkdir -- "$normalization_dir"
preimage_archive="$normalization_dir/original.md"
cp -- "$report" "$preimage_archive"
chmod --reference="$report" "$preimage_archive"
preimage_hash=$(sha256 "$preimage_archive")
preimage_length=$(byte_length "$preimage_archive")
[[ "$(git -C "$worktree" hash-object --no-filters "$preimage_archive")" == "$REPORT_BLOB" ]] \
    || die "historical generated report archive blob is invalid"
[[ "$(sha256 "$report")" == "$preimage_hash" \
    && "$(byte_length "$report")" == "$preimage_length" ]] \
    || die "historical generated report changed while its authority was archived"

report_authority="$diagnostics/frozen-next-report-authority.env"
authority_tmp="$diagnostics/.frozen-next-report-authority.env.tmp"
printf '%s\n' \
    "authority_version=1" "run_id=$OPENGGF_TEST_RUN_ID" "frozen_head=$FROZEN_NEXT_HEAD" \
    "worktree=$worktree" "report=$report" "report_relative=$REPORT_RELATIVE" \
    "preimage_archive=$preimage_archive" "preimage_hash=$preimage_hash" \
    "preimage_length=$preimage_length" "preimage_blob=$REPORT_BLOB" \
    "diagnostics_root=$diagnostics" "outer_uid=$outer_uid" "authenticated_home=$authenticated_home" \
    > "$authority_tmp"
mv -- "$authority_tmp" "$report_authority"

test_seam_marker="$diagnostics/frozen-next-test-seam.env"
test_seam_variables=
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
    OPENGGF_TEST_FAIL_GENERATED_ARCHIVE OPENGGF_TEST_FAIL_RESTORE \
    OPENGGF_TEST_REAL_CP OPENGGF_TEST_REAL_MV; do
    [[ -v "$test_seam_variable" ]] || continue
    test_seam_variables="${test_seam_variables:+$test_seam_variables,}$test_seam_variable"
done
if [[ -n "$test_seam_variables" ]]; then
    test_seam_mode=rejected-unmodeled
    [[ "${OPENGGF_FROZEN_NEXT_SELF_TEST_MODE:-}" == 1 ]] \
        && test_seam_mode=exact-self-test-v1
    printf 'run_id=%s\nmode=%s\nvariables=%s\n' \
        "$OPENGGF_TEST_RUN_ID" "$test_seam_mode" "$test_seam_variables" > "$test_seam_marker"
    [[ "$test_seam_mode" == exact-self-test-v1 ]] \
        || die "adapter test seam requires exact self-test mode"
fi

supervisor_pid= supervisor_start= private_pid1_pid= private_pid1_start=
private_pid_namespace= common_mount_namespace= target_created=0 finalized=0
target_kind= target_device= target_inode= child_status=not-started safety_phase=preflight
tripwire_trigger="$diagnostics/frozen-next-safety-failure.env"
tripwire_evidence="$diagnostics/frozen-next-identity-tripwire.env"
arm_identity_tripwire() {
    local reason=$1 child=$2 adapter_status=$3 current_hash current_length mode temporary
    local recorded_reason=${OPENGGF_TEST_TRIPWIRE_REASON_OVERRIDE:-$reason}
    printf 'run_id=%s\nreason=%s\nchild_status=%s\nadapter_status=%s\n' \
        "$OPENGGF_TEST_RUN_ID" "$recorded_reason" "$child" "$adapter_status" > "$tripwire_trigger"
    if [[ "${OPENGGF_TEST_TRIPWIRE_ARM_FAILURE:-}" == 1 ]]; then
        printf 'frozen-next adapter: identity tripwire arm failed by authenticated fixture\n' >&2
        return 78
    fi
    [[ -f "$report" && ! -L "$report" ]] || return 78
    current_hash=$(sha256 "$report")
    current_length=$(byte_length "$report")
    if [[ "$current_hash" == "$preimage_hash" && "$current_length" == "$preimage_length" ]]; then
        temporary=$(mktemp "$(dirname -- "$report")/.rewind-round-trip-gaps.tripwire.XXXXXX") || return 78
        if ! {
            printf '# Frozen-next adapter identity tripwire\n\n'
            printf 'run_id=%s\nreason=%s\nchild_status=%s\nadapter_status=%s\n' \
                "$OPENGGF_TEST_RUN_ID" "$recorded_reason" "$child" "$adapter_status"
        } > "$temporary" || ! chmod --reference="$report" "$temporary" \
            || ! mv -fT -- "$temporary" "$report"; then
            [[ ! -e "$temporary" && ! -L "$temporary" ]] || unlink -- "$temporary" 2>/dev/null || true
            return 78
        fi
        mode=deterministic-marker
    else
        mode=existing-dirty-report
    fi
    current_hash=$(sha256 "$report")
    current_length=$(byte_length "$report")
    [[ "$current_hash" != "$preimage_hash" || "$current_length" != "$preimage_length" ]] || return 78
    printf 'run_id=%s\nreason=%s\nchild_status=%s\nadapter_status=%s\nmode=%s\nreport_hash=%s\nreport_length=%s\n' \
        "$OPENGGF_TEST_RUN_ID" "$recorded_reason" "$child" "$adapter_status" "$mode" \
        "$current_hash" "$current_length" > "$tripwire_evidence"
}
cleanup_target() {
    local current_start cleanup_supervisor=$supervisor_pid cleanup_supervisor_start=$supervisor_start
    local cleanup_private_pid1=$private_pid1_pid cleanup_private_pid1_start=$private_pid1_start
    [[ "$target_created" == 1 ]] || return 0
    if [[ "${OPENGGF_ADAPTER_TEST_SHIM:-}" == 1 \
        && -n "${OPENGGF_TEST_CLEANUP_IDENTITY_PID:-}" ]]; then
        cleanup_supervisor=$OPENGGF_TEST_CLEANUP_IDENTITY_PID
        cleanup_supervisor_start=${OPENGGF_TEST_CLEANUP_IDENTITY_RECORDED_START:?}
        cleanup_private_pid1=$cleanup_supervisor
        cleanup_private_pid1_start=$cleanup_supervisor_start
    fi
    if [[ -n "$cleanup_supervisor" && -n "$cleanup_supervisor_start" \
        && -n "$cleanup_private_pid1" && -n "$cleanup_private_pid1_start" ]]; then
        current_start=$(process_start "$cleanup_supervisor" || true)
        if [[ "$current_start" == "$cleanup_supervisor_start" \
            && !( "${OPENGGF_ADAPTER_TEST_SHIM:-}" == 1 \
                && -n "${OPENGGF_TEST_CLEANUP_IDENTITY_PID:-}" ) ]]; then
            kill -KILL "$cleanup_supervisor" 2>/dev/null || true
            wait "$cleanup_supervisor" 2>/dev/null || true
        fi
        wait_process_pair_gone "$cleanup_supervisor" "$cleanup_supervisor_start" \
            "$cleanup_private_pid1" "$cleanup_private_pid1_start" || return 76
    elif [[ -n "$supervisor_pid" ]]; then
        kill -KILL "$supervisor_pid" 2>/dev/null || true
        wait "$supervisor_pid" 2>/dev/null || true
    fi
    [[ "$(lstat_kind "$target")" == directory \
        && "$(lstat_device "$target")" == "$target_device" \
        && "$(lstat_inode "$target")" == "$target_inode" ]] || return 73
    mountpoint -q -- "$target" && return 73
    directory_empty "$target" || return 73
    rmdir -- "$target" || return 73
    target_created=0
}
on_exit() {
    local status=$? cleanup_status=0 tripwire_status=0
    trap - EXIT INT TERM
    if (( finalized == 0 )) && (( status != 0 )); then
        arm_identity_tripwire "adapter-safety-$safety_phase" "$child_status" "$status" \
            || tripwire_status=$?
        cleanup_target || cleanup_status=$?
        finalized=1
        if (( cleanup_status != 0 )); then
            printf 'frozen-next adapter: authenticated target cleanup failed: status=%s target=%s\n' \
                "$cleanup_status" "$target" >&2
        fi
        (( tripwire_status == 0 )) || exit "$tripwire_status"
    elif (( finalized == 0 )); then
        cleanup_target || cleanup_status=$?
    fi
    if (( status != 0 )); then exit "$status"; fi
    exit "$cleanup_status"
}
on_signal() {
    local status=$1 tripwire_status=0
    trap - INT TERM
    arm_identity_tripwire "adapter-safety-signal" "$child_status" "$status" || tripwire_status=$?
    cleanup_target || true
    finalized=1
    (( tripwire_status == 0 )) || exit "$tripwire_status"
    exit "$status"
}
trap on_exit EXIT
trap 'on_signal 130' INT
trap 'on_signal 143' TERM

preflight="$diagnostics/frozen-next-mount-preflight"
mkdir -- "$preflight" "$preflight/source" "$preflight/target"
unshare --user --map-root-user --mount --pid --fork --kill-child=KILL bash -c '
    set -euo pipefail
    mount --make-rprivate /
    mount --bind "$1" "$2"
    mountpoint -q -- "$2"
    [[ "$(stat -Lc "%d:%i" -- "$1")" == "$(stat -Lc "%d:%i" -- "$2")" ]]
    umount -- "$2"
' bash "$preflight/source" "$preflight/target" || die "unprivileged bind-mount preflight failed"
rmdir -- "$preflight/target" "$preflight/source"
printf 'unprivileged_user_mount_namespace=passed\nbind_mount_identity=passed\n' > "$preflight/result.env"
safety_phase=mountpoint-setup
for mountpoint_name in test-tmp surefire-reports trace-reports diagnostics artifacts distribution; do
    mkdir -- "$build/$mountpoint_name"
done

safety_phase=target-create
mkdir -- "$target"
target_created=1
target_kind=$(lstat_kind "$target")
target_device=$(lstat_device "$target")
target_inode=$(lstat_inode "$target")
[[ "$target_kind" == directory && -n "$target_device" && -n "$target_inode" ]] || die "could not authenticate target mountpoint"
mountpoint -q -- "$target" && die "target unexpectedly mounted in parent namespace"
directory_empty "$target" || die "target mountpoint is not empty"
[[ -z "$(git -C "$worktree" status --porcelain --untracked-files=all)" ]] || die "target mountpoint changed frozen-next source inventory"
ignore_attribution=$(git -C "$worktree" check-ignore -v target)
[[ "$ignore_attribution" == *"$exclude"* ]] || die "external exclude does not own target"

marker_tmp="$diagnostics/.frozen-next-session-recovery.env.tmp"
printf '%s\n' \
    "marker_phase=target-authenticated" "report_authority=$report_authority" \
    "run_id=$OPENGGF_TEST_RUN_ID" "frozen_head=$FROZEN_NEXT_HEAD" "worktree=$worktree" \
    "report=$report" "report_relative=$REPORT_RELATIVE" "preimage_archive=$preimage_archive" \
    "preimage_hash=$preimage_hash" "preimage_length=$preimage_length" "preimage_blob=$REPORT_BLOB" \
    "target=$target" "target_kind=$target_kind" "target_device=$target_device" "target_inode=$target_inode" \
    "parent_expected_empty=true" "parent_expected_mount=false" "supervisor_pid=pending" \
    "supervisor_start=pending" "private_pid1_pid=pending" "private_pid1_start=pending" \
    "private_pid_namespace_inode=pending" "common_mount_namespace_inode=pending" "build_root=$build" \
    "outer_uid=$outer_uid" "authenticated_home=$authenticated_home" \
    "tmp_root=$tmp" "surefire_reports=$surefire_reports" "trace_reports=$trace_reports" \
    "diagnostics_root=$diagnostics" "artifact_root=$artifacts" "distribution_root=$distribution" \
    > "$marker_tmp"
mv -- "$marker_tmp" "$marker"

ready_fifo="$diagnostics/frozen-next-namespace-ready.fifo"
go_fifo="$diagnostics/frozen-next-namespace-go.fifo"
leader_info="$diagnostics/frozen-next-namespace-leader.env"
mount_evidence="$diagnostics/frozen-next-mount-evidence.txt"
path_evidence="$diagnostics/frozen-next-session-evidence.txt"
status_file="$diagnostics/frozen-next-namespace-status.env"
mkfifo -- "$ready_fifo" "$go_fifo"
exec {ready_fd}<>"$ready_fifo"
exec {go_fd}<>"$go_fifo"
namespace_token=$(printf '%s:%s:%s:%s' "$OPENGGF_TEST_RUN_ID" "$$" "$target_device" "$target_inode" | sha256sum | awk '{print $1}')
safety_phase=leader-start
OPENGGF_FROZEN_NAMESPACE_TOKEN="$namespace_token" OPENGGF_FROZEN_TARGET="$target" \
OPENGGF_FROZEN_AUTHENTICATED_HOME="$authenticated_home" OPENGGF_FROZEN_OUTER_UID="$outer_uid" \
OPENGGF_FROZEN_ADAPTER_PARENT_PID="$$" \
OPENGGF_FROZEN_SESSION_ROOT="$session_root" OPENGGF_FROZEN_READY_FIFO="$ready_fifo" \
OPENGGF_FROZEN_GO_FIFO="$go_fifo" OPENGGF_FROZEN_LEADER_INFO="$leader_info" \
OPENGGF_FROZEN_MOUNT_EVIDENCE="$mount_evidence" OPENGGF_FROZEN_PATH_EVIDENCE="$path_evidence" \
OPENGGF_FROZEN_STATUS_FILE="$status_file" \
unshare --user --map-root-user --mount --pid --fork --kill-child=KILL \
    "$adapter" --namespace-leader "$namespace_token" "$@" &
supervisor_pid=$!
supervisor_start=$(process_start "$supervisor_pid")
[[ -n "$supervisor_start" ]] || die "could not record namespace supervisor identity"

safety_phase=ready-barrier
ready_value=
IFS= read -r -t 30 -u "$ready_fd" ready_value || die "namespace leader did not reach ready barrier"
[[ "$ready_value" == ready && -f "$leader_info" && ! -L "$leader_info" ]] || die "namespace leader published invalid readiness evidence"
recorded_supervisor_pid=$(sed -n 's/^supervisor_host_pid=//p' "$leader_info")
private_pid1_pid=$(sed -n 's/^private_pid1_host_pid=//p' "$leader_info")
private_pid1_start=$(sed -n 's/^private_pid1_start=//p' "$leader_info")
private_pid1_nspid=$(sed -n 's/^private_pid1_nspid=//p' "$leader_info")
private_pid_namespace=$(sed -n 's/^private_pid_namespace_inode=//p' "$leader_info")
common_mount_namespace=$(sed -n 's/^common_mount_namespace_inode=//p' "$leader_info")
supervisor_children=$(process_children "$supervisor_pid")
set -- $supervisor_children
observed_supervisor_start=$(process_start "$supervisor_pid")
observed_private_pid1_start=$(process_start "$private_pid1_pid")
observed_supervisor_mount=$(mount_namespace_inode "$supervisor_pid")
observed_private_pid1_mount=$(mount_namespace_inode "$private_pid1_pid")
[[ -z "${OPENGGF_TEST_READY_SUPERVISOR_START_OVERRIDE:-}" ]] \
    || observed_supervisor_start=$OPENGGF_TEST_READY_SUPERVISOR_START_OVERRIDE
[[ -z "${OPENGGF_TEST_READY_PRIVATE_PID1_START_OVERRIDE:-}" ]] \
    || observed_private_pid1_start=$OPENGGF_TEST_READY_PRIVATE_PID1_START_OVERRIDE
if [[ -n "${OPENGGF_TEST_READY_COMMON_MOUNT_OVERRIDE:-}" ]]; then
    observed_supervisor_mount=$OPENGGF_TEST_READY_COMMON_MOUNT_OVERRIDE
    observed_private_pid1_mount=$OPENGGF_TEST_READY_COMMON_MOUNT_OVERRIDE
fi
[[ "$recorded_supervisor_pid" == "$supervisor_pid" \
    && "$observed_supervisor_start" == "$supervisor_start" \
    && $# == 1 && "$1" == "$private_pid1_pid" \
    && "$observed_private_pid1_start" == "$private_pid1_start" \
    && "$(process_nspid "$private_pid1_pid")" == "$private_pid1_nspid" \
    && "$private_pid1_nspid" == *$'\t1' \
    && "$(pid_namespace_inode "$private_pid1_pid")" == "$private_pid_namespace" \
    && "$observed_supervisor_mount" == "$common_mount_namespace" \
    && "$observed_private_pid1_mount" == "$common_mount_namespace" ]] \
    || die "namespace supervisor/private PID 1 identity changed before release"
parent_identity="$diagnostics/frozen-next-parent-process-identity.env"
printf 'supervisor_pid=%s\nsupervisor_start=%s\nprivate_pid1_pid=%s\nprivate_pid1_start=%s\n' \
    "$supervisor_pid" "$supervisor_start" "$private_pid1_pid" "$private_pid1_start" \
    > "$parent_identity"
printf 'private_pid1_nspid=%s\nprivate_pid_namespace_inode=%s\ncommon_mount_namespace_inode=%s\n' \
    "$private_pid1_nspid" "$private_pid_namespace" "$common_mount_namespace" >> "$parent_identity"
safety_phase=parent-view-proof
[[ "$(lstat_kind "$target")" == "$target_kind" && "$(lstat_device "$target")" == "$target_device" \
    && "$(lstat_inode "$target")" == "$target_inode" ]] || die "parent target identity changed at ready barrier"
mountpoint -q -- "$target" && die "private bind mount propagated into parent namespace"
directory_empty "$target" || die "parent target is not empty at ready barrier"
[[ -z "$(git -C "$worktree" status --porcelain --untracked-files=all)" ]] || die "source inventory changed at ready barrier"

printf '%s\n' \
    "marker_phase=ready-verified" "report_authority=$report_authority" \
    "run_id=$OPENGGF_TEST_RUN_ID" "frozen_head=$FROZEN_NEXT_HEAD" "worktree=$worktree" \
    "report=$report" "report_relative=$REPORT_RELATIVE" "preimage_archive=$preimage_archive" \
    "preimage_hash=$preimage_hash" "preimage_length=$preimage_length" "preimage_blob=$REPORT_BLOB" \
    "target=$target" "target_kind=$target_kind" "target_device=$target_device" "target_inode=$target_inode" \
    "parent_expected_empty=true" "parent_expected_mount=false" "supervisor_pid=$supervisor_pid" \
    "supervisor_start=$supervisor_start" "private_pid1_pid=$private_pid1_pid" \
    "private_pid1_start=$private_pid1_start" "private_pid1_nspid=$private_pid1_nspid" \
    "private_pid_namespace_inode=$private_pid_namespace" \
    "common_mount_namespace_inode=$common_mount_namespace" "parent_process_identity=$parent_identity" \
    "build_root=$build" \
    "outer_uid=$outer_uid" "authenticated_home=$authenticated_home" \
    "tmp_root=$tmp" "surefire_reports=$surefire_reports" "trace_reports=$trace_reports" \
    "diagnostics_root=$diagnostics" "artifact_root=$artifacts" "distribution_root=$distribution" \
    > "$marker_tmp"
mv -- "$marker_tmp" "$marker"
if [[ "${OPENGGF_ADAPTER_TEST_SHIM:-}" == 1 \
    && -n "${OPENGGF_TEST_CLEANUP_IDENTITY_PID:-}" ]]; then
    fixture_pid=$OPENGGF_TEST_CLEANUP_IDENTITY_PID
    fixture_actual_start=${OPENGGF_TEST_CLEANUP_IDENTITY_ACTUAL_START:?}
    fixture_recorded_start=${OPENGGF_TEST_CLEANUP_IDENTITY_RECORDED_START:?}
    [[ "$fixture_pid" =~ ^[0-9]+$ && "$fixture_actual_start" =~ ^[0-9]+$ \
        && "$fixture_recorded_start" =~ ^[0-9]+$ \
        && "$(process_start "$fixture_pid")" == "$fixture_actual_start" ]] \
        || die "controlled cleanup identity fixture is not live and exact"
    sed -e "s/^supervisor_pid=.*/supervisor_pid=$fixture_pid/" \
        -e "s/^supervisor_start=.*/supervisor_start=$fixture_recorded_start/" \
        -e "s/^private_pid1_pid=.*/private_pid1_pid=$fixture_pid/" \
        -e "s/^private_pid1_start=.*/private_pid1_start=$fixture_recorded_start/" \
        "$marker" > "$marker_tmp"
    mv -- "$marker_tmp" "$marker"
    parent_identity_tmp="$diagnostics/.frozen-next-parent-process-identity.env.tmp"
    sed -e "s/^supervisor_pid=.*/supervisor_pid=$fixture_pid/" \
        -e "s/^supervisor_start=.*/supervisor_start=$fixture_recorded_start/" \
        -e "s/^private_pid1_pid=.*/private_pid1_pid=$fixture_pid/" \
        -e "s/^private_pid1_start=.*/private_pid1_start=$fixture_recorded_start/" \
        "$parent_identity" > "$parent_identity_tmp"
    mv -- "$parent_identity_tmp" "$parent_identity"
fi
safety_phase=coordinator-release
printf 'go\n' >&"$go_fd"
exec {ready_fd}>&-
exec {go_fd}>&-
unlink -- "$ready_fifo"
unlink -- "$go_fifo"
printf 'ready=verified\nparent_target_kind=directory\nparent_target_mount=false\ngo=released\n' >> "$mount_evidence"

safety_phase=namespace-teardown
set +e
wait "$supervisor_pid"
leader_status=$?
set -e
wait_process_pair_gone "$supervisor_pid" "$supervisor_start" \
    "$private_pid1_pid" "$private_pid1_start" \
    || die "namespace supervisor or private PID 1 survived teardown"
printf 'target_ignore_attribution=%s\n' "$ignore_attribution" >> "$path_evidence"
[[ -f "$status_file" && ! -L "$status_file" ]] || die "namespace leader omitted child status evidence"
child_status=$(sed -n 's/^child_status=//p' "$status_file")
path_failure=$(sed -n 's/^path_failure=//p' "$status_file")
[[ "$child_status" =~ ^[0-9]+$ && "$path_failure" =~ ^[01]$ ]] || die "invalid namespace status evidence"
if (( leader_status != child_status && !(path_failure != 0 && child_status == 0 && leader_status == 69) )); then
    printf 'frozen-next adapter: namespace teardown changed child status: child=%s leader=%s\n' \
        "$child_status" "$leader_status" >&2
    tripwire_status=0
    arm_identity_tripwire namespace-teardown "$child_status" "$leader_status" || tripwire_status=$?
    cleanup_status=0
    cleanup_target || cleanup_status=$?
    finalized=1
    if (( cleanup_status != 0 )); then
        printf 'frozen-next adapter: authenticated target cleanup failed: status=%s target=%s\n' \
            "$cleanup_status" "$target" >&2
    fi
    (( tripwire_status == 0 )) || exit "$tripwire_status"
    if (( child_status != 0 )); then exit "$child_status"; fi
    exit "$leader_status"
fi

shopt -s nullglob
reports=("$surefire_reports"/TEST-*.xml)
shopt -u nullglob
status_inventory="$normalization_dir/source-inventory-before-normalization.txt"
git -C "$worktree" status --porcelain --untracked-files=all > "$status_inventory"
unexpected_inventory="$normalization_dir/unexpected-source-inventory.txt"
: > "$unexpected_inventory"
while IFS= read -r status_line; do
    [[ -n "$status_line" ]] || continue
    status_path=${status_line:3}
    [[ "$status_path" == "$REPORT_RELATIVE" && "${status_line:0:2}" != '??' ]] || printf '%s\n' "$status_line" >> "$unexpected_inventory"
done < "$status_inventory"

normalization_status=not-required
generated_hash= generated_length= probe_record=
if [[ ! -f "$report" || -L "$report" ]]; then
    normalization_status=invalid-report-type
elif [[ "$(sha256 "$report")" != "$preimage_hash" || "$(byte_length "$report")" != "$preimage_length" ]]; then
    generated_hash=$(sha256 "$report")
    generated_length=$(byte_length "$report")
    if (( path_failure != 0 )); then
        normalization_status=path-validation-failed
    elif ! rg -Fx '# Rewind Round-Trip Probe' "$report" >/dev/null \
        || ! rg -Fx '## Summary' "$report" >/dev/null || ! rg -F '| Probed: |' "$report" >/dev/null \
        || ! rg -F '| Skipped/Unprobed: |' "$report" >/dev/null; then
        normalization_status=invalid-generated-shape
    elif [[ -s "$unexpected_inventory" ]]; then
        normalization_status=unexpected-source-mutation
    elif ! probe_record=$(probe_outcome "${reports[@]}"); then
        normalization_status=missing-authenticated-probe-outcome
    else
        generated_archive="$normalization_dir/generated.md"
        if ! cp -- "$report" "$generated_archive" || [[ ! -f "$generated_archive" || -L "$generated_archive" ]]; then
            normalization_status=generated-archive-failed
        elif [[ "$(sha256 "$generated_archive")" != "$generated_hash" \
            || "$(byte_length "$generated_archive")" != "$generated_length" ]]; then
            normalization_status=generated-archive-identity-failed
        elif ! atomic_restore "$preimage_archive" "$report" "$preimage_hash" "$preimage_length"; then
            normalization_status=restore-failed
        else
            normalization_status=restored
        fi
    fi
elif [[ -s "$unexpected_inventory" ]]; then
    normalization_status=unexpected-source-mutation
elif (( path_failure != 0 )); then
    normalization_status=path-validation-failed
fi

metadata="$normalization_dir/metadata.env"
{
    printf 'run_id=%s\npath=%s\n' "$OPENGGF_TEST_RUN_ID" "$report"
    printf 'original_hash=%s\noriginal_length=%s\n' "$preimage_hash" "$preimage_length"
    printf 'generated_hash=%s\ngenerated_length=%s\n' "$generated_hash" "$generated_length"
    printf 'probe_record=%s\nrestoration_status=%s\n' "$probe_record" "$normalization_status"
    printf 'path_validation=%s\n' "$([[ $path_failure == 0 ]] && printf passed || printf failed)"
} > "$metadata"

normalization_failure=0
case "$normalization_status" in restored|not-required) ;; *) normalization_failure=1 ;; esac
cleanup_status=0
cleanup_target || cleanup_status=$?
finalized=1
if (( cleanup_status != 0 )); then
    printf 'frozen-next adapter: authenticated target cleanup failed: status=%s target=%s\n' "$cleanup_status" "$target" >&2
    tripwire_status=0
    arm_identity_tripwire target-cleanup "$child_status" "$cleanup_status" || tripwire_status=$?
    (( tripwire_status == 0 )) || exit "$tripwire_status"
    if (( child_status != 0 )); then exit "$child_status"; fi
    exit "$cleanup_status"
fi
if (( normalization_failure != 0 )); then
    printf 'frozen-next adapter: normalization failed: %s path_validation=%s\n' "$normalization_status" "$path_failure" >&2
    if (( child_status != 0 )); then exit "$child_status"; fi
    exit 70
fi
exit "$child_status"
