#!/usr/bin/env bash
set -euo pipefail

tool_root=$(cd -- "$(dirname -- "$0")/.." && pwd -P)
repo_root=$(cd -- "$tool_root/../../.." && pwd -P)
nuked_source=
while (($#)); do
  case "$1" in
    --nuked-source) nuked_source=${2-}; shift 2 ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
done
[[ -n "$nuked_source" ]] || {
  echo 'usage: test-jni-proof.sh --nuked-source DIR' >&2
  exit 2
}

"$tool_root/run-jni-proof.sh" --help | grep -q -- '--nuked-source'

test_root="$repo_root/target/fm-core-jni-proof-test"
if [[ -e "$test_root" ]]; then
  echo "test output already exists: $test_root" >&2
  exit 2
fi
"$tool_root/run-jni-proof.sh" \
  --output "$test_root" --nuked-source "$nuked_source" \
  --capture "$tool_root/tests/fixtures/complete-ym-capture.jsonl"

python3 - "$test_root/result.json" <<'PY'
import json
import sys

result = json.load(open(sys.argv[1], encoding="utf-8"))
assert result["schema"] == "openggf.fm-core-jni-proof.v1"
assert result["timing_collected"] is False
assert result["java_c_pcm_frames"] > 0
assert result["java_c_pcm_mismatches"] == 0
assert result["chunking_mismatches"] == 0
assert result["snapshot_replay_mismatches"] == 0
assert result["snapshot_restored_into_fresh_handle"] is True
assert result["partial_frame_snapshot_cycle"] == 7
assert result["negative_control_changed_frames"] > 0
assert result["invalid_capacity_rejected_before_mutation"] is True
assert result["invalid_snapshot_rejected_before_mutation"] is True
assert result["use_after_close_rejected"] is True
assert result["double_close_safe"] is True
assert result["relocated_absolute_load"] is True
assert result["native_snapshot_portability"] == "same-library-build-only"
assert result["native_compiler_flags"] == ["-O2", "-fPIC", "-shared", "-Wl,-z,defs"]
assert result["java_major"] == 21
assert len(result["git_head"]) == 40
assert isinstance(result["git_dirty"], bool)
assert set(result["compiled_input_sha256"]) == {
    "NukedOpn2State.java", "NukedOpn2Tables.java", "NukedOpn2.java",
    "JniNukedProof.java", "nuked_jni.c", "ym3438.c", "ym3438.h"
}
assert all(len(value) == 64 for value in result["compiled_input_sha256"].values())
assert result["capture_replay"]["terminal_ym_cycle"] == 72
assert result["capture_replay"]["ym_events"] == 2
assert result["capture_replay"]["ym_origin_counts"] == {
    "DAC_STREAM": 0, "EXTERNAL_BUS": 2}
assert result["capture_replay"]["java_c_pcm_frames"] == 3
assert result["capture_replay"]["java_c_pcm_mismatches"] == 0
assert result["capture_replay"]["ignored_output_gate_boundaries"] == 1
assert result["capture_replay"]["presentation_pcm_reconstructable"] is False
PY

if "$tool_root/run-jni-proof.sh" \
    --output "$repo_root/jni-proof-outside-target" \
    --nuked-source "$nuked_source" >/dev/null 2>&1; then
  echo 'run-jni-proof accepted output outside target' >&2
  exit 1
fi

echo 'fm-core JNI proof tests: PASS'
