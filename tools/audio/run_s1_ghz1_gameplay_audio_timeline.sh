#!/usr/bin/env bash
# Deterministic two-producer capture for the pinned S1 GHZ1 gameplay-audio timeline.
set -euo pipefail

EXIT_MATCH=0
EXIT_USAGE=2
EXIT_MISMATCH=3
EXIT_TOOL_FAILURE=4

usage() {
	cat <<'EOF'
Usage: tools/audio/run_s1_ghz1_gameplay_audio_timeline.sh --rom PATH [--bizhawk-home PATH]

Records two independent pinned BizHawk reference streams and two independent
OpenGGF streams beneath target/audio-parity/s1-ghz1-gameplay/. Each reference
probe writes only to a fresh staging file; the trusted Java boundary validates
and atomically create-new publishes it. Existing captures and reports are never
replaced.

Exit codes: 0=match, 2=usage, 3=parity mismatch, 4=capture/tool failure.
EOF
}

fail() {
	echo "S1 GHZ1 gameplay-audio timeline capture/tool failure: $*" >&2
	exit "$EXIT_TOOL_FAILURE"
}

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
REPO=$(cd "$SCRIPT_DIR/../.." && pwd)
MOVIE="$REPO/src/test/resources/traces/s1/runs/s1-sonic-complete-withemeralds/sonic1-complete-withemeralds.bk2"
OUTPUT_ROOT="$REPO/target/audio-parity/s1-ghz1-gameplay"
PROBE="$REPO/tools/bizhawk/probes/s1_ghz1_gameplay_audio_timeline_probe.lua"
LAUNCHER="$REPO/tools/bizhawk/run_bizhawk_lua.sh"
ROM_PATH=""
BIZHAWK_DIR="${BIZHAWK_HOME:-}"

for replacement in OGGF_AUDIO_TIMELINE_JAVA_BIN OGGF_AUDIO_TIMELINE_MONO_BIN \
	OGGF_AUDIO_PARITY_JAVA_BIN MONO_BIN BIZHAWK_EXTRA_ARGS; do
	if [[ -v "$replacement" ]]; then
		fail "unsupported command replacement environment variable: $replacement"
	fi
done

while [ "$#" -gt 0 ]; do
	case "$1" in
		--rom|--bizhawk-home)
			[ "$#" -ge 2 ] || { echo "Argument error: $1 requires a value" >&2; usage >&2; exit "$EXIT_USAGE"; }
			case "$1" in
				--rom) ROM_PATH=$2 ;;
				--bizhawk-home) BIZHAWK_DIR=$2 ;;
			esac
			shift 2 ;;
		-h|--help) usage; exit "$EXIT_MATCH" ;;
		*) echo "Argument error: unknown option: $1" >&2; usage >&2; exit "$EXIT_USAGE" ;;
	esac
done

[ -n "$ROM_PATH" ] || { echo "Argument error: --rom is required" >&2; usage >&2; exit "$EXIT_USAGE"; }
if [ -z "$BIZHAWK_DIR" ]; then
	for candidate in "$REPO/docs/BizHawk-2.11-linux-x64" "$(dirname "$REPO")/OpenGGF/docs/BizHawk-2.11-linux-x64"; do
		if [ -f "$candidate/EmuHawk.exe" ]; then BIZHAWK_DIR=$candidate; break; fi
	done
fi
[ -n "$BIZHAWK_DIR" ] || fail "BizHawk 2.11 home was not found; pass --bizhawk-home"

CLASSPATH_FILE="$REPO/target/s1-gameplay-audio-timeline.classpath"
if ! mvn -q -Dmse=off -Pci -DskipTests compile dependency:build-classpath \
	-Dmdep.outputFile="$CLASSPATH_FILE" -f "$REPO/pom.xml"; then
	fail "Maven could not compile the trusted timeline tool"
fi
[ -s "$CLASSPATH_FILE" ] || fail "Maven did not produce the timeline tool classpath"
JAVA_TOOL=(java -cp "$REPO/target/classes:$(<"$CLASSPATH_FILE")" com.openggf.tools.audio.timeline.S1GameplayAudioTimelineTool)

VALIDATED=$("${JAVA_TOOL[@]}" validate --repo "$REPO" --rom "$ROM_PATH" --movie "$MOVIE" \
	--bizhawk-home "$BIZHAWK_DIR" --output-root "$OUTPUT_ROOT") || fail "pinned input validation failed"
# The Java response is generated only after all pinned identities pass; split only fixed key=value records.
declare -A validated=()
while IFS='=' read -r key value; do
	case "$key" in ROM_PATH|MOVIE_PATH|BIZHAWK_HOME|OUTPUT_ROOT) ;; *) fail "invalid validation response" ;; esac
	[ -n "$value" ] || fail "empty validation response"
	[[ "$value" != *$'\001'* && "$value" != *$'\037'* && "$value" != *$'\177'* ]] || fail "control character in validation response"
	[[ -v "validated[$key]" ]] && fail "duplicate validation response"
	validated[$key]=$value
done <<< "$VALIDATED"
for key in ROM_PATH MOVIE_PATH BIZHAWK_HOME OUTPUT_ROOT; do [[ -v "validated[$key]" ]] || fail "missing validation response: $key"; done
ROM_PATH=${validated[ROM_PATH]}
MOVIE=${validated[MOVIE_PATH]}
BIZHAWK_DIR=${validated[BIZHAWK_HOME]}
OUTPUT_ROOT=${validated[OUTPUT_ROOT]}

mkdir -p -- "$OUTPUT_ROOT" || fail "cannot create safe output root"
RUN_DIR=$(mktemp -d "$OUTPUT_ROOT/run.XXXXXXXX") || fail "cannot create a unique run directory"
REFERENCE_1="$RUN_DIR/reference-1.jsonl"
REFERENCE_2="$RUN_DIR/reference-2.jsonl"
OPENGGF_1="$RUN_DIR/openggf-1.jsonl"
OPENGGF_2="$RUN_DIR/openggf-2.jsonl"
HUMAN_REPORT="$RUN_DIR/parity-report.txt"
JSON_REPORT="$RUN_DIR/parity-report.json"

capture_reference() {
	local output=$1 log=$2 staging
	staging=$(mktemp "$RUN_DIR/reference.XXXXXXXX.staging") || return 1
	if ! BIZHAWK_HOME="$BIZHAWK_DIR" OGGF_OUT="$staging" "$LAUNCHER" "$PROBE" "$MOVIE" "$ROM_PATH" >"$log" 2>&1; then
		"${JAVA_TOOL[@]}" publish-reference --repo "$REPO" --run-root "$RUN_DIR" --staging "$staging" --output "$output" >/dev/null 2>&1 || true
		return 1
	fi
	"${JAVA_TOOL[@]}" publish-reference --repo "$REPO" --run-root "$RUN_DIR" --staging "$staging" --output "$output"
}

capture_openggf() {
	local output=$1 log=$2
	mvn -Dmse=off -Pci \
		-Dtest=com.openggf.tools.audio.timeline.TestS1Ghz1OpenGgfAudioTimelineCapture#captureRequestedOutput \
		-Ds1.audio.timeline.run.path="$RUN_DIR" -Ds1.audio.timeline.output="$output" test >"$log" 2>&1
}

echo "Run directory: $RUN_DIR"
echo "Recording BizHawk reference capture 1/2..."
capture_reference "$REFERENCE_1" "$RUN_DIR/reference-1.log" || fail "BizHawk capture 1 failed; see $RUN_DIR/reference-1.log"
echo "Recording BizHawk reference capture 2/2..."
capture_reference "$REFERENCE_2" "$RUN_DIR/reference-2.log" || fail "BizHawk capture 2 failed; see $RUN_DIR/reference-2.log"
cmp -s -- "$REFERENCE_1" "$REFERENCE_2" || fail "BizHawk captures differ byte-for-byte"

echo "Recording OpenGGF capture 1/2..."
capture_openggf "$OPENGGF_1" "$RUN_DIR/openggf-1.log" || fail "OpenGGF capture 1 failed; see $RUN_DIR/openggf-1.log"
echo "Recording OpenGGF capture 2/2..."
capture_openggf "$OPENGGF_2" "$RUN_DIR/openggf-2.log" || fail "OpenGGF capture 2 failed; see $RUN_DIR/openggf-2.log"
cmp -s -- "$OPENGGF_1" "$OPENGGF_2" || fail "OpenGGF captures differ byte-for-byte"

set +e
"${JAVA_TOOL[@]}" compare --repo "$REPO" --run-root "$RUN_DIR" --reference "$REFERENCE_1" --openggf "$OPENGGF_1" \
	--human-report "$HUMAN_REPORT" --json-report "$JSON_REPORT"
RESULT=$?
set -e
echo "Detailed captures, logs, and reports preserved: $RUN_DIR"
case "$RESULT" in
	"$EXIT_MATCH"|"$EXIT_MISMATCH") exit "$RESULT" ;;
	*) fail "comparison failed; see preserved logs in $RUN_DIR" ;;
esac
