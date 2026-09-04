#!/usr/bin/env bash
set -euo pipefail

tool_root=$(cd -- "$(dirname -- "$0")/.." && pwd -P)
repo_root=$(cd -- "$tool_root/../../.." && pwd -P)
test_root="$repo_root/target/fm-core-benchmark-tests"
rm -rf -- "$test_root"
mkdir -p -- "$test_root/source" "$test_root/output"

"$tool_root/run.sh" --help | grep -q -- '--nuked-source'
"$tool_root/fetch-sources.sh" --help | grep -q -- '--output'

printf 'pinned input\n' > "$test_root/source/input.txt"
input_sha=$(sha256sum "$test_root/source/input.txt" | cut -d' ' -f1)
printf '%s  input.txt\n' "$input_sha" > "$test_root/source.lock"

python3 "$tool_root/verify-source.py" \
  --source "$test_root/source" --lock "$test_root/source.lock"

printf 'tampered\n' > "$test_root/source/input.txt"
if python3 "$tool_root/verify-source.py" \
    --source "$test_root/source" --lock "$test_root/source.lock" >/dev/null 2>&1; then
  echo 'verify-source accepted a hash mismatch' >&2
  exit 1
fi

printf 'not-a-lock-row\n' > "$test_root/malformed.lock"
if python3 "$tool_root/verify-source.py" \
    --source "$test_root/source" --lock "$test_root/malformed.lock" >/dev/null 2>&1; then
  echo 'verify-source accepted malformed lock data' >&2
  exit 1
fi
printf 'pinned input\n' > "$test_root/source/input.txt"

cat > "$test_root/java.json" <<'JSON'
{"implementation":"java-nuked","checksum":1234,"snapshot_errors":0,"negative_control_changes":19,"nanoseconds_per_frame":[10.0]}
JSON
cat > "$test_root/native.json" <<'JSON'
{"implementations":{"c-nuked":{"checksum":1234,"snapshot_errors":0,"negative_control_changes":17,"nanoseconds_per_frame":[8.0]},"cpp-ymfm":{"checksum":9876,"snapshot_errors":0,"negative_control_changes":16,"nanoseconds_per_frame":[3.0]}}}
JSON

python3 "$tool_root/assemble-results.py" \
  --java "$test_root/java.json" --native "$test_root/native.json" \
  --output "$test_root/output/result.json" --repo "$repo_root" \
  --frames 128 --warmups 0 --iterations 1 \
  --nuked-lock "$test_root/source.lock" --ymfm-lock "$test_root/source.lock" \
  --build-input fixture="$test_root/source/input.txt" \
  --native-c-flags=-O2 --native-cxx-flags='-O2 -std=c++14'

python3 - "$test_root/output/result.json" <<'PY'
import json
import sys
result = json.load(open(sys.argv[1], encoding="utf-8"))
assert result["validation"] == {
    "java_c_nuked_checksum_match": True,
    "snapshot_replay": "pass",
    "active_negative_controls": "pass",
}
assert result["measurement"]["publishable"] is False
assert len(result["source_pins"]["nuked"]["lock_sha256"]) == 64
assert len(result["source_pins"]["ymfm"]["lock_sha256"]) == 64
assert result["build"]["inputs"]["fixture"] == "569dcc66411eb4426c8032cd03aeb5b1128c1ad63024377ec80deee1cd215e08"
assert result["build"]["native_cxx_flags"] == "-O2 -std=c++14"
PY

python3 - "$test_root/native.json" <<'PY'
import json
import sys
path = sys.argv[1]
value = json.load(open(path, encoding="utf-8"))
value["implementations"]["c-nuked"]["checksum"] = 4321
json.dump(value, open(path, "w", encoding="utf-8"))
PY
if python3 "$tool_root/assemble-results.py" \
    --java "$test_root/java.json" --native "$test_root/native.json" \
    --output "$test_root/output/mismatch.json" --repo "$repo_root" \
    --frames 128 --warmups 0 --iterations 1 \
    --nuked-lock "$test_root/source.lock" --ymfm-lock "$test_root/source.lock" \
    --build-input fixture="$test_root/source/input.txt" \
    --native-c-flags=-O2 --native-cxx-flags='-O2 -std=c++14' >/dev/null 2>&1; then
  echo 'assemble-results accepted a Java/C checksum mismatch' >&2
  exit 1
fi

python3 - "$test_root/native.json" <<'PY'
import json
import sys
path = sys.argv[1]
value = json.load(open(path, encoding="utf-8"))
value["implementations"]["c-nuked"]["checksum"] = 1234
value["implementations"]["cpp-ymfm"]["negative_control_changes"] = 0
json.dump(value, open(path, "w", encoding="utf-8"))
PY
if python3 "$tool_root/assemble-results.py" \
    --java "$test_root/java.json" --native "$test_root/native.json" \
    --output "$test_root/output/inert.json" --repo "$repo_root" \
    --frames 128 --warmups 0 --iterations 1 \
    --nuked-lock "$test_root/source.lock" --ymfm-lock "$test_root/source.lock" \
    --build-input fixture="$test_root/source/input.txt" \
    --native-c-flags=-O2 --native-cxx-flags='-O2 -std=c++14' >/dev/null 2>&1; then
  echo 'assemble-results accepted an inert negative control' >&2
  exit 1
fi

outside=$(mktemp -d)
trap 'rm -rf -- "$outside"' EXIT
if "$tool_root/run.sh" --output "$outside/result" \
    --nuked-source "$test_root/source" --ymfm-source "$test_root/source" \
    --frames 8 --warmups 0 --iterations 1 >/dev/null 2>&1; then
  echo 'run.sh accepted output outside the worktree target directory' >&2
  exit 1
fi

ln -s "$outside" "$test_root/escape-link"
if "$tool_root/run.sh" --output "$test_root/escape-link/nested/created-outside" \
    --nuked-source "$test_root/source" --ymfm-source "$test_root/source" \
    --frames 8 --warmups 0 --iterations 1 >/dev/null 2>&1; then
  echo 'run.sh accepted a target symlink escape' >&2
  exit 1
fi
if [[ -e "$outside/nested" ]]; then
  echo 'run.sh created an output directory before rejecting a symlink escape' >&2
  exit 1
fi
if "$tool_root/fetch-sources.sh" \
    --output "$test_root/escape-link/fetch-nested/sources" >/dev/null 2>&1; then
  echo 'fetch-sources accepted a target symlink escape' >&2
  exit 1
fi
if [[ -e "$outside/fetch-nested" ]]; then
  echo 'fetch-sources created a directory before rejecting a symlink escape' >&2
  exit 1
fi

echo 'fm-core-benchmark boundary tests: PASS'
