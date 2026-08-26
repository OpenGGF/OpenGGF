#!/usr/bin/env bash
# Historical child adapter: route frozen next's target tree into a coordinator session.
set -euo pipefail

die() { printf 'frozen-next adapter: %s\n' "$*" >&2; exit 2; }
canonical_dir() { [[ -d "$1" ]] || die "not a directory: $1"; (CDPATH= cd -- "$1" && pwd -P); }
sha256() { sha256sum -- "$1" | awk '{print $1}'; }
xml_property() {
    local report=$1 property=$2
    sed -n "s/.*<property name=\"$property\" value=\"\([^\"]*\)\"\/>.*/\1/p" "$report" | head -1
}

(( $# > 0 )) || die "Maven command required"
for argument in "$@"; do
    case "$argument" in
        clean|clean@*|*:clean|*:clean@*) die "clean is forbidden for the frozen adapter" ;;
    esac
    [[ "$argument" != *surefire.argLine* ]] || die "caller surefire.argLine is forbidden"
done
worktree=$(canonical_dir "${OPENGGF_TEST_WORKTREE:-}")
[[ "$(git -C "$worktree" rev-parse HEAD)" == 84d9a3761f618035dd1caa40a3d5fc72a1019693 ]] \
    || die "unexpected frozen-next HEAD"
git -C "$worktree" symbolic-ref -q HEAD >/dev/null && die "frozen-next worktree must be detached"
[[ -z "$(git -C "$worktree" status --porcelain --untracked-files=all)" ]] \
    || die "frozen-next worktree is not clean"

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
[[ "$(git -C "$worktree" config --get core.excludesFile)" == "$exclude" ]] \
    || die "external exclude is not inherited"
[[ "$(<"$exclude")" == "/target" && "$(sha256 "$exclude")" == "$(printf '/target\n' | sha256sum | awk '{print $1}')" ]] \
    || die "external exclude hash is invalid"

target_link="$worktree/target"
[[ ! -e "$target_link" && ! -L "$target_link" ]] || die "target already exists; refusing to replace it"
for variable in OPENGGF_BUILD_DIRECTORY OPENGGF_TEST_TMP_ROOT OPENGGF_TEST_DIAGNOSTICS OPENGGF_TEST_RUN_ID; do
    [[ -n "${!variable:-}" ]] || die "missing coordinator variable: $variable"
done
build=$(canonical_dir "$OPENGGF_BUILD_DIRECTORY")
tmp=$(canonical_dir "$OPENGGF_TEST_TMP_ROOT")
diagnostics=$(canonical_dir "$OPENGGF_TEST_DIAGNOSTICS")
session_root=$(canonical_dir "$(dirname -- "$OPENGGF_TEST_MANIFEST")")
surefire_reports=$(canonical_dir "$session_root/surefire-reports")
trace_reports=$(canonical_dir "$session_root/trace-reports")
marker="$diagnostics/frozen-next-session-recovery.env"
[[ ! -e "$marker" && ! -L "$marker" ]] || die "recovery marker already exists"

cleanup() {
    local linked canonical
    [[ -f "$marker" && ! -L "$marker" ]] || return 0
    [[ "$(sed -n 's/^run_id=//p' "$marker")" == "$OPENGGF_TEST_RUN_ID" ]] || return 0
    [[ "$(sed -n 's/^worktree=//p' "$marker")" == "$worktree" ]] || return 0
    [[ "$(sed -n 's/^target_link=//p' "$marker")" == "$target_link" ]] || return 0
    [[ "$(sed -n 's/^target=//p' "$marker")" == "$build" ]] || return 0
    [[ -L "$target_link" ]] || return 0
    linked=$(readlink -- "$target_link")
    [[ "$linked" == "$build" ]] || return 0
    canonical=$(readlink -f -- "$target_link")
    [[ "$canonical" == "$build" ]] || return 0
    unlink -- "$target_link"
}
trap cleanup EXIT INT TERM

printf 'run_id=%s\nworktree=%s\ntarget_link=%s\ntarget=%s\n' \
    "$OPENGGF_TEST_RUN_ID" "$worktree" "$target_link" "$build" > "$marker"
ln -s -- "$build" "$target_link"
ln -s -- "$OPENGGF_TEST_TMP_ROOT" "$build/test-tmp"
ln -s -- "$surefire_reports" "$build/surefire-reports"
ln -s -- "$trace_reports" "$build/trace-reports"
ln -s -- "$OPENGGF_TEST_DIAGNOSTICS" "$build/diagnostics"
ln -s -- "$OPENGGF_ARTIFACT_ROOT" "$build/artifacts"
ln -s -- "$OPENGGF_DISTRIBUTION_ROOT" "$build/distribution"
[[ -z "$(git -C "$worktree" status --porcelain --untracked-files=all)" ]] \
    || die "target link changed frozen-next source inventory"
ignore_attribution=$(git -C "$worktree" check-ignore -v target)
[[ "$ignore_attribution" == *"$exclude"* ]] || die "external exclude does not own target"

maven=("$@")
arg_line=$("${maven[0]}" -q -Dmse=off help:evaluate -Dexpression=surefire.argLine -DforceStdout)
[[ -n "$arg_line" && "$arg_line" != *'${'* ]] || die "could not resolve surefire.argLine"
arg_line="$arg_line -Dorg.lwjgl.system.SharedLibraryExtractPath=$OPENGGF_TEST_TMP_ROOT/lwjgl-\${surefire.forkNumber}"
set +e
"${maven[@]:0:1}" "-Dsurefire.argLine=$arg_line" "${maven[@]:1}"
status=$?
set -e

{
    printf 'run_id=%s\nworktree=%s\ntarget_link=%s\ntarget=%s\n' \
        "$OPENGGF_TEST_RUN_ID" "$worktree" "$target_link" "$build"
    printf 'report_jvm_properties:\n'
    printf 'target_ignore_attribution=%s\n' "$ignore_attribution"
    grep -hE 'name="(java.io.tmpdir|org.lwjgl.system.SharedLibraryExtractPath)"' \
        "$surefire_reports"/TEST-*.xml 2>/dev/null || true
    shopt -s nullglob
    reports=("$surefire_reports"/TEST-*.xml)
    shopt -u nullglob
    for report in "${reports[@]}"; do
        temp_lexical=$(xml_property "$report" java.io.tmpdir)
        lwjgl_lexical=$(xml_property "$report" org.lwjgl.system.SharedLibraryExtractPath)
        [[ -n "$temp_lexical" && -n "$lwjgl_lexical" ]] \
            || die "report is missing required JVM path properties: $report"
        temp_canonical=$(readlink -f -- "$temp_lexical") \
            || die "could not canonicalize report temp path: $temp_lexical"
        lwjgl_canonical=$(readlink -f -- "$lwjgl_lexical") \
            || die "could not canonicalize report LWJGL path: $lwjgl_lexical"
        printf 'report=%s\n' "$(basename -- "$report")"
        printf 'java_io_tmpdir_lexical=%s\n' "$temp_lexical"
        printf 'java_io_tmpdir_canonical=%s\n' "$temp_canonical"
        printf 'lwjgl_extract_lexical=%s\n' "$lwjgl_lexical"
        printf 'lwjgl_extract_canonical=%s\n' "$lwjgl_canonical"
    done
} > "$diagnostics/frozen-next-session-evidence.txt"
exit "$status"
