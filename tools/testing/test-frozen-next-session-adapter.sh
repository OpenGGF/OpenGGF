#!/usr/bin/env bash
# Exercises the frozen-next compatibility adapter against the immutable next parent.
set -euo pipefail

if [[ "${OPENGGF_ADAPTER_TEST_SHIM:-}" == 1 ]]; then
    shim_name=$(basename -- "$0")
    case "$shim_name" in
        unshare)
            if [[ "${OPENGGF_TEST_FAIL_PREFLIGHT:-}" == 1 && "${4:-}" == bash ]]; then
                printf 'controlled unshare preflight failure\n' >&2
                exit 68
            fi
            exec "${OPENGGF_TEST_REAL_UNSHARE:?}" "$@"
            ;;
        mount)
            mount_target=${*: -1}
            if [[ "${OPENGGF_TEST_FAIL_BIND:-}" == 1 && " $* " == *' --bind '* \
                && "$mount_target" == "${OPENGGF_TEST_WORKTREE:?}/target" ]]; then
                printf 'controlled bind failure: %s\n' "$mount_target" >&2
                exit 72
            fi
            exec "${OPENGGF_TEST_REAL_MOUNT:?}" "$@"
            ;;
        stat)
            stat_path=${*: -1}
            if [[ "${OPENGGF_TEST_WRONG_MOUNT_IDENTITY:-}" == 1 \
                && " $* " == *" -Lc %d:%i "* \
                && "$stat_path" == "${OPENGGF_TEST_WORKTREE:?}/target" ]]; then
                printf '0:0\n'
                exit 0
            fi
            exec "${OPENGGF_TEST_REAL_STAT:?}" "$@"
            ;;
        mountpoint)
            mountpoint_target=${*: -1}
            if [[ "${OPENGGF_TEST_PROPAGATION_LEAK:-}" == 1 \
                && "$mountpoint_target" == "${OPENGGF_TEST_WORKTREE:?}/target" \
                && -f "${OPENGGF_TEST_DIAGNOSTICS:?}/frozen-next-namespace-leader.env" ]]; then
                exit 0
            fi
            exec "${OPENGGF_TEST_REAL_MOUNTPOINT:?}" "$@"
            ;;
        *) ;;
    esac
fi

if [[ "${1:-}" == --contained-nested ]]; then
    evidence=${2:?nested containment evidence required}
    nested_host= nested_start=
    while IFS=: read -r key value; do
        if [[ "$key" == NSpid ]]; then
            value=${value#[$' \t']}
            read -r nested_host _ <<< "$value"
            break
        fi
    done < /proc/self/status
    nested_start=$(sed 's/^[^)]*) //' "/proc/$nested_host/stat" | awk '{print $20}')
    nested_mount=$(stat -Lc %i /proc/self/ns/mnt)
    printf 'pid=%s\nstart=%s\nmount_namespace_inode=%s\n' \
        "$nested_host" "$nested_start" "$nested_mount" > "$evidence"
    sleep 300
    exit 0
fi

if [[ "${OPENGGF_FAKE_MAVEN:-}" == 1 ]]; then
    if [[ " $* " == *' help:evaluate '* ]]; then
        if [[ "${OPENGGF_TEST_RESOLVED_USER_HOME:-}" == 1 ]]; then
            printf '%s\n' '-Xshare:off -Duser.home=/attacker -Xmx1g'
            exit 0
        fi
        printf '%s\n' '-Xshare:off -Xmx1g'
        exit 0
    fi
    if [[ " $* " == *' --mutate '* ]]; then
        mutation=${OPENGGF_TEST_MUTATION_INPUT:?}
        printf 'changed\n' >> "$mutation"
    fi
    if [[ " $* " == *' --emit-report '* ]]; then
        report_root="${OPENGGF_TEST_WORKTREE:?}/target/surefire-reports"
        mkdir -p "$report_root"
        temp_value="${OPENGGF_TEST_WORKTREE:?}/target/test-tmp"
        lwjgl_value="${OPENGGF_TEST_TMP_ROOT:?}/lwjgl-1"
        home_value="${OPENGGF_FROZEN_AUTHENTICATED_HOME:?}"
        mkdir -p "$temp_value" "$lwjgl_value"
        printf '%s\n' \
            '<?xml version="1.0" encoding="UTF-8"?>' \
            '<testsuite name="com.openggf.game.rewind.coverage.TestRewindRoundTripProbe" tests="1" errors="0" skipped="0" failures="0">' \
            '  <properties>' \
            "    <property name=\"user.home\" value=\"$home_value\"/>" \
            "    <property name=\"java.io.tmpdir\" value=\"$temp_value\"/>" \
            "    <property name=\"org.lwjgl.system.SharedLibraryExtractPath\" value=\"$lwjgl_value\"/>" \
            '  </properties>' \
            '  <testcase name="probeReportIsWrittenToDisk" classname="com.openggf.game.rewind.coverage.TestRewindRoundTripProbe" time="0.001"/>' \
            '</testsuite>' \
            > "$report_root/TEST-com.openggf.game.rewind.coverage.TestRewindRoundTripProbe.xml"
    fi
    if [[ " $* " == *' --assert-mount-topology '* ]]; then
        target="${OPENGGF_TEST_WORKTREE:?}/target"
        mountpoint -q -- "$target" || exit 61
        [[ -d "$target" && ! -L "$target" ]] || exit 62
        for mapping in \
            "${OPENGGF_BUILD_DIRECTORY:?}:$target" \
            "${OPENGGF_TEST_TMP_ROOT:?}:$target/test-tmp" \
            "$(dirname -- "${OPENGGF_TEST_MANIFEST:?}")/surefire-reports:$target/surefire-reports" \
            "$(dirname -- "${OPENGGF_TEST_MANIFEST:?}")/trace-reports:$target/trace-reports" \
            "${OPENGGF_TEST_DIAGNOSTICS:?}:$target/diagnostics" \
            "${OPENGGF_ARTIFACT_ROOT:?}:$target/artifacts" \
            "${OPENGGF_DISTRIBUTION_ROOT:?}:$target/distribution"; do
            source=${mapping%%:*}
            mounted=${mapping#*:}
            mountpoint -q -- "$mounted" || exit 63
            [[ "$(stat -Lc '%d:%i' -- "$source")" == "$(stat -Lc '%d:%i' -- "$mounted")" ]] \
                || exit 64
        done
        [[ "$(id -u)" == 0 \
            && "${OPENGGF_FROZEN_AUTHENTICATED_HOME:?}" == "$(readlink -f -- "$HOME")" ]] \
            || exit 65
    fi
    if [[ " $* " == *' --rewrite-report '* ]]; then
        report="${OPENGGF_TEST_WORKTREE:?}/docs/status/rewind-round-trip-gaps.md"
        printf '%s\n' \
            '# Rewind Round-Trip Probe' \
            '' \
            'Generated: 2099-01-01' \
            '' \
            '## Summary' \
            '' \
            '| Metric | Value |' \
            '|--------|-------|' \
            '| Total classes discovered | 1 |' \
            '| Probed: | 1 |' \
            '| Skipped/Unprobed: | 0 |' \
            '| Probe coverage | 100.0% |' \
            '| REAL gaps found | 0 |' \
            > "$report"
    fi
    if [[ " $* " == *' --second-source-mutation '* ]]; then
        printf '\ncontrolled second mutation\n' >> "${OPENGGF_TEST_WORKTREE:?}/README.md"
    fi
    if [[ " $* " == *' --missing-report '* ]]; then
        unlink -- "${OPENGGF_TEST_WORKTREE:?}/docs/status/rewind-round-trip-gaps.md"
    fi
    if [[ " $* " == *' --symlink-report '* ]]; then
        report="${OPENGGF_TEST_WORKTREE:?}/docs/status/rewind-round-trip-gaps.md"
        unlink -- "$report"
        ln -s -- README.md "$report"
    fi
    if [[ " $* " == *' --directory-report '* ]]; then
        report="${OPENGGF_TEST_WORKTREE:?}/docs/status/rewind-round-trip-gaps.md"
        unlink -- "$report"
        mkdir -- "$report"
    fi
    if [[ " $* " == *' --kill-adapter '* ]]; then
        kill -KILL "${OPENGGF_FROZEN_ADAPTER_PARENT_PID:?}"
        sleep 2
    fi
    if [[ " $* " == *' --wait-for-parent-mutation '* ]]; then
        printf 'manifest=%s\ntarget=%s\nadapter_pid=%s\n' "$OPENGGF_TEST_MANIFEST" \
            "$OPENGGF_TEST_WORKTREE/target" "${OPENGGF_FROZEN_ADAPTER_PARENT_PID:?}" \
            > "${OPENGGF_TEST_PARENT_MUTATION_READY:?}"
        for _ in {1..400}; do
            [[ -f "${OPENGGF_TEST_PARENT_MUTATION_GO:?}" ]] && break
            sleep 0.05
        done
        [[ -f "$OPENGGF_TEST_PARENT_MUTATION_GO" ]] || exit 66
    fi
    if [[ " $* " == *' --wait-for-launcher-interrupt '* ]]; then
        printf 'fake_pid=%s\nadapter_pid=%s\nmanifest=%s\n' \
            "$$" "$PPID" "$OPENGGF_TEST_MANIFEST" > "${OPENGGF_TEST_INTERRUPT_READY:?}"
        while :; do sleep 1; done
    fi
    if [[ " $* " == *' --spawn-contained-descendants '* ]]; then
        detached_evidence="${OPENGGF_TEST_DIAGNOSTICS:?}/frozen-next-contained-detached.env"
        nested_evidence="${OPENGGF_TEST_DIAGNOSTICS:?}/frozen-next-contained-nested.env"
        setsid perl -e '
            use strict; use warnings;
            my $file = shift;
            my $p = fork(); die "fork1: $!" unless defined $p; exit 0 if $p;
            my $q = fork(); die "fork2: $!" unless defined $q; exit 0 if $q;
            open my $s, "<", "/proc/self/status" or die $!;
            my $host;
            while (<$s>) { if (/^NSpid:\s+(\d+)/) { $host=$1; last } }
            open my $stat, "<", "/proc/$host/stat" or die $!;
            my $line = <$stat>; $line =~ s/^[^)]*\) //;
            my @fields = split / /, $line;
            my $start = $fields[19];
            open my $f, ">", $file or die $!;
            print {$f} "pid=$host\nstart=$start\n"; close $f;
            sleep 300;
        ' "$detached_evidence" </dev/null >/dev/null 2>&1 &
        unshare --mount --fork "$0" --contained-nested "$nested_evidence" \
            </dev/null >/dev/null 2>&1 &
        for _ in {1..200}; do
            [[ -s "$detached_evidence" && -s "$nested_evidence" ]] && break
            sleep 0.05
        done
        [[ -s "$detached_evidence" && -s "$nested_evidence" ]] || exit 67
        if [[ " $* " == *' --wait-contained '* ]]; then
            printf 'manifest=%s\n' "$OPENGGF_TEST_MANIFEST" > "${OPENGGF_TEST_CONTAINED_READY:?}"
            while :; do sleep 1; done
        fi
    fi
    [[ " $* " == *' --fail '* ]] && exit 23
    exit 0
fi

script_dir="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)"
project_root="$(CDPATH= cd -- "$script_dir/../.." && pwd -P)"
launcher="$script_dir/frozen-next-session-launch.sh"
adapter="$script_dir/frozen-next-session-adapter.sh"
fake_maven="$script_dir/test-frozen-next-session-adapter.sh"
exclude="$script_dir/frozen-next-session.exclude"
frozen_next="84d9a3761f618035dd1caa40a3d5fc72a1019693"
frozen_harness="a17adaba5b57298ffd88c6d7b6ab3a4d6aff87bb"

fail() {
    printf 'FAIL: %s\n' "$*" >&2
    exit 1
}
review_failures=0
review_fail() {
    printf 'EXPECTED RED: %s\n' "$*" >&2
    review_failures=$((review_failures + 1))
}

[[ -x "$launcher" ]] || fail "frozen-next-session-launch.sh missing or not executable"
[[ -x "$adapter" ]] || fail "frozen-next-session-adapter.sh missing or not executable"
[[ "$(<"$exclude")" == "/target" ]] || fail "external exclude must contain exactly /target"

scratch_root="${OPENGGF_FROZEN_NEXT_ADAPTER_TEST_ROOT:-$(mktemp -d "${TMPDIR:-/tmp}/openggf-frozen-next-adapter.XXXXXX")}"
mkdir -p "$scratch_root"
test_root="$scratch_root/run-$$"
next_tree="$test_root/next"
harness_tree="$test_root/harness"
fixture_pids=()
cleanup_fixtures() {
    local pid
    for pid in "${fixture_pids[@]}"; do kill -KILL "$pid" 2>/dev/null || true; done
    git worktree remove --force "$next_tree" 2>/dev/null || true
    git worktree remove --force "$harness_tree" 2>/dev/null || true
}
trap cleanup_fixtures EXIT

mkdir -p "$test_root"
git cowtree add --from "$project_root" --detach "$next_tree" "$frozen_next" >/dev/null
git cowtree add --from "$project_root" --detach "$harness_tree" "$frozen_harness" >/dev/null
[[ "$(git -C "$next_tree" rev-parse HEAD)" == "$frozen_next" ]] || fail "next cowtree HEAD changed"
[[ "$(git -C "$harness_tree" rev-parse HEAD)" == "$frozen_harness" ]] || fail "harness cowtree HEAD changed"

wrapper="$harness_tree/tools/testing/test-session.sh"
coordinator="$harness_tree/tools/testing/TestSessionCoordinator.java"
run_launcher() {
    set +e
    launch_output=$("$launcher" --worktree "$next_tree" --expected-head "$frozen_next" \
        --harness-worktree "$harness_tree" --expected-harness-head "$frozen_harness" \
        --wrapper "$wrapper" --coordinator "$coordinator" --adapter "$adapter" -- "$@" 2>&1)
    launch_status=$?
    set -e
    printf '%s\n' "$launch_output"
    return "$launch_status"
}
manifest_from_output() {
    printf '%s\n' "$1" | sed -n 's/.*manifest=\([^ ]*\).*/\1/p' | tail -1
}
restore_frozen_file() {
    local relative=$1 destination="$next_tree/$1"
    if [[ -L "$destination" ]]; then
        unlink -- "$destination"
    elif [[ -d "$destination" ]]; then
        rmdir -- "$destination"
    fi
    git -C "$next_tree" show "$frozen_next:$relative" > "$destination"
}
assert_session_outcome() {
    local expected_state=$1 expected_valid=$2
    local outcome_manifest
    outcome_manifest=$(manifest_from_output "$launch_output")
    [[ -f "$outcome_manifest" ]] || fail "session did not publish a manifest for $expected_state"
    rg -F "\"state\": \"$expected_state\"" "$outcome_manifest" >/dev/null \
        || fail "manifest did not record state $expected_state"
    [[ "$launch_output" == *"valid=$expected_valid"* ]] \
        || fail "terminal marker did not record valid=$expected_valid for $expected_state"
}
assert_report_restored() {
    [[ -f "$next_tree/docs/status/rewind-round-trip-gaps.md" \
        && ! -L "$next_tree/docs/status/rewind-round-trip-gaps.md" ]] \
        || fail "historical report is not a restored regular file"
    [[ "$(git -C "$next_tree" hash-object --no-filters docs/status/rewind-round-trip-gaps.md)" \
        == d83614ec3a32abd1d6636d2be247ade01331bf3c ]] \
        || fail "historical report bytes were not restored"
}
recorded_identity_gone() {
    local evidence=$1 pid start current_start
    pid=$(sed -n 's/^pid=//p' "$evidence")
    start=$(sed -n 's/^start=//p' "$evidence")
    [[ "$pid" =~ ^[0-9]+$ && "$start" =~ ^[0-9]+$ ]] || return 1
    current_start=$(sed 's/^[^)]*) //' "/proc/$pid/stat" 2>/dev/null | awk '{print $20}' || true)
    [[ "$current_start" != "$start" ]]
}
test_pid_containment() {
    local containment_manifest containment_diagnostics marker common_mount nested_mount evidence
    OPENGGF_FAKE_MAVEN=1 run_launcher "$fake_maven" --assert-mount-topology --spawn-contained-descendants \
        || fail "normal PID containment run failed"
    containment_manifest=$(manifest_from_output "$launch_output")
    containment_diagnostics="$(dirname -- "$containment_manifest")/diagnostics"
    marker="$containment_diagnostics/frozen-next-session-recovery.env"
    common_mount=$(sed -n 's/^common_mount_namespace_inode=//p' "$marker")
    nested_mount=$(sed -n 's/^mount_namespace_inode=//p' \
        "$containment_diagnostics/frozen-next-contained-nested.env")
    [[ "$common_mount" =~ ^[0-9]+$ && "$nested_mount" =~ ^[0-9]+$ \
        && "$nested_mount" != "$common_mount" ]] \
        || fail "nested descendant did not prove a distinct mount namespace"
    for evidence in "$containment_diagnostics/frozen-next-contained-detached.env" \
        "$containment_diagnostics/frozen-next-contained-nested.env"; do
        recorded_identity_gone "$evidence" || fail "normal PID containment left $(basename -- "$evidence") alive"
    done
    [[ "$launch_output" == *'authenticated=true admissible=true'* ]] \
        || fail "normal PID containment lacked authenticated admission"
    [[ ! -e "$next_tree/target" && ! -L "$next_tree/target" ]] \
        || fail "normal PID containment left target"
    printf 'PASS: normal PID1 exit contains detached and nested-mount descendants\n'

    local ready="$test_root/forced-containment-ready.env" output="$test_root/forced-containment.out"
    local launcher_pid forced_status forced_manifest forced_diagnostics supervisor_pid supervisor_start
    set +e
    OPENGGF_FAKE_MAVEN=1 OPENGGF_TEST_CONTAINED_READY="$ready" \
        "$launcher" --worktree "$next_tree" --expected-head "$frozen_next" \
        --harness-worktree "$harness_tree" --expected-harness-head "$frozen_harness" \
        --wrapper "$wrapper" --coordinator "$coordinator" --adapter "$adapter" -- \
        "$fake_maven" --spawn-contained-descendants --wait-contained > "$output" 2>&1 &
    launcher_pid=$!
    set -e
    for _ in {1..400}; do [[ -s "$ready" ]] && break; sleep 0.05; done
    [[ -s "$ready" ]] || fail "forced containment did not reach descendant-ready barrier"
    forced_manifest=$(sed -n 's/^manifest=//p' "$ready")
    forced_diagnostics="$(dirname -- "$forced_manifest")/diagnostics"
    marker="$forced_diagnostics/frozen-next-session-recovery.env"
    [[ -f "$marker" ]] || fail "forced containment omitted recovery marker"
    supervisor_pid=$(sed -n 's/^supervisor_pid=//p' "$marker")
    supervisor_start=$(sed -n 's/^supervisor_start=//p' "$marker")
    [[ "$supervisor_pid" =~ ^[0-9]+$ && "$supervisor_start" =~ ^[0-9]+$ \
        && "$(sed 's/^[^)]*) //' "/proc/$supervisor_pid/stat" | awk '{print $20}')" == "$supervisor_start" ]] \
        || fail "forced containment supervisor identity was not live and exact"
    kill -KILL "$supervisor_pid"
    set +e
    wait "$launcher_pid"
    forced_status=$?
    set -e
    (( forced_status != 0 )) || fail "forced supervisor death was accepted"
    launch_output=$(<"$output")
    [[ "$launch_output" == *'state=INVALID_IDENTITY_CHANGED valid=false'* \
        && "$launch_output" == *'authenticated=true admissible=false'* ]] \
        || fail "forced supervisor death lacked authenticated invalid outcome"
    for evidence in "$forced_diagnostics/frozen-next-contained-detached.env" \
        "$forced_diagnostics/frozen-next-contained-nested.env"; do
        recorded_identity_gone "$evidence" || fail "forced supervisor death left $(basename -- "$evidence") alive"
    done
    assert_report_restored
    [[ ! -e "$next_tree/target" && ! -L "$next_tree/target" ]] \
        || fail "forced supervisor death left target"
    printf 'PASS: forced supervisor death contains detached and nested-mount descendants\n'
}
test_namespace_safety_negatives() {
    local shim_dir="$test_root/namespace-safety-bin" real_unshare real_mount real_stat real_mountpoint
    local negative manifest diagnostics log
    mkdir -- "$shim_dir"
    real_unshare=$(command -v unshare)
    real_mount=$(command -v mount)
    real_stat=$(command -v stat)
    real_mountpoint=$(command -v mountpoint)
    for negative in unshare mount stat mountpoint; do ln -s -- "$fake_maven" "$shim_dir/$negative"; done

    for negative in bind wrong-identity propagation published-pid-namespace \
        ready-supervisor-start ready-pid1-start ready-common-mount; do
        case "$negative" in
            bind)
                PATH="$shim_dir:$PATH" OPENGGF_FROZEN_NEXT_SELF_TEST_MODE=1 \
                    OPENGGF_ADAPTER_TEST_SHIM=1 OPENGGF_TEST_FAIL_BIND=1 \
                    OPENGGF_TEST_REAL_UNSHARE="$real_unshare" OPENGGF_TEST_REAL_MOUNT="$real_mount" \
                    OPENGGF_TEST_REAL_STAT="$real_stat" OPENGGF_TEST_REAL_MOUNTPOINT="$real_mountpoint" \
                    OPENGGF_FAKE_MAVEN=1 run_launcher "$fake_maven" && fail "bind failure was accepted"
                ;;
            wrong-identity)
                PATH="$shim_dir:$PATH" OPENGGF_FROZEN_NEXT_SELF_TEST_MODE=1 \
                    OPENGGF_ADAPTER_TEST_SHIM=1 OPENGGF_TEST_WRONG_MOUNT_IDENTITY=1 \
                    OPENGGF_TEST_REAL_UNSHARE="$real_unshare" OPENGGF_TEST_REAL_MOUNT="$real_mount" \
                    OPENGGF_TEST_REAL_STAT="$real_stat" OPENGGF_TEST_REAL_MOUNTPOINT="$real_mountpoint" \
                    OPENGGF_FAKE_MAVEN=1 run_launcher "$fake_maven" && fail "wrong mount identity was accepted"
                ;;
            propagation)
                PATH="$shim_dir:$PATH" OPENGGF_FROZEN_NEXT_SELF_TEST_MODE=1 \
                    OPENGGF_ADAPTER_TEST_SHIM=1 OPENGGF_TEST_PROPAGATION_LEAK=1 \
                    OPENGGF_TEST_REAL_UNSHARE="$real_unshare" OPENGGF_TEST_REAL_MOUNT="$real_mount" \
                    OPENGGF_TEST_REAL_STAT="$real_stat" OPENGGF_TEST_REAL_MOUNTPOINT="$real_mountpoint" \
                    OPENGGF_FAKE_MAVEN=1 run_launcher "$fake_maven" && fail "propagation leak was accepted"
                ;;
            published-pid-namespace)
                OPENGGF_FROZEN_NEXT_SELF_TEST_MODE=1 \
                    OPENGGF_TEST_PUBLISHED_PID_NAMESPACE_OVERRIDE=1 OPENGGF_FAKE_MAVEN=1 \
                    run_launcher "$fake_maven" && fail "published PID namespace mismatch was accepted"
                ;;
            ready-supervisor-start)
                OPENGGF_FROZEN_NEXT_SELF_TEST_MODE=1 \
                    OPENGGF_TEST_READY_SUPERVISOR_START_OVERRIDE=1 OPENGGF_FAKE_MAVEN=1 \
                    run_launcher "$fake_maven" && fail "ready supervisor start mismatch was accepted"
                ;;
            ready-pid1-start)
                OPENGGF_FROZEN_NEXT_SELF_TEST_MODE=1 \
                    OPENGGF_TEST_READY_PRIVATE_PID1_START_OVERRIDE=1 OPENGGF_FAKE_MAVEN=1 \
                    run_launcher "$fake_maven" && fail "ready PID1 start mismatch was accepted"
                ;;
            ready-common-mount)
                OPENGGF_FROZEN_NEXT_SELF_TEST_MODE=1 \
                    OPENGGF_TEST_READY_COMMON_MOUNT_OVERRIDE=1 OPENGGF_FAKE_MAVEN=1 \
                    run_launcher "$fake_maven" && fail "ready common mount mismatch was accepted"
                ;;
        esac
        manifest=$(manifest_from_output "$launch_output")
        [[ -f "$manifest" ]] || fail "$negative safety failure omitted manifest"
        diagnostics="$(dirname -- "$manifest")/diagnostics"
        log="$(dirname -- "$manifest")/maven.log"
        rg -F '"state": "INVALID_IDENTITY_CHANGED"' "$manifest" >/dev/null \
            || fail "$negative safety failure remained superficially valid"
        [[ "$launch_output" == *'authenticated=true admissible=false'* ]] \
            || fail "$negative safety failure lacked authenticated rejection"
        [[ -f "$diagnostics/frozen-next-identity-tripwire.env" ]] \
            || fail "$negative safety failure omitted tripwire"
        case "$negative" in
            bind) rg -F 'bind mount failed' "$log" >/dev/null || fail "bind failure lacked diagnostic" ;;
            wrong-identity) rg -F 'bind identity mismatch' "$log" >/dev/null || fail "mount identity failure lacked diagnostic" ;;
            propagation) rg -F 'private bind mount propagated' "$log" >/dev/null || fail "propagation failure lacked diagnostic" ;;
            published-pid-namespace|ready-supervisor-start|ready-pid1-start|ready-common-mount)
                rg -F 'supervisor/private PID 1 identity changed' "$log" >/dev/null \
                    || fail "$negative identity failure lacked diagnostic"
                ;;
        esac
        assert_report_restored
        if [[ -e "$next_tree/target" || -L "$next_tree/target" ]]; then
            [[ "$negative" == propagation && -d "$next_tree/target" && ! -L "$next_tree/target" \
                && -z "$(find "$next_tree/target" -mindepth 1 -maxdepth 1 -print -quit)" ]] \
                || fail "$negative left an unexpected target"
            "$real_mountpoint" -q -- "$next_tree/target" && fail "propagation fixture leaked a real parent mount"
            rmdir -- "$next_tree/target"
        fi
    done
    printf 'PASS: bind, mount identity, propagation, and ready PID-identity failures reject safely\n'
}
test_recovery_marker_identity_mismatch() {
    local ready="$test_root/marker-mismatch-ready.env" go="$test_root/marker-mismatch-go"
    local output launcher_pid status manifest diagnostics marker parent_identity temporary mismatch
    for mismatch in supervisor_start private_pid1_start private_pid_namespace_inode common_mount_namespace_inode; do
        ready="$test_root/marker-$mismatch-ready.env"
        go="$test_root/marker-$mismatch-go"
        output="$test_root/marker-$mismatch.out"
        set +e
        OPENGGF_FAKE_MAVEN=1 OPENGGF_TEST_PARENT_MUTATION_READY="$ready" \
            OPENGGF_TEST_PARENT_MUTATION_GO="$go" \
            "$launcher" --worktree "$next_tree" --expected-head "$frozen_next" \
            --harness-worktree "$harness_tree" --expected-harness-head "$frozen_harness" \
            --wrapper "$wrapper" --coordinator "$coordinator" --adapter "$adapter" -- \
            "$fake_maven" --wait-for-parent-mutation > "$output" 2>&1 &
        launcher_pid=$!
        set -e
        for _ in {1..400}; do [[ -s "$ready" ]] && break; sleep 0.05; done
        [[ -s "$ready" ]] || fail "recovery $mismatch mismatch did not reach child barrier"
        manifest=$(sed -n 's/^manifest=//p' "$ready")
        diagnostics="$(dirname -- "$manifest")/diagnostics"
        marker="$diagnostics/frozen-next-session-recovery.env"
        parent_identity="$diagnostics/frozen-next-parent-process-identity.env"
        [[ -f "$marker" && -f "$parent_identity" ]] || fail "recovery $mismatch mismatch omitted identity evidence"
        temporary="$diagnostics/.fixture-marker-mismatch.tmp"
        sed "s/^$mismatch=.*/$mismatch=1/" "$marker" > "$temporary"
        mv -- "$temporary" "$marker"
        : > "$go"
        set +e
        wait "$launcher_pid"
        status=$?
        set -e
        (( status == 74 )) || fail "recovery $mismatch mismatch returned $status instead of 74"
        launch_output=$(<"$output")
        [[ "$launch_output" == *'authenticated target cleanup failed: status=74'* \
            && "$launch_output" == *'authenticated=true admissible=false'* ]] \
            || fail "recovery $mismatch mismatch lacked authenticated launcher rejection"
        assert_report_restored
        [[ ! -e "$next_tree/target" && ! -L "$next_tree/target" ]] \
            || fail "recovery $mismatch mismatch left target after adapter cleanup"
    done
    printf 'PASS: recovery marker PID/start/namespace/mount mismatches reject launcher admission\n'
}

test_terminal_line_authentication() {
    local definitions run_id=fixture-run manifest="$test_root/terminal-manifest.json" status=0
    definitions=$(sed -n '/^authenticate_terminal_line()/,/^}/p' "$launcher")
    [[ "$definitions" == *'authenticate_terminal_line()'* ]] \
        || fail "launcher lacks exact terminal-line authentication"
    eval "$definitions"
    printf '{"run_id": "%s", "state": "PASSED", "valid": true}\n' "$run_id" > "$manifest"
    terminal_state= terminal_valid= terminal_run_id= terminal_manifest=
    authenticate_terminal_line \
        $'noise valid=false\n'"OPENGGF_TEST_RUN_END run_id=$run_id state=PASSED valid=true manifest=$manifest" \
        "$run_id" "$manifest" || fail "single exact terminal line was rejected"
    [[ "$terminal_state" == PASSED && "$terminal_valid" == true ]] \
        || fail "terminal line did not publish authenticated state/valid"
    for collision in \
        "prefix-OPENGGF_TEST_RUN_END run_id=$run_id state=PASSED valid=true manifest=$manifest" \
        "OPENGGF_TEST_RUN_END run_id=wrong-run state=PASSED valid=true manifest=$manifest" \
        $'OPENGGF_TEST_RUN_END run_id=fixture-run state=PASSED valid=true manifest='"$manifest"$'\nOPENGGF_TEST_RUN_END run_id=fixture-run state=PASSED valid=true manifest='"$manifest"; do
        status=0
        authenticate_terminal_line "$collision" "$run_id" "$manifest" || status=$?
        (( status != 0 )) || fail "terminal collision/duplicate/wrong-run output was authenticated"
    done
    printf 'PASS: launcher authenticates one exact matching coordinator terminal line\n'
}
test_identity_lifecycle_semantics() {
    local definitions pid start survivor_target recycled_target status=0
    definitions=$(sed -n \
        -e '/^process_start()/p' \
        -e '/^wait_process_pair_gone()/,/^}/p' "$adapter")
    [[ "$definitions" == *'wait_process_pair_gone()'* ]] \
        || fail "could not load exact production identity lifecycle helper"
    eval "$definitions"
    pid=$BASHPID
    start=$(process_start "$pid")
    [[ "$start" =~ ^[0-9]+$ ]] || fail "could not record fixture process start"
    survivor_target="$test_root/identity-survivor-target"
    mkdir -- "$survivor_target"
    wait_process_pair_gone "$pid" "$start" "$pid" "$start" || status=$?
    (( status == 76 )) || fail "same PID and start did not classify as survivor"
    [[ -d "$survivor_target" ]] || fail "surviving identity did not preserve target"
    rmdir -- "$survivor_target"

    recycled_target="$test_root/identity-recycled-target"
    mkdir -- "$recycled_target"
    wait_process_pair_gone "$pid" "$((start + 1))" "$pid" "$((start + 1))" \
        || fail "same PID with different start did not classify recorded identity as gone"
    rmdir -- "$recycled_target"
    [[ ! -e "$recycled_target" ]] || fail "recycled identity did not permit exact rmdir"
    printf 'PASS: exact PID/start survivor preserves target; recycled identity permits rmdir\n'
}

test_functional_identity_cleanup() {
    local path normal_status output ready go launcher_pid manifest diagnostics marker parent_identity
    local sleeper_pid actual_start recorded_start target_device target_inode adapter_pid supervisor_pid
    for path in normal forced; do
        for lifecycle in survivor recycled; do
            sleep 300 &
            sleeper_pid=$!
            fixture_pids+=("$sleeper_pid")
            actual_start=$(sed 's/^[^)]*) //' "/proc/$sleeper_pid/stat" | awk '{print $20}')
            [[ "$actual_start" =~ ^[0-9]+$ ]] || fail "$path $lifecycle could not authenticate fixture identity"
            recorded_start=$actual_start
            [[ "$lifecycle" == recycled ]] && recorded_start=$((actual_start + 1))
            if [[ "$path" == normal ]]; then
                if OPENGGF_FROZEN_NEXT_SELF_TEST_MODE=1 OPENGGF_ADAPTER_TEST_SHIM=1 \
                    OPENGGF_TEST_CLEANUP_IDENTITY_PID="$sleeper_pid" \
                    OPENGGF_TEST_CLEANUP_IDENTITY_ACTUAL_START="$actual_start" \
                    OPENGGF_TEST_CLEANUP_IDENTITY_RECORDED_START="$recorded_start" \
                    OPENGGF_FAKE_MAVEN=1 run_launcher "$fake_maven"; then
                    normal_status=0
                else
                    normal_status=$launch_status
                fi
                if [[ "$lifecycle" == survivor ]]; then
                    (( normal_status == 76 )) || fail "normal live identity returned $normal_status instead of 76"
                    [[ "$launch_output" == *'cleanup_status=76'* \
                        && "$launch_output" == *'authenticated=true admissible=false'* ]] \
                        || fail "normal live identity lacked authenticated cleanup-76 rejection"
                    manifest=$(manifest_from_output "$launch_output")
                    marker="$(dirname -- "$manifest")/diagnostics/frozen-next-session-recovery.env"
                    target_device=$(sed -n 's/^target_device=//p' "$marker")
                    target_inode=$(sed -n 's/^target_inode=//p' "$marker")
                    [[ -d "$next_tree/target" && ! -L "$next_tree/target" \
                        && "$(stat -c %d -- "$next_tree/target")" == "$target_device" \
                        && "$(stat -c %i -- "$next_tree/target")" == "$target_inode" ]] \
                        || fail "normal live identity did not preserve the exact authenticated target"
                    rmdir -- "$next_tree/target"
                else
                    (( normal_status == 0 )) || fail "normal recycled identity returned $normal_status"
                    [[ "$launch_output" == *'authenticated=true admissible=false'* ]] \
                        || fail "normal recycled test-seam identity was admitted"
                    [[ ! -e "$next_tree/target" && ! -L "$next_tree/target" ]] \
                        || fail "normal recycled identity did not permit exact rmdir"
                fi
                manifest=$(manifest_from_output "$launch_output")
                diagnostics="$(dirname -- "$manifest")/diagnostics"
                seam_marker="$diagnostics/frozen-next-test-seam.env"
                [[ -f "$seam_marker" && ! -L "$seam_marker" \
                    && "$(sed -n 's/^run_id=//p' "$seam_marker")" == "$(sed -n 's/.*\"run_id\": \"\([^\"]*\)\".*/\1/p' "$manifest" | head -1)" ]] \
                    || fail "normal $lifecycle lacked a run-bound test-seam marker"
            else
                ready="$test_root/forced-$lifecycle-ready.env"
                go="$test_root/forced-$lifecycle-go"
                output="$test_root/forced-$lifecycle.out"
                set +e
                OPENGGF_FROZEN_NEXT_SELF_TEST_MODE=1 OPENGGF_ADAPTER_TEST_SHIM=1 \
                    OPENGGF_TEST_CLEANUP_IDENTITY_PID="$sleeper_pid" \
                    OPENGGF_TEST_CLEANUP_IDENTITY_ACTUAL_START="$actual_start" \
                    OPENGGF_TEST_CLEANUP_IDENTITY_RECORDED_START="$recorded_start" \
                    OPENGGF_FAKE_MAVEN=1 OPENGGF_TEST_PARENT_MUTATION_READY="$ready" \
                    OPENGGF_TEST_PARENT_MUTATION_GO="$go" \
                    "$launcher" --worktree "$next_tree" --expected-head "$frozen_next" \
                    --harness-worktree "$harness_tree" --expected-harness-head "$frozen_harness" \
                    --wrapper "$wrapper" --coordinator "$coordinator" --adapter "$adapter" -- \
                    "$fake_maven" --emit-report --rewrite-report --wait-for-parent-mutation > "$output" 2>&1 &
                launcher_pid=$!
                set -e
                for _ in {1..400}; do [[ -s "$ready" ]] && break; sleep 0.05; done
                [[ -s "$ready" ]] || fail "forced $lifecycle did not reach authenticated barrier"
                manifest=$(sed -n 's/^manifest=//p' "$ready")
                adapter_pid=$(sed -n 's/^adapter_pid=//p' "$ready")
                diagnostics="$(dirname -- "$manifest")/diagnostics"
                marker="$diagnostics/frozen-next-session-recovery.env"
                parent_identity="$diagnostics/frozen-next-parent-process-identity.env"
                supervisor_pid=$(sed -n 's/^supervisor_host_pid=//p' "$diagnostics/frozen-next-namespace-leader.env")
                [[ "$adapter_pid" =~ ^[0-9]+$ && "$supervisor_pid" =~ ^[0-9]+$ \
                    && "$(sed -n 's/^supervisor_pid=//p' "$marker")" == "$sleeper_pid" \
                    && "$(sed -n 's/^supervisor_pid=//p' "$parent_identity")" == "$sleeper_pid" ]] \
                    || fail "forced $lifecycle did not publish controlled recovery identity"
                target_device=$(sed -n 's/^target_device=//p' "$marker")
                target_inode=$(sed -n 's/^target_inode=//p' "$marker")
                kill -KILL "$adapter_pid" 2>/dev/null || true
                kill -KILL "$supervisor_pid" 2>/dev/null || true
                : > "$go"
                set +e
                wait "$launcher_pid"
                forced_status=$?
                set -e
                (( forced_status != 0 )) || fail "forced $lifecycle termination was accepted"
                launch_output=$(<"$output")
                if [[ "$lifecycle" == survivor ]]; then
                    [[ "$launch_output" == *'cleanup_status=76'* ]] \
                        || fail "forced live identity lacked cleanup status 76"
                    [[ -d "$next_tree/target" && ! -L "$next_tree/target" \
                        && "$(stat -c %d -- "$next_tree/target")" == "$target_device" \
                        && "$(stat -c %i -- "$next_tree/target")" == "$target_inode" ]] \
                        || fail "forced live identity did not preserve the exact authenticated target"
                    rmdir -- "$next_tree/target"
                else
                    [[ "$launch_output" == *'cleanup_status=0'* ]] \
                        || fail "forced recycled identity did not complete authenticated cleanup"
                    [[ ! -e "$next_tree/target" && ! -L "$next_tree/target" ]] \
                        || fail "forced recycled identity did not permit exact rmdir"
                fi
                seam_marker="$diagnostics/frozen-next-test-seam.env"
                [[ -f "$seam_marker" && ! -L "$seam_marker" \
                    && "$(sed -n 's/^run_id=//p' "$seam_marker")" == "$(sed -n 's/.*\"run_id\": \"\([^\"]*\)\".*/\1/p' "$manifest" | head -1)" ]] \
                    || fail "forced $lifecycle lacked a run-bound test-seam marker"
                assert_report_restored
            fi
            kill -KILL "$sleeper_pid" 2>/dev/null || true
            wait "$sleeper_pid" 2>/dev/null || true
        done
    done
    printf 'PASS: normal and forced cleanup preserve live identities and remove recycled identities\n'
}

test_test_seam_admission() {
    local manifest diagnostics seam_marker seam_names unmodeled
    for unmodeled in adapter-shim cleanup-identity; do
        case "$unmodeled" in
            adapter-shim)
                if OPENGGF_ADAPTER_TEST_SHIM=1 OPENGGF_FAKE_MAVEN=1 run_launcher "$fake_maven"; then
                    fail "adapter test shim without exact self-test mode was accepted"
                fi
                ;;
            cleanup-identity)
                if OPENGGF_TEST_CLEANUP_IDENTITY_PID=1 OPENGGF_FAKE_MAVEN=1 \
                    run_launcher "$fake_maven"; then
                    fail "cleanup identity seam without exact self-test mode was accepted"
                fi
                ;;
        esac
        [[ "$launch_output" == *'authenticated=true admissible=false'* ]] \
            || fail "unmodeled $unmodeled lacked explicit launcher rejection"
        manifest=$(manifest_from_output "$launch_output")
        diagnostics="$(dirname -- "$manifest")/diagnostics"
        rg -F 'adapter test seam requires exact self-test mode' "$(dirname -- "$manifest")/maven.log" >/dev/null \
            || fail "unmodeled $unmodeled lacked fail-closed diagnostic"
    done

    OPENGGF_FROZEN_NEXT_SELF_TEST_MODE=1 OPENGGF_ADAPTER_TEST_SHIM=1 \
        OPENGGF_FAKE_MAVEN=1 run_launcher "$fake_maven" \
        || fail "authenticated self-test seam run failed"
    [[ "$launch_output" == *'authenticated=true admissible=false'* ]] \
        || fail "successful adapter test seam was admitted"
    manifest=$(manifest_from_output "$launch_output")
    diagnostics="$(dirname -- "$manifest")/diagnostics"
    seam_marker="$diagnostics/frozen-next-test-seam.env"
    [[ -f "$seam_marker" && ! -L "$seam_marker" \
        && "$(sed -n 's/^run_id=//p' "$seam_marker")" == "$(sed -n 's/.*\"run_id\": \"\([^\"]*\)\".*/\1/p' "$manifest" | head -1)" \
        && "$(sed -n 's/^mode=//p' "$seam_marker")" == exact-self-test-v1 ]] \
        || fail "successful adapter test seam marker was not authenticated"
    seam_names=$(sed -n 's/^variables=//p' "$seam_marker")
    [[ ",$seam_names," == *,OPENGGF_ADAPTER_TEST_SHIM,* ]] \
        || fail "adapter test seam marker omitted the activating variable"
    [[ ! -e "$next_tree/target" && ! -L "$next_tree/target" ]] \
        || fail "successful adapter test seam left target"

    OPENGGF_FROZEN_NEXT_SELF_TEST_MODE=1 OPENGGF_TEST_CORRUPT_GENERATED_ARCHIVE= \
        OPENGGF_FAKE_MAVEN=1 run_launcher "$fake_maven" \
        || fail "empty-valued archive-corruption seam run failed"
    manifest=$(manifest_from_output "$launch_output")
    seam_marker="$(dirname -- "$manifest")/diagnostics/frozen-next-test-seam.env"
    seam_names=$(sed -n 's/^variables=//p' "$seam_marker")
    [[ ",$seam_names," == *,OPENGGF_TEST_CORRUPT_GENERATED_ARCHIVE,* \
        && "$launch_output" == *'authenticated=true admissible=false'* ]] \
        || fail "empty-valued archive-corruption seam was not authenticated and rejected"
    printf 'PASS: lifecycle/test shims are explicit and never admissible\n'
}

test_test_seam_inventory_audit() {
    local adapter_inventory launcher_inventory referenced variable
    local non_seam=',OPENGGF_TEST_WORKTREE,OPENGGF_TEST_TMP_ROOT,OPENGGF_TEST_DIAGNOSTICS,'
    non_seam+='OPENGGF_TEST_RUN_ID,OPENGGF_TEST_MANIFEST,OPENGGF_TEST_CONTAINED_READY,'
    non_seam+='OPENGGF_TEST_INTERRUPT_READY,OPENGGF_TEST_MUTATION_INPUT,'
    non_seam+='OPENGGF_TEST_PARENT_MUTATION_READY,OPENGGF_TEST_PARENT_MUTATION_GO,'
    non_seam+='OPENGGF_TEST_RESOLVED_USER_HOME,OPENGGF_TEST_RUN_END,'
    adapter_inventory=$(sed -n '/^test_seam_variables=$/,/^done$/p' "$adapter" \
        | rg -o 'OPENGGF_(FROZEN_NEXT_SELF_TEST_MODE|ADAPTER_TEST_SHIM|TEST_[A-Z0-9_]+)' | sort -u)
    launcher_inventory=$(sed -n '/^launcher_test_seam_variables=$/,/^done$/p' "$launcher" \
        | rg -o 'OPENGGF_(FROZEN_NEXT_SELF_TEST_MODE|ADAPTER_TEST_SHIM|TEST_[A-Z0-9_]+)' | sort -u)
    [[ "$adapter_inventory" == "$launcher_inventory" ]] \
        || fail "adapter and launcher authenticated test-seam inventories differ"
    [[ "$adapter_inventory" == *OPENGGF_TEST_CORRUPT_GENERATED_ARCHIVE* ]] \
        || fail "archive-corruption control is absent from authenticated test-seam inventory"
    referenced=$(rg --no-filename -o 'OPENGGF_TEST_[A-Z0-9_]+' \
        "$adapter" "$launcher" "$fake_maven" | sort -u)
    while IFS= read -r variable; do
        [[ -n "$variable" ]] || continue
        if rg -Fxq -- "$variable" <<< "$adapter_inventory" \
            || [[ "$non_seam" == *",$variable,"* ]]; then
            continue
        fi
        fail "unclassified OPENGGF_TEST control escaped seam audit: $variable"
    done <<< "$referenced"
    printf 'PASS: adapter/launcher seam inventories mechanically cover test controls\n'
}

test_test_seam_marker_mutation() {
    local mutation ready go output launcher_pid status manifest diagnostics marker temporary
    for mutation in mode variables; do
        ready="$test_root/seam-$mutation-ready.env"
        go="$test_root/seam-$mutation-go"
        output="$test_root/seam-$mutation.out"
        set +e
        OPENGGF_FROZEN_NEXT_SELF_TEST_MODE=1 OPENGGF_ADAPTER_TEST_SHIM=1 \
            OPENGGF_FAKE_MAVEN=1 OPENGGF_TEST_PARENT_MUTATION_READY="$ready" \
            OPENGGF_TEST_PARENT_MUTATION_GO="$go" \
            "$launcher" --worktree "$next_tree" --expected-head "$frozen_next" \
            --harness-worktree "$harness_tree" --expected-harness-head "$frozen_harness" \
            --wrapper "$wrapper" --coordinator "$coordinator" --adapter "$adapter" -- \
            "$fake_maven" --wait-for-parent-mutation > "$output" 2>&1 &
        launcher_pid=$!
        set -e
        for _ in {1..400}; do [[ -s "$ready" ]] && break; sleep 0.05; done
        [[ -s "$ready" ]] || fail "seam $mutation mutation did not reach barrier"
        manifest=$(sed -n 's/^manifest=//p' "$ready")
        diagnostics="$(dirname -- "$manifest")/diagnostics"
        marker="$diagnostics/frozen-next-test-seam.env"
        [[ -f "$marker" && ! -L "$marker" ]] || fail "seam $mutation mutation omitted marker"
        temporary="$diagnostics/.fixture-seam-mutation.tmp"
        case "$mutation" in
            mode) sed 's/^mode=.*/mode=attacker/' "$marker" > "$temporary" ;;
            variables) sed 's/^variables=.*/variables=OPENGGF_ADAPTER_TEST_SHIM/' "$marker" > "$temporary" ;;
        esac
        mv -- "$temporary" "$marker"
        : > "$go"
        set +e
        wait "$launcher_pid"
        status=$?
        set -e
        (( status == 77 )) || fail "seam $mutation mutation returned $status instead of 77"
        launch_output=$(<"$output")
        [[ "$launch_output" == *'authenticated target cleanup failed: status=77'* \
            && "$launch_output" == *'authenticated=true admissible=false'* ]] \
            || fail "seam $mutation mutation lacked authenticated launcher rejection"
        assert_report_restored
        [[ ! -e "$next_tree/target" && ! -L "$next_tree/target" ]] \
            || fail "seam $mutation mutation left target"
    done
    printf 'PASS: test-seam marker mode/list mutation rejects launcher admission\n'
}
test_launcher_signal_recovery() {
    local signal_case expected_signal_status interrupt_ready interrupt_output interrupt_tmp
    local launcher_pid interrupt_manifest interrupt_status
    local -a interrupt_captures
    for signal_case in INT TERM; do
        case "$signal_case" in INT) expected_signal_status=130 ;; TERM) expected_signal_status=143 ;; esac
        interrupt_ready="$test_root/launcher-$signal_case-ready.env"
        interrupt_output="$test_root/launcher-$signal_case.out"
        interrupt_tmp="$test_root/launcher-$signal_case-tmp"
        mkdir "$interrupt_tmp"
        set +e
        TMPDIR="$interrupt_tmp" OPENGGF_FAKE_MAVEN=1 OPENGGF_TEST_INTERRUPT_READY="$interrupt_ready" \
            perl -e '
                my ($signal, $ready, @command) = @ARGV;
                my $pid = fork();
                die "fork failed: $!" unless defined $pid;
                if ($pid == 0) { exec {$command[0]} @command; die "exec failed: $!"; }
                my $observed = 0;
                for (1 .. 200) {
                    if (-f $ready) { $observed = 1; last; }
                    select undef, undef, undef, 0.05;
                }
                unless ($observed) { kill "TERM", $pid; waitpid $pid, 0; exit 124; }
                kill $signal, $pid or die "signal failed: $!";
                waitpid $pid, 0;
                exit(($? & 127) ? 128 + ($? & 127) : $? >> 8);
            ' "$signal_case" "$interrupt_ready" \
            "$launcher" --worktree "$next_tree" --expected-head "$frozen_next" \
            --harness-worktree "$harness_tree" --expected-harness-head "$frozen_harness" \
            --wrapper "$wrapper" --coordinator "$coordinator" --adapter "$adapter" \
            -- "$fake_maven" --emit-report --rewrite-report --wait-for-launcher-interrupt \
            > "$interrupt_output" 2>&1
        interrupt_status=$?
        set -e
        [[ -f "$interrupt_ready" ]] || fail "$signal_case launcher child did not become ready"
        interrupt_manifest=$(sed -n 's/^manifest=//p' "$interrupt_ready")
        (( interrupt_status == expected_signal_status )) \
            || fail "$signal_case launcher did not preserve status $expected_signal_status: $interrupt_status"
        [[ -f "$interrupt_manifest" ]] && rg -q '"state": "INVALID_IDENTITY_CHANGED"' "$interrupt_manifest" \
            || fail "$signal_case launcher did not digest the generated report mutation"
        if [[ "$(<"$interrupt_output")" == *'OPENGGF_TEST_RUN_END'* ]]; then
            [[ "$(<"$interrupt_output")" == *'valid=false'* ]] \
                || fail "$signal_case outer recovery was marked valid"
        else
            [[ "$(<"$interrupt_output")" == *'NoSuchFileException:'*'manifest.json.tmp'* ]] \
                || fail "$signal_case missing END marker lacked the exact frozen-coordinator race diagnostic"
            printf 'NON-CERTIFYING: %s omitted OPENGGF_TEST_RUN_END after diagnosed manifest tmp race\n' \
                "$signal_case"
        fi
        assert_report_restored
        [[ ! -e "$next_tree/target" && ! -L "$next_tree/target" ]] \
            || fail "$signal_case outer recovery left target"
        shopt -s nullglob
        interrupt_captures=("$interrupt_tmp"/*)
        shopt -u nullglob
        (( ${#interrupt_captures[@]} == 0 )) || fail "$signal_case retained its private capture"
    done
    printf 'PASS: launcher INT/TERM outer report recovery remains identity-invalid\n'
}
test_authenticated_rmdir_failure() {
    local failures_before=$review_failures
    local shim_dir="$test_root/rmdir-failure-bin"
    local rmdir_shim="$shim_dir/rmdir"
    local real_rmdir
    real_rmdir=$(command -v rmdir)
    mkdir -p "$shim_dir"
    printf '%s\n' \
        '#!/usr/bin/env bash' \
        'set -euo pipefail' \
        'target=${!#}' \
        'if [[ "$target" == "${OPENGGF_TEST_RMDIR_FAIL_TARGET:?}" ]]; then' \
        '    printf "controlled rmdir failure: %s\\n" "$target" >&2' \
        '    exit 73' \
        'fi' \
        'exec "${OPENGGF_TEST_REAL_RMDIR:?}" "$@"' > "$rmdir_shim"
    chmod +x "$rmdir_shim"

    if PATH="$shim_dir:$PATH" OPENGGF_FROZEN_NEXT_SELF_TEST_MODE=1 \
        OPENGGF_TEST_RMDIR_FAIL_TARGET="$next_tree/target" \
        OPENGGF_TEST_REAL_RMDIR="$real_rmdir" OPENGGF_FAKE_MAVEN=1 run_launcher "$fake_maven"; then
        review_fail "launcher returned success after authenticated target rmdir failure"
    elif (( launch_status != 73 )); then
        fail "authenticated rmdir failure returned unexpected status: $launch_status"
    fi
    [[ -d "$next_tree/target" && ! -L "$next_tree/target" ]] \
        || fail "controlled rmdir failure did not retain the ordinary target directory"
    [[ -z "$(find "$next_tree/target" -mindepth 1 -maxdepth 1 -print -quit)" ]] \
        || fail "controlled rmdir failure left a non-empty target"
    [[ "$launch_output" == *'authenticated target cleanup failed'* ]] \
        || review_fail "authenticated rmdir failure was not reported"
    "$real_rmdir" -- "$next_tree/target"

    if PATH="$shim_dir:$PATH" OPENGGF_FROZEN_NEXT_SELF_TEST_MODE=1 \
        OPENGGF_TEST_RMDIR_FAIL_TARGET="$next_tree/target" \
        OPENGGF_TEST_REAL_RMDIR="$real_rmdir" OPENGGF_FAKE_MAVEN=1 \
        run_launcher "$fake_maven" --fail; then
        fail "failing Maven child became successful during authenticated rmdir failure"
    elif (( launch_status != 23 )); then
        fail "authenticated rmdir failure replaced Maven status 23: $launch_status"
    fi
    [[ -d "$next_tree/target" && ! -L "$next_tree/target" ]] \
        || fail "child failure did not retain the exact ordinary target after rmdir failure"
    [[ "$launch_output" == *'authenticated target cleanup failed'* ]] \
        || review_fail "child failure did not report authenticated rmdir failure"
    "$real_rmdir" -- "$next_tree/target"

    if (( review_failures == failures_before )); then
        printf 'PASS: authenticated target rmdir failure propagation\n'
    fi
}

test_preflight_safety_tripwire() {
    local shim_dir="$test_root/preflight-failure-bin"
    local real_unshare preflight_manifest preflight_diagnostics
    mkdir -- "$shim_dir"
    real_unshare=$(command -v unshare)
    ln -s -- "$fake_maven" "$shim_dir/unshare"
    if PATH="$shim_dir:$PATH" OPENGGF_FROZEN_NEXT_SELF_TEST_MODE=1 \
        OPENGGF_ADAPTER_TEST_SHIM=1 OPENGGF_TEST_FAIL_PREFLIGHT=1 \
        OPENGGF_TEST_REAL_UNSHARE="$real_unshare" OPENGGF_FAKE_MAVEN=1 \
        run_launcher "$fake_maven"; then
        fail "unavailable namespace preflight was accepted"
    fi
    preflight_manifest=$(manifest_from_output "$launch_output")
    [[ -f "$preflight_manifest" ]] || fail "preflight safety failure omitted its manifest"
    rg -F '"state": "INVALID_IDENTITY_CHANGED"' "$preflight_manifest" >/dev/null \
        || fail "preflight safety failure remained superficially valid"
    [[ "$launch_output" == *'valid=false'* ]] \
        || fail "preflight safety failure lacked invalid terminal evidence"
    preflight_diagnostics="$(dirname -- "$preflight_manifest")/diagnostics"
    rg -Fx 'reason=adapter-safety-preflight' "$preflight_diagnostics/frozen-next-safety-failure.env" >/dev/null \
        || fail "preflight safety failure did not arm the phase tripwire"
    [[ -f "$preflight_diagnostics/frozen-next-identity-tripwire.env" ]] \
        || fail "preflight safety failure omitted tripwire evidence"
    assert_report_restored
    [[ ! -e "$next_tree/target" && ! -L "$next_tree/target" ]] \
        || fail "preflight safety failure created a target"
    [[ "$launch_output" == *'OPENGGF_FROZEN_NEXT_LAUNCH_END '* \
        && "$launch_output" == *'admissible=false'* ]] \
        || fail "preflight safety failure lacked authenticated launcher rejection"
    printf 'PASS: unavailable namespace preflight is identity-invalid before setup\n'
}

test_resolved_base_user_home_rejection() {
    local resolved_manifest resolved_log
    if OPENGGF_FAKE_MAVEN=1 OPENGGF_TEST_RESOLVED_USER_HOME=1 run_launcher "$fake_maven"; then
        fail "resolved Surefire base user.home override was accepted"
    fi
    resolved_manifest=$(manifest_from_output "$launch_output")
    [[ -f "$resolved_manifest" ]] || fail "resolved user.home rejection omitted its manifest"
    rg -F '"state": "INVALID_IDENTITY_CHANGED"' "$resolved_manifest" >/dev/null \
        || fail "resolved user.home rejection remained superficially valid"
    resolved_log="$(dirname -- "$resolved_manifest")/maven.log"
    rg -F 'resolved surefire.argLine user.home override is forbidden' "$resolved_log" >/dev/null \
        || fail "resolved user.home rejection lacked the exact diagnostic"
    assert_report_restored
    [[ ! -e "$next_tree/target" && ! -L "$next_tree/target" ]] \
        || fail "resolved user.home rejection left target"
    printf 'PASS: resolved Surefire base user.home override rejected\n'
}

if [[ "${OPENGGF_FROZEN_NEXT_ADAPTER_FOCUS:-}" == review-safety ]]; then
    test_preflight_safety_tripwire
    exit 0
fi

if [[ "${OPENGGF_FROZEN_NEXT_ADAPTER_FOCUS:-}" == resolved-user-home ]]; then
    test_resolved_base_user_home_rejection
    exit 0
fi

if [[ "${OPENGGF_FROZEN_NEXT_ADAPTER_FOCUS:-}" == pid-containment ]]; then
    test_pid_containment
    exit 0
fi

if [[ "${OPENGGF_FROZEN_NEXT_ADAPTER_FOCUS:-}" == namespace-negatives ]]; then
    test_namespace_safety_negatives
    exit 0
fi

if [[ "${OPENGGF_FROZEN_NEXT_ADAPTER_FOCUS:-}" == marker-mismatch ]]; then
    test_recovery_marker_identity_mismatch
    exit 0
fi

if [[ "${OPENGGF_FROZEN_NEXT_ADAPTER_FOCUS:-}" == identity-lifecycle ]]; then
    test_identity_lifecycle_semantics
    exit 0
fi

if [[ "${OPENGGF_FROZEN_NEXT_ADAPTER_FOCUS:-}" == functional-lifecycle ]]; then
    test_functional_identity_cleanup
    exit 0
fi

if [[ "${OPENGGF_FROZEN_NEXT_ADAPTER_FOCUS:-}" == seam-admission ]]; then
    test_test_seam_admission
    exit 0
fi

if [[ "${OPENGGF_FROZEN_NEXT_ADAPTER_FOCUS:-}" == seam-audit ]]; then
    test_test_seam_inventory_audit
    exit 0
fi

if [[ "${OPENGGF_FROZEN_NEXT_ADAPTER_FOCUS:-}" == seam-mutation ]]; then
    test_test_seam_marker_mutation
    exit 0
fi

if [[ "${OPENGGF_FROZEN_NEXT_ADAPTER_FOCUS:-}" == terminal-auth ]]; then
    test_terminal_line_authentication
    exit 0
fi

if [[ "${OPENGGF_FROZEN_NEXT_ADAPTER_FOCUS:-}" == cleanup-failure ]]; then
    test_authenticated_rmdir_failure
    (( review_failures == 0 )) || fail "$review_failures focused cleanup regression case(s) remain"
    exit 0
fi

if [[ "${OPENGGF_FROZEN_NEXT_ADAPTER_FOCUS:-}" == signals ]]; then
    test_launcher_signal_recovery
    exit 0
fi

if [[ "${OPENGGF_FROZEN_NEXT_ADAPTER_FOCUS:-}" == task1a-red ]]; then
    red_failures=0
    if OPENGGF_FAKE_MAVEN=1 run_launcher "$fake_maven" --emit-report --rewrite-report; then
        red_manifest=$(manifest_from_output "$launch_output")
        if ! rg -q '"state": "PASSED"' "$red_manifest" || [[ "$launch_output" != *'valid=true'* ]]; then
            printf 'EXPECTED RED: authorized generated report did not produce PASSED/valid=true\n' >&2
            red_failures=$((red_failures + 1))
        fi
    else
        printf 'EXPECTED RED: authorized generated report returned status %s\n' "$launch_status" >&2
        red_failures=$((red_failures + 1))
    fi
    git -C "$next_tree" show "$frozen_next:docs/status/rewind-round-trip-gaps.md" \
        > "$next_tree/docs/status/rewind-round-trip-gaps.md"

    if OPENGGF_FAKE_MAVEN=1 run_launcher "$fake_maven" --emit-report --lexical-target-temp; then
        red_manifest=$(manifest_from_output "$launch_output")
        red_evidence="$(dirname -- "$red_manifest")/diagnostics/frozen-next-session-evidence.txt"
        if ! rg -Fx "java_io_tmpdir_lexical=$next_tree/target/test-tmp" "$red_evidence" >/dev/null \
            || ! rg -Fx "java_io_tmpdir_canonical=$next_tree/target/test-tmp" "$red_evidence" >/dev/null; then
            printf 'EXPECTED RED: report JVM did not retain worktree-local target/test-tmp semantics\n' >&2
            red_failures=$((red_failures + 1))
        fi
    else
        printf 'EXPECTED RED: lexical temp reproduction unexpectedly failed to launch\n' >&2
        red_failures=$((red_failures + 1))
    fi
    (( red_failures == 0 )) || fail "$red_failures Task 1A RED reproduction(s) remain"
    exit 0
fi

if [[ "${OPENGGF_FROZEN_NEXT_ADAPTER_FOCUS:-}" == mount-red ]]; then
    OPENGGF_FAKE_MAVEN=1 run_launcher "$fake_maven" --emit-report --assert-mount-topology
    assert_session_outcome PASSED true
    [[ ! -e "$next_tree/target" && ! -L "$next_tree/target" ]] || fail "focused mount run left target"
    printf 'PASS: private authenticated bind-mount topology\n'
    exit 0
fi

test_pid_containment
test_namespace_safety_negatives
test_recovery_marker_identity_mismatch
test_identity_lifecycle_semantics
test_functional_identity_cleanup
test_test_seam_admission
test_test_seam_inventory_audit
test_test_seam_marker_mutation
test_terminal_line_authentication

# The selected guards are independent and force two Surefire processes when forkCount=2.
run_launcher mvn -DforkCount=2 -Dtest=TestAudioBackendBypassGuard,TestProductionAwtBlacklistGuard test
success_manifest=$(manifest_from_output "$launch_output")
[[ -f "$success_manifest" ]] || fail "successful session did not publish a manifest"
evidence="$(dirname -- "$success_manifest")/diagnostics/frozen-next-session-evidence.txt"
mount_evidence="$(dirname -- "$success_manifest")/diagnostics/frozen-next-mount-evidence.txt"
[[ ! -e "$(dirname -- "$success_manifest")/diagnostics/frozen-next-test-seam.env" ]] \
    || fail "production two-fork run unexpectedly published a test-seam marker"
[[ "$launch_output" == *'authenticated=true admissible=true'* ]] \
    || fail "production two-fork run lost authenticated admission"
success_tmp="$(dirname -- "$success_manifest")/tmp"
success_target="$next_tree/target/test-tmp"
rg -F 'TEST-com.openggf.audio.TestAudioBackendBypassGuard.xml' "$success_manifest" >/dev/null \
    || fail "successful manifest omitted the audio guard report"
rg -F 'TEST-com.openggf.game.TestProductionAwtBlacklistGuard.xml' "$success_manifest" >/dev/null \
    || fail "successful manifest omitted the AWT guard report"
[[ "$(rg -Fxc "java_io_tmpdir_lexical=$success_target" "$evidence")" == 2 ]] \
    || fail "successful session did not use worktree-local lexical temp paths"
[[ "$(rg -Fxc "java_io_tmpdir_canonical=$success_target" "$evidence")" == 2 ]] \
    || fail "successful session did not retain worktree-local canonical fork temp paths"
for fork in 1 2; do
    rg -Fx "lwjgl_extract_lexical=$success_tmp/lwjgl-$fork" "$evidence" >/dev/null \
        || fail "successful session omitted lexical fork $fork LWJGL path"
    rg -Fx "lwjgl_extract_canonical=$success_tmp/lwjgl-$fork" "$evidence" >/dev/null \
        || fail "successful session omitted canonical fork $fork LWJGL path"
done
rg -F "target_ignore_attribution=$exclude" "$evidence" >/dev/null \
    || fail "external exclude was not attributed during the run"
rg -Fx 'make_rprivate=passed' "$mount_evidence" >/dev/null \
    || fail "successful session omitted recursive-private mount evidence"
rg -Fx 'ready=verified' "$mount_evidence" >/dev/null \
    || fail "successful session omitted ready barrier proof"
rg -Fx 'parent_target_kind=directory' "$mount_evidence" >/dev/null \
    || fail "successful session omitted parent ordinary-directory proof"
rg -Fx 'parent_target_mount=false' "$mount_evidence" >/dev/null \
    || fail "successful session omitted parent non-mount proof"
rg -Fx 'go=released' "$mount_evidence" >/dev/null \
    || fail "successful session omitted go barrier proof"
printf 'PASS: successful two-fork frozen-next session\n'

[[ ! -e "$next_tree/target" && ! -L "$next_tree/target" ]] || fail "successful run left target"
[[ -z "$(git -C "$next_tree" status --porcelain --untracked-files=all)" ]] || fail "successful run dirtied frozen next"
[[ "$(git -C "$next_tree" rev-parse HEAD)" == "$frozen_next" ]] || fail "successful run changed frozen next HEAD"
git -C "$next_tree" symbolic-ref -q HEAD >/dev/null && fail "successful run attached frozen next HEAD"

if [[ "${OPENGGF_FROZEN_NEXT_ADAPTER_FOCUS:-}" == real-precedence ]]; then
    printf 'PASS: frozen-POM private bind-mount temp semantics\n'
    exit 0
fi

# Exact generated-report normalization succeeds for both a successful and a
# failing child, preserving the child's original status and manifest validity.
OPENGGF_FAKE_MAVEN=1 run_launcher "$fake_maven" --emit-report --rewrite-report
assert_session_outcome PASSED true
assert_report_restored
normalization_manifest=$(manifest_from_output "$launch_output")
normalization_root="$(dirname -- "$normalization_manifest")/diagnostics/frozen-next-generated-report"
[[ -f "$normalization_root/original.md" && -f "$normalization_root/generated.md" \
    && -f "$normalization_root/metadata.env" ]] || fail "normalization archives are incomplete"
rg -Fx 'restoration_status=restored' "$normalization_root/metadata.env" >/dev/null \
    || fail "normalization metadata omitted successful restoration"
rg -F $'probe_record=' "$normalization_root/metadata.env" >/dev/null \
    || fail "normalization metadata omitted probe outcome"
printf 'PASS: authorized generated report normalization after child success\n'

if OPENGGF_FAKE_MAVEN=1 run_launcher "$fake_maven" --emit-report --rewrite-report --fail; then
    fail "failing child became successful after authorized normalization"
fi
(( launch_status == 23 )) || fail "authorized normalization replaced child status 23: $launch_status"
assert_session_outcome FAILED true
assert_report_restored
printf 'PASS: authorized generated report normalization preserves child failure\n'

OPENGGF_FAKE_MAVEN=1 run_launcher "$fake_maven" --emit-report
assert_session_outcome PASSED true
assert_report_restored
printf 'PASS: no generated report mutation requires no normalization\n'

# A second source mutation makes the session identity-invalid. Outer recovery
# restores only the pinned report; it deliberately leaves the second mutation.
if OPENGGF_FAKE_MAVEN=1 run_launcher "$fake_maven" --emit-report --rewrite-report --second-source-mutation; then
    fail "report plus second source mutation was accepted"
fi
(( launch_status == 70 )) || fail "second mutation returned unexpected status: $launch_status"
assert_session_outcome INVALID_IDENTITY_CHANGED false
assert_report_restored
[[ -n "$(git -C "$next_tree" status --porcelain -- README.md)" ]] \
    || fail "second mutation was hidden or restored"
restore_frozen_file README.md
printf 'PASS: second source mutation remains identity-invalid\n'

# Generated archive and child-side restore failures leave the mutation through
# the coordinator digest. Authenticated outer recovery then restores hygiene.
failure_shim_dir="$test_root/normalization-failure-bin"
mkdir -p "$failure_shim_dir"
real_cp=$(command -v cp)
real_mv=$(command -v mv)
printf '%s\n' \
    '#!/usr/bin/env bash' \
    'set -euo pipefail' \
    'destination=${!#}' \
    'if [[ "${OPENGGF_TEST_FAIL_GENERATED_ARCHIVE:-}" == 1' \
    '    && "$destination" == */frozen-next-generated-report/generated.md ]]; then exit 67; fi' \
    'if [[ "${OPENGGF_TEST_CORRUPT_GENERATED_ARCHIVE:-}" == 1' \
    '    && "$destination" == */frozen-next-generated-report/generated.md ]]; then' \
    '  "${OPENGGF_TEST_REAL_CP:?}" "$@"; printf "controlled archive corruption\n" >> "$destination"; exit 0; fi' \
    'exec "${OPENGGF_TEST_REAL_CP:?}" "$@"' > "$failure_shim_dir/cp"
printf '%s\n' \
    '#!/usr/bin/env bash' \
    'set -euo pipefail' \
    'for argument in "$@"; do' \
    '  if [[ "${OPENGGF_TEST_FAIL_RESTORE:-}" == 1' \
    '      && "$argument" == */.rewind-round-trip-gaps.restore.* ]]; then exit 68; fi' \
    'done' \
    'exec "${OPENGGF_TEST_REAL_MV:?}" "$@"' > "$failure_shim_dir/mv"
chmod +x "$failure_shim_dir/cp" "$failure_shim_dir/mv"

if PATH="$failure_shim_dir:$PATH" OPENGGF_FROZEN_NEXT_SELF_TEST_MODE=1 \
    OPENGGF_TEST_REAL_CP="$real_cp" OPENGGF_TEST_REAL_MV="$real_mv" \
    OPENGGF_TEST_FAIL_GENERATED_ARCHIVE=1 OPENGGF_FAKE_MAVEN=1 \
    run_launcher "$fake_maven" --emit-report --rewrite-report; then
    fail "generated archive failure was accepted"
fi
(( launch_status == 70 )) || fail "generated archive failure returned unexpected status: $launch_status"
assert_session_outcome INVALID_IDENTITY_CHANGED false
assert_report_restored
failure_manifest=$(manifest_from_output "$launch_output")
rg -F 'normalization failed: generated-archive-failed' "$(dirname -- "$failure_manifest")/maven.log" >/dev/null \
    || fail "generated archive failure lacked an explicit diagnostic"
printf 'PASS: generated archive failure remains identity-invalid\n'

if PATH="$failure_shim_dir:$PATH" OPENGGF_FROZEN_NEXT_SELF_TEST_MODE=1 \
    OPENGGF_TEST_REAL_CP="$real_cp" OPENGGF_TEST_REAL_MV="$real_mv" \
    OPENGGF_TEST_CORRUPT_GENERATED_ARCHIVE=1 OPENGGF_FAKE_MAVEN=1 \
    run_launcher "$fake_maven" --emit-report --rewrite-report; then
    fail "generated archive byte corruption was accepted"
fi
(( launch_status == 70 )) || fail "generated archive corruption returned unexpected status: $launch_status"
assert_session_outcome INVALID_IDENTITY_CHANGED false
assert_report_restored
failure_manifest=$(manifest_from_output "$launch_output")
rg -F 'normalization failed: generated-archive-identity-failed' \
    "$(dirname -- "$failure_manifest")/maven.log" >/dev/null \
    || fail "generated archive corruption lacked an explicit diagnostic"
printf 'PASS: generated archive copy is byte-verified against authenticated measurement\n'

if PATH="$failure_shim_dir:$PATH" OPENGGF_FROZEN_NEXT_SELF_TEST_MODE=1 \
    OPENGGF_TEST_REAL_CP="$real_cp" OPENGGF_TEST_REAL_MV="$real_mv" \
    OPENGGF_TEST_FAIL_RESTORE=1 OPENGGF_FAKE_MAVEN=1 \
    run_launcher "$fake_maven" --emit-report --rewrite-report --fail; then
    fail "restore failure plus child failure was accepted"
fi
(( launch_status == 23 )) || fail "restore failure replaced child status 23: $launch_status"
assert_session_outcome INVALID_IDENTITY_CHANGED false
assert_report_restored
failure_manifest=$(manifest_from_output "$launch_output")
rg -F 'normalization failed: restore-failed' "$(dirname -- "$failure_manifest")/maven.log" >/dev/null \
    || fail "restore failure lacked an explicit diagnostic"
printf 'PASS: restore failure preserves child status and invalidates identity\n'

# Unsafe initial preimages are rejected before coordinator launch and remain
# untouched for inspection.
printf 'wrong initial bytes\n' > "$next_tree/docs/status/rewind-round-trip-gaps.md"
if OPENGGF_FAKE_MAVEN=1 run_launcher "$fake_maven"; then fail "wrong initial report blob was accepted"; fi
[[ -z "$(manifest_from_output "$launch_output")" ]] || fail "wrong initial blob started a coordinator session"
restore_frozen_file docs/status/rewind-round-trip-gaps.md

unlink -- "$next_tree/docs/status/rewind-round-trip-gaps.md"
if OPENGGF_FAKE_MAVEN=1 run_launcher "$fake_maven"; then fail "missing initial report was accepted"; fi
restore_frozen_file docs/status/rewind-round-trip-gaps.md

unlink -- "$next_tree/docs/status/rewind-round-trip-gaps.md"
ln -s -- README.md "$next_tree/docs/status/rewind-round-trip-gaps.md"
if OPENGGF_FAKE_MAVEN=1 run_launcher "$fake_maven"; then fail "symlink initial report was accepted"; fi
restore_frozen_file docs/status/rewind-round-trip-gaps.md

unlink -- "$next_tree/docs/status/rewind-round-trip-gaps.md"
mkdir -- "$next_tree/docs/status/rewind-round-trip-gaps.md"
if OPENGGF_FAKE_MAVEN=1 run_launcher "$fake_maven"; then fail "directory initial report was accepted"; fi
restore_frozen_file docs/status/rewind-round-trip-gaps.md
printf 'PASS: unsafe initial report preimages rejected\n'

# Replacing the report after authentication is never normalized. No-follow
# outer recovery refuses the unsafe type, but target cleanup still completes.
for replacement in missing-report symlink-report directory-report; do
    if OPENGGF_FAKE_MAVEN=1 run_launcher "$fake_maven" --emit-report "--$replacement"; then
        fail "in-run $replacement replacement was accepted"
    fi
    (( launch_status == 70 )) || fail "in-run $replacement returned unexpected status: $launch_status"
    assert_session_outcome INVALID_IDENTITY_CHANGED false
    [[ ! -e "$next_tree/target" && ! -L "$next_tree/target" ]] \
        || fail "in-run $replacement prevented target cleanup"
    case "$replacement" in
        missing-report) [[ ! -e "$next_tree/docs/status/rewind-round-trip-gaps.md" ]] \
            || fail "missing report was recreated by unsafe recovery" ;;
        symlink-report) [[ -L "$next_tree/docs/status/rewind-round-trip-gaps.md" ]] \
            || fail "symlink report was replaced by unsafe recovery" ;;
        directory-report) [[ -d "$next_tree/docs/status/rewind-round-trip-gaps.md" ]] \
            || fail "directory report was replaced by unsafe recovery" ;;
    esac
    restore_frozen_file docs/status/rewind-round-trip-gaps.md
done
printf 'PASS: in-run report type replacement remains identity-invalid\n'

if [[ "${OPENGGF_FROZEN_NEXT_ADAPTER_FOCUS:-}" == normalization ]]; then
    printf 'PASS: focused generated-report normalization matrix\n'
    exit 0
fi

mkdir "$next_tree/target"
if run_launcher mvn -Dtest=TestAudioBackendBypassGuard test; then
    fail "ordinary target directory was accepted"
fi
[[ -d "$next_tree/target" && ! -L "$next_tree/target" ]] || fail "ordinary target was removed"
rmdir "$next_tree/target"

ln -s / "$next_tree/target"
if run_launcher mvn -Dtest=TestAudioBackendBypassGuard test; then
    fail "mismatched target symlink was accepted"
fi
[[ -L "$next_tree/target" ]] || fail "mismatched target symlink was removed"
unlink "$next_tree/target"

# An ordinary child Maven failure is admissible when adapter safety and cleanup
# both succeed; it must not arm the identity tripwire.
if OPENGGF_FAKE_MAVEN=1 run_launcher "$fake_maven" --fail; then
    fail "ordinary child failure was accepted"
fi
(( launch_status == 23 )) || fail "ordinary child failure status changed: $launch_status"
assert_session_outcome FAILED true
[[ "$launch_output" == *'OPENGGF_FROZEN_NEXT_LAUNCH_END '* \
    && "$launch_output" == *'authenticated=true admissible=true'* ]] \
    || fail "ordinary child failure lacked explicit authenticated launcher admission"
ordinary_manifest=$(manifest_from_output "$launch_output")
ordinary_diagnostics="$(dirname -- "$ordinary_manifest")/diagnostics"
[[ ! -e "$ordinary_diagnostics/frozen-next-safety-failure.env" \
    && ! -e "$ordinary_diagnostics/frozen-next-identity-tripwire.env" ]] \
    || fail "ordinary child failure armed the safety tripwire"
[[ ! -e "$next_tree/target" && ! -L "$next_tree/target" ]] || fail "failed child left target"
printf 'PASS: ordinary Maven failure remains certifying terminal-red evidence\n'

# Killing the adapter shell bypasses its trap; the launcher must consume the
# coordinator marker and perform the no-follow outer recovery.
if OPENGGF_FAKE_MAVEN=1 run_launcher "$fake_maven" --kill-adapter; then
    fail "forced child termination was accepted"
fi
[[ ! -e "$next_tree/target" && ! -L "$next_tree/target" ]] || fail "outer recovery left target"
printf 'PASS: forced child termination outer recovery\n'

# Parent-visible mutations must force the final source digest invalid through
# the pinned report tripwire while outer recovery preserves the unsafe target.
for parent_mutation in nonempty replaced-directory symlink; do
    parent_ready="$test_root/parent-$parent_mutation-ready.env"
    parent_go="$test_root/parent-$parent_mutation-go"
    parent_output="$test_root/parent-$parent_mutation.out"
    parent_child_args=(--wait-for-parent-mutation)
    [[ "$parent_mutation" == symlink ]] \
        && parent_child_args+=(--emit-report --rewrite-report --fail)
    OPENGGF_FAKE_MAVEN=1 OPENGGF_TEST_PARENT_MUTATION_READY="$parent_ready" \
        OPENGGF_TEST_PARENT_MUTATION_GO="$parent_go" \
        "$launcher" --worktree "$next_tree" --expected-head "$frozen_next" \
        --harness-worktree "$harness_tree" --expected-harness-head "$frozen_harness" \
        --wrapper "$wrapper" --coordinator "$coordinator" --adapter "$adapter" \
        -- "$fake_maven" "${parent_child_args[@]}" > "$parent_output" 2>&1 &
    parent_launcher_pid=$!
    for _ in {1..400}; do [[ -f "$parent_ready" ]] && break; sleep 0.05; done
    [[ -f "$parent_ready" ]] || fail "$parent_mutation fixture did not reach mounted child"
    parent_target=$(sed -n 's/^target=//p' "$parent_ready")
    [[ "$parent_target" == "$next_tree/target" && -d "$parent_target" && ! -L "$parent_target" ]] \
        || fail "$parent_mutation fixture did not expose the ordinary parent target"
    case "$parent_mutation" in
        nonempty) printf 'foreign\n' > "$parent_target/foreign" ;;
        replaced-directory) rmdir -- "$parent_target"; mkdir -- "$parent_target" ;;
        symlink) rmdir -- "$parent_target"; ln -s -- / "$parent_target" ;;
    esac
    : > "$parent_go"
    set +e
    wait "$parent_launcher_pid"
    parent_status=$?
    set -e
    parent_text=$(<"$parent_output")
    parent_manifest=$(manifest_from_output "$parent_text")
    [[ -f "$parent_manifest" \
        && "$(<"$parent_manifest")" == *'"state": "INVALID_IDENTITY_CHANGED"'* \
        && "$parent_text" == *'valid=false'* ]] \
        || fail "$parent_mutation safety failure did not invalidate the coordinator digest"
    [[ "$parent_text" == *'authenticated identity tripwire:'* \
        && "$parent_text" == *'authenticated target cleanup failed: status=73'* ]] \
        || fail "$parent_mutation cleanup refusal lacked a diagnostic"
    parent_diagnostics="$(dirname -- "$parent_manifest")/diagnostics"
    parent_trigger="$parent_diagnostics/frozen-next-safety-failure.env"
    parent_tripwire="$parent_diagnostics/frozen-next-identity-tripwire.env"
    [[ -f "$parent_trigger" && -f "$parent_tripwire" \
        && "$(sed -n 's/^run_id=//p' "$parent_trigger")" == "$(sed -n 's/.*\"run_id\": \"\([^\"]*\)\".*/\1/p' "$parent_manifest" | head -1)" ]] \
        || fail "$parent_mutation tripwire evidence was not session-authenticated"
    assert_report_restored
    case "$parent_mutation" in
        nonempty)
            (( parent_status == 73 )) || fail "child-0 cleanup failure returned $parent_status instead of 73"
            rg -Fx 'reason=target-cleanup' "$parent_trigger" >/dev/null \
                || fail "child-0 cleanup failure used the wrong tripwire trigger"
            rg -Fx 'child_status=0' "$parent_trigger" >/dev/null \
                || fail "child-0 cleanup failure recorded the wrong child status"
            [[ -f "$parent_target/foreign" ]] || fail "non-empty target payload was removed"
            unlink -- "$parent_target/foreign"
            rmdir -- "$parent_target"
            ;;
        replaced-directory)
            (( parent_status != 0 )) || fail "replaced-directory cleanup failure was accepted"
            [[ -d "$parent_target" && ! -L "$parent_target" ]] || fail "replacement directory was removed"
            rmdir -- "$parent_target"
            ;;
        symlink)
            (( parent_status == 23 )) || fail "symlink child status was not preserved: $parent_status"
            parent_log=$(sed -n 's/.*log=\([^ ]*\).*/\1/p' "$parent_output" | tail -1)
            [[ -f "$parent_log" \
                && "$(<"$parent_log")" == *'namespace teardown changed child status: child=23 leader=75'* ]] \
                || fail "symlink fixture lacked exact namespace teardown status 75 provenance"
            rg -Fx 'reason=namespace-teardown' "$parent_trigger" >/dev/null \
                || fail "symlink fixture used the wrong tripwire trigger"
            rg -Fx 'child_status=23' "$parent_trigger" >/dev/null \
                || fail "symlink fixture did not preserve child status N in tripwire evidence"
            rg -Fx 'adapter_status=75' "$parent_trigger" >/dev/null \
                || fail "symlink fixture did not record namespace teardown status 75"
            rg -Fx 'mode=existing-dirty-report' "$parent_tripwire" >/dev/null \
                || fail "symlink fixture did not preserve the already-dirty authenticated report"
            [[ -L "$parent_target" && "$(readlink -- "$parent_target")" == / ]] \
                || fail "replacement symlink was removed or changed"
            unlink -- "$parent_target"
            ;;
    esac
done
printf 'PASS: parent target mutation tripwire and cleanup refusal\n'

# If the pinned tripwire cannot be armed, the launcher must reject the result
# even though the historical coordinator can only see a clean tracked report.
for tripwire_case in arm-failure unrelated-trigger; do
    tripwire_ready="$test_root/tripwire-$tripwire_case-ready.env"
    tripwire_go="$test_root/tripwire-$tripwire_case-go"
    tripwire_output="$test_root/tripwire-$tripwire_case.out"
    tripwire_env=(OPENGGF_FAKE_MAVEN=1 OPENGGF_TEST_PARENT_MUTATION_READY="$tripwire_ready" \
        OPENGGF_TEST_PARENT_MUTATION_GO="$tripwire_go")
    if [[ "$tripwire_case" == arm-failure ]]; then
        tripwire_env+=(OPENGGF_FROZEN_NEXT_SELF_TEST_MODE=1 OPENGGF_TEST_TRIPWIRE_ARM_FAILURE=1)
    else
        tripwire_env+=(OPENGGF_FROZEN_NEXT_SELF_TEST_MODE=1 \
            OPENGGF_TEST_TRIPWIRE_REASON_OVERRIDE=unrelated-test-trigger)
    fi
    env "${tripwire_env[@]}" "$launcher" --worktree "$next_tree" --expected-head "$frozen_next" \
        --harness-worktree "$harness_tree" --expected-harness-head "$frozen_harness" \
        --wrapper "$wrapper" --coordinator "$coordinator" --adapter "$adapter" \
        -- "$fake_maven" --wait-for-parent-mutation > "$tripwire_output" 2>&1 &
    tripwire_pid=$!
    for _ in {1..400}; do [[ -f "$tripwire_ready" ]] && break; sleep 0.05; done
    [[ -f "$tripwire_ready" ]] || fail "$tripwire_case did not reach the parent mutation barrier"
    tripwire_target=$(sed -n 's/^target=//p' "$tripwire_ready")
    printf 'foreign\n' > "$tripwire_target/foreign"
    : > "$tripwire_go"
    set +e
    wait "$tripwire_pid"
    tripwire_status=$?
    set -e
    (( tripwire_status != 0 )) || fail "$tripwire_case was accepted"
    tripwire_text=$(<"$tripwire_output")
    case "$tripwire_case" in
        arm-failure)
            (( tripwire_status == 78 )) || fail "tripwire-arm failure returned $tripwire_status instead of 78"
            [[ "$tripwire_text" == *'identity tripwire evidence is incomplete'* ]] \
                || fail "tripwire-arm failure lacked hard launcher rejection"
            ;;
        unrelated-trigger)
            [[ "$tripwire_text" == *'rejected unrelated identity tripwire trigger'* ]] \
                || fail "unrelated tripwire trigger was not rejected"
            ;;
    esac
    assert_report_restored
    [[ -f "$tripwire_target/foreign" ]] || fail "$tripwire_case removed unsafe target content"
    unlink -- "$tripwire_target/foreign"
    rmdir -- "$tripwire_target"
done
printf 'PASS: tripwire arm failure and unrelated trigger are non-certifying\n'

# The authenticated coordinator forcibly terminates the adapter before its
# signal trap can restore the generated report. Both launcher signals therefore
# digest the mutation, publish INVALID_IDENTITY_CHANGED, and only then allow
# mandatory outer recovery to restore hygiene without upgrading the manifest.
test_launcher_signal_recovery

test_authenticated_rmdir_failure

mutation_input="$test_root/runtime-input"
cp "$exclude" "$mutation_input"
if OPENGGF_RUNTIME_INPUTS="$mutation_input" OPENGGF_TEST_MUTATION_INPUT="$mutation_input" \
    OPENGGF_FAKE_MAVEN=1 run_launcher "$fake_maven" --mutate; then
    fail "runtime input mutation was accepted"
fi
[[ "$launch_output" == *'state=INVALID_IDENTITY_CHANGED'* ]] \
    || fail "runtime mutation did not produce INVALID_IDENTITY_CHANGED"
[[ ! -e "$next_tree/target" && ! -L "$next_tree/target" ]] || fail "mutated run left target"
printf 'PASS: runtime input mutation identity invalidation\n'

if run_launcher mvn clean test; then
    fail "clean Maven lifecycle was accepted"
fi
clean_manifest=$(manifest_from_output "$launch_output")
[[ -f "$clean_manifest" ]] || fail "clean rejection did not publish a manifest"
rg -F 'clean is forbidden for the frozen adapter' "$(dirname -- "$clean_manifest")/maven.log" >/dev/null \
    || fail "clean rejection did not identify the forbidden lifecycle in the session log"
[[ ! -e "$next_tree/target" && ! -L "$next_tree/target" ]] || fail "clean rejection left target"
printf 'PASS: clean lifecycle rejection\n'

for clean_goal in clean:clean org.apache.maven.plugins:maven-clean-plugin:3.2.0:clean; do
    if OPENGGF_FAKE_MAVEN=1 run_launcher "$fake_maven" "$clean_goal"; then
        review_fail "accepted Maven clean invocation: $clean_goal"
        continue
    fi
    alternate_clean_manifest=$(manifest_from_output "$launch_output")
    if [[ ! -f "$alternate_clean_manifest" ]] || \
        ! rg -F 'clean is forbidden for the frozen adapter' \
            "$(dirname -- "$alternate_clean_manifest")/maven.log" >/dev/null; then
        review_fail "did not identify forbidden Maven clean invocation: $clean_goal"
    fi
done
printf 'PASS: alternate Maven clean goal rejection\n'

for override in \
    '-Dsurefire.argLine=-Dorg.lwjgl.system.SharedLibraryExtractPath=/attacker' \
    '-DargLine=-Xmx64m' \
    '-Djava.io.tmpdir=/attacker' \
    '-Duser.home=/attacker' \
    '-Dorg.lwjgl.system.SharedLibraryExtractPath=/attacker'; do
    if OPENGGF_FAKE_MAVEN=1 run_launcher "$fake_maven" "$override"; then
        review_fail "accepted a caller fork-JVM override: $override"
    else
        override_manifest=$(manifest_from_output "$launch_output")
        if [[ ! -f "$override_manifest" ]] || \
            ! rg -F 'caller fork-JVM argument override is forbidden' \
                "$(dirname -- "$override_manifest")/maven.log" >/dev/null; then
            review_fail "fork-JVM override was not rejected: $override"
        fi
    fi
done
printf 'PASS: caller fork-JVM override rejection\n'

for override_environment in MAVEN_OPTS JAVA_TOOL_OPTIONS; do
    if [[ "$override_environment" == MAVEN_OPTS ]]; then
        if MAVEN_OPTS='-Xmx128m -Duser.home=/attacker' OPENGGF_FAKE_MAVEN=1 \
            run_launcher "$fake_maven"; then
            review_fail "accepted a preexisting MAVEN_OPTS user.home override"
        fi
    else
        if JAVA_TOOL_OPTIONS='-Duser.home=/attacker' OPENGGF_FAKE_MAVEN=1 \
            run_launcher "$fake_maven"; then
            review_fail "accepted a preexisting JAVA_TOOL_OPTIONS user.home override"
        fi
    fi
    identity_manifest=$(manifest_from_output "$launch_output")
    [[ -f "$identity_manifest" ]] || fail "$override_environment rejection omitted manifest"
    rg -F "preexisting $override_environment user.home override is forbidden" \
        "$(dirname -- "$identity_manifest")/maven.log" >/dev/null \
        || review_fail "$override_environment user.home rejection lacked a diagnostic"
done
mismatched_home="$test_root/mismatched-home"
mkdir -- "$mismatched_home"
if HOME="$mismatched_home" OPENGGF_FAKE_MAVEN=1 run_launcher "$fake_maven"; then
    review_fail "accepted canonical HOME that differs from the outer passwd home"
fi
identity_manifest=$(manifest_from_output "$launch_output")
[[ -f "$identity_manifest" ]] || fail "canonical HOME rejection omitted manifest"
rg -F 'outer passwd home does not match canonical HOME' \
    "$(dirname -- "$identity_manifest")/maven.log" >/dev/null \
    || review_fail "canonical HOME mismatch lacked a diagnostic"
printf 'PASS: mapped-root user identity override rejection\n'

if "$launcher" --worktree "$next_tree" --expected-head "$frozen_next" \
    --harness-worktree "$harness_tree" --expected-harness-head deadbeef \
    --wrapper "$wrapper" --coordinator "$coordinator" --adapter "$adapter" -- mvn test; then
    fail "wrong harness commit was accepted"
fi
if "$launcher" --worktree "$next_tree" --expected-head "$frozen_next" \
    --harness-worktree "$harness_tree" --expected-harness-head "$frozen_harness" \
    --wrapper "$project_root/tools/testing/test-frozen-next-session-adapter.sh" \
    --coordinator "$coordinator" --adapter "$adapter" -- mvn test; then
    fail "wrapper outside harness was accepted"
fi
coordinator_mismatch="$harness_tree/tools/testing/TestSessionCoordinatorSelfTest.java"
set +e
mismatch_output=$("$launcher" --worktree "$next_tree" --expected-head "$frozen_next" \
    --harness-worktree "$harness_tree" --expected-harness-head "$frozen_harness" \
    --wrapper "$wrapper" --coordinator "$coordinator_mismatch" --adapter "$adapter" -- mvn test 2>&1)
mismatch_status=$?
set -e
printf '%s\n' "$mismatch_output"
if (( mismatch_status == 0 )); then
    fail "coordinator outside harness was accepted"
fi
[[ "$mismatch_output" == *'coordinator bytes differ from expected harness blob'* ]] \
    || fail "coordinator byte mismatch did not reach authenticated byte comparison"

[[ "$(git -C "$next_tree" rev-parse HEAD)" == "$frozen_next" ]] || fail "final frozen next HEAD changed"
git -C "$next_tree" symbolic-ref -q HEAD >/dev/null && fail "final frozen next HEAD became attached"
[[ -z "$(git -C "$next_tree" status --porcelain --untracked-files=all)" ]] \
    || fail "final frozen next source inventory is dirty"
[[ ! -e "$next_tree/target" && ! -L "$next_tree/target" ]] || fail "final frozen next target remains"

(( review_failures == 0 )) || fail "$review_failures review regression case(s) remain"

printf 'PASS: frozen-next session adapter safety checks\n'
