#!/usr/bin/env bash
# Exercises the frozen-next compatibility adapter against the immutable next parent.
set -euo pipefail

if [[ "${OPENGGF_FAKE_MAVEN:-}" == 1 ]]; then
    if [[ " $* " == *' help:evaluate '* ]]; then
        printf '%s\n' '-Xshare:off -Xmx1g'
        exit 0
    fi
    if [[ " $* " == *' --mutate '* ]]; then
        mutation=${OPENGGF_TEST_MUTATION_INPUT:?}
        printf 'changed\n' >> "$mutation"
    fi
    if [[ " $* " == *' --kill-adapter '* ]]; then
        kill -KILL "$PPID"
        sleep 2
    fi
    if [[ " $* " == *' --replace-target-equivalent '* ]]; then
        target_link="$OPENGGF_TEST_WORKTREE/target"
        replacement="$OPENGGF_BUILD_DIRECTORY/../build"
        unlink "$target_link"
        ln -s -- "$replacement" "$target_link"
        if [[ " $* " == *' --kill-after-replace '* ]]; then
            kill -KILL "$PPID"
            sleep 2
        fi
    fi
    if [[ " $* " == *' --wait-for-launcher-interrupt '* ]]; then
        printf 'fake_pid=%s\nadapter_pid=%s\nmanifest=%s\n' \
            "$$" "$PPID" "$OPENGGF_TEST_MANIFEST" > "${OPENGGF_TEST_INTERRUPT_READY:?}"
        while :; do sleep 1; done
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
frozen_harness="f1b82774d4aeb9585e75bd74e90856e7b67256d7"

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
trap 'git worktree remove --force "$next_tree" 2>/dev/null || true; git worktree remove --force "$harness_tree" 2>/dev/null || true' EXIT

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

# The selected guards are independent and force two Surefire processes when forkCount=2.
run_launcher mvn -DforkCount=2 -Dtest=TestAudioBackendBypassGuard,TestProductionAwtBlacklistGuard test
success_manifest=$(manifest_from_output "$launch_output")
[[ -f "$success_manifest" ]] || fail "successful session did not publish a manifest"
evidence="$(dirname -- "$success_manifest")/diagnostics/frozen-next-session-evidence.txt"
success_tmp="$(dirname -- "$success_manifest")/tmp"
rg -F 'TEST-com.openggf.audio.TestAudioBackendBypassGuard.xml' "$success_manifest" >/dev/null \
    || fail "successful manifest omitted the audio guard report"
rg -F 'TEST-com.openggf.game.TestProductionAwtBlacklistGuard.xml' "$success_manifest" >/dev/null \
    || fail "successful manifest omitted the AWT guard report"
[[ "$(rg -Fxc "java_io_tmpdir_lexical=$next_tree/target/test-tmp" "$evidence")" == 2 ]] \
    || fail "successful session did not retain both lexical fork temp paths"
[[ "$(rg -Fxc "java_io_tmpdir_canonical=$success_tmp" "$evidence")" == 2 ]] \
    || fail "successful session did not retain both canonical fork temp paths"
for fork in 1 2; do
    rg -Fx "lwjgl_extract_lexical=$success_tmp/lwjgl-$fork" "$evidence" >/dev/null \
        || fail "successful session omitted lexical fork $fork LWJGL path"
    rg -Fx "lwjgl_extract_canonical=$success_tmp/lwjgl-$fork" "$evidence" >/dev/null \
        || fail "successful session omitted canonical fork $fork LWJGL path"
done
rg -F "target_ignore_attribution=$exclude" "$evidence" >/dev/null \
    || fail "external exclude was not attributed during the run"
printf 'PASS: successful two-fork frozen-next session\n'

[[ ! -e "$next_tree/target" && ! -L "$next_tree/target" ]] || fail "successful run left target"
[[ -z "$(git -C "$next_tree" status --porcelain --untracked-files=all)" ]] || fail "successful run dirtied frozen next"
[[ "$(git -C "$next_tree" rev-parse HEAD)" == "$frozen_next" ]] || fail "successful run changed frozen next HEAD"
git -C "$next_tree" symbolic-ref -q HEAD >/dev/null && fail "successful run attached frozen next HEAD"

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

# A child Maven failure is still required to remove the adapter-created link.
if OPENGGF_FAKE_MAVEN=1 run_launcher "$fake_maven" --fail; then
    fail "ordinary child failure was accepted"
fi
[[ ! -e "$next_tree/target" && ! -L "$next_tree/target" ]] || fail "failed child left target"
printf 'PASS: ordinary Maven failure cleanup\n'

# Killing the adapter shell bypasses its trap; the launcher must consume the
# coordinator marker and perform the no-follow outer recovery.
if OPENGGF_FAKE_MAVEN=1 run_launcher "$fake_maven" --kill-adapter; then
    fail "forced child termination was accepted"
fi
[[ ! -e "$next_tree/target" && ! -L "$next_tree/target" ]] || fail "outer recovery left target"
printf 'PASS: forced child termination outer recovery\n'

# A replacement symlink whose raw payload differs from the adapter-created
# absolute target must survive even when it resolves to the same build path.
OPENGGF_FAKE_MAVEN=1 run_launcher "$fake_maven" --replace-target-equivalent
equivalent_manifest=$(manifest_from_output "$launch_output")
equivalent_build=$(sed -n 's/.*"build_root": "\([^"]*\)".*/\1/p' "$equivalent_manifest" | head -1)
equivalent_raw="$equivalent_build/../build"
if [[ ! -L "$next_tree/target" ]]; then
    review_fail "normal child/outer cleanup unlinked a same-canonical different-raw target"
elif [[ "$(readlink -- "$next_tree/target")" != "$equivalent_raw" ]]; then
    fail "normal cleanup changed the replacement target payload"
else
    unlink "$next_tree/target"
fi
printf 'PASS: same-canonical different-raw normal cleanup refusal\n'

if OPENGGF_FAKE_MAVEN=1 run_launcher "$fake_maven" --replace-target-equivalent --kill-after-replace; then
    fail "forced equivalent-target child termination was accepted"
fi
equivalent_manifest=$(manifest_from_output "$launch_output")
equivalent_build=$(sed -n 's/.*"build_root": "\([^"]*\)".*/\1/p' "$equivalent_manifest" | head -1)
equivalent_raw="$equivalent_build/../build"
if [[ ! -L "$next_tree/target" ]]; then
    review_fail "outer recovery unlinked a same-canonical different-raw target"
elif [[ "$(readlink -- "$next_tree/target")" != "$equivalent_raw" ]]; then
    fail "outer recovery changed the replacement target payload"
else
    unlink "$next_tree/target"
fi
printf 'PASS: same-canonical different-raw outer recovery refusal\n'

# Stop the launcher before killing its adapter child so the coordinator can
# publish a recoverable marker while the launcher remains interrupted.
interrupt_ready="$test_root/launcher-interrupt-ready.env"
interrupt_output="$test_root/launcher-interrupt.out"
interrupt_tmp="$test_root/launcher-tmp"
mkdir "$interrupt_tmp"
TMPDIR="$interrupt_tmp" OPENGGF_FAKE_MAVEN=1 OPENGGF_TEST_INTERRUPT_READY="$interrupt_ready" \
    "$launcher" --worktree "$next_tree" --expected-head "$frozen_next" \
    --harness-worktree "$harness_tree" --expected-harness-head "$frozen_harness" \
    --wrapper "$wrapper" --coordinator "$coordinator" --adapter "$adapter" \
    -- "$fake_maven" --wait-for-launcher-interrupt > "$interrupt_output" 2>&1 &
launcher_pid=$!
for _ in {1..200}; do
    [[ -f "$interrupt_ready" ]] && break
    sleep 0.05
done
[[ -f "$interrupt_ready" ]] || fail "launcher interruption child did not become ready"
fake_pid=$(sed -n 's/^fake_pid=//p' "$interrupt_ready")
adapter_pid=$(sed -n 's/^adapter_pid=//p' "$interrupt_ready")
interrupt_manifest=$(sed -n 's/^manifest=//p' "$interrupt_ready")
kill -STOP "$launcher_pid"
kill -KILL "$fake_pid" 2>/dev/null || true
kill -KILL "$adapter_pid"
for _ in {1..200}; do
    [[ -f "$interrupt_manifest" ]] && rg -q '"state": "FAILED"' "$interrupt_manifest" && break
    sleep 0.05
done
[[ -f "$interrupt_manifest" ]] && rg -q '"state": "FAILED"' "$interrupt_manifest" \
    || fail "launcher interruption coordinator did not finalize the failed child"
kill -TERM "$launcher_pid"
kill -CONT "$launcher_pid"
set +e
wait "$launcher_pid"
interrupt_status=$?
set -e
(( interrupt_status == 143 )) || fail "interrupted launcher did not preserve TERM status: $interrupt_status"
if [[ -L "$next_tree/target" ]]; then
    interrupt_build=$(sed -n 's/.*"build_root": "\([^"]*\)".*/\1/p' "$interrupt_manifest" | head -1)
    [[ "$(readlink -- "$next_tree/target")" == "$interrupt_build" ]] \
        || fail "launcher interruption left an unexpected target payload"
    unlink "$next_tree/target"
    review_fail "launcher interruption skipped outer recovery"
elif [[ -e "$next_tree/target" ]]; then
    fail "launcher interruption replaced target with a non-symlink"
fi
shopt -s nullglob
interrupt_captures=("$interrupt_tmp"/*)
shopt -u nullglob
(( ${#interrupt_captures[@]} == 0 )) || fail "launcher interruption retained its private capture"
printf 'PASS: interrupted launcher outer recovery\n'

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

if OPENGGF_FAKE_MAVEN=1 run_launcher "$fake_maven" \
    '-Dsurefire.argLine=-Dorg.lwjgl.system.SharedLibraryExtractPath=/attacker'; then
    review_fail "accepted a caller-supplied surefire.argLine override"
else
    override_manifest=$(manifest_from_output "$launch_output")
    if [[ ! -f "$override_manifest" ]] || \
        ! rg -F 'caller surefire.argLine is forbidden' \
            "$(dirname -- "$override_manifest")/maven.log" >/dev/null; then
        review_fail "surefire.argLine override was not rejected by the adapter"
    fi
fi
printf 'PASS: caller surefire.argLine override rejection\n'

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
