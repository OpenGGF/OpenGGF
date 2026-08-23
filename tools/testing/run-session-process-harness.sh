#!/usr/bin/env bash
set -euo pipefail

script_dir="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)"
project_root="$(CDPATH= cd -- "$script_dir/../.." && pwd -P)"
if [[ -n "${OPENGGF_HARNESS_ROOT:-}" ]]; then
    harness_root="$OPENGGF_HARNESS_ROOT"
else
    harness_root="$(mktemp -d "${TMPDIR:-/tmp}/openggf-session-harness.XXXXXX")"
fi
classes="$(mktemp -d "${TMPDIR:-/tmp}/openggf-session-harness-classes.XXXXXX")"
trap 'rm -rf "$classes"' EXIT

javac --release 21 -d "$classes" "$script_dir/TestSessionProcessHarness.java"
java -ea -cp "$classes" TestSessionProcessHarness "$project_root" "$harness_root"
