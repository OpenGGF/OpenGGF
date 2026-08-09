#!/usr/bin/env bash
# Deterministic two-run Sonic 1 GHZ music-driver parity capture.
set -uo pipefail

EXIT_MATCH=0
EXIT_USAGE=2
EXIT_MISMATCH=3
EXIT_TOOL_FAILURE=4

usage() {
	cat <<'EOF'
Usage: tools/audio/run_s1_audio_parity.sh [options]

Options:
  --rom PATH           Sonic 1 World REV01 .gen (otherwise discover at repository root)
  --movie PATH         pinned sound-test BK2 fixture
  --bizhawk-home PATH  BizHawk 2.11 Linux x64 installation (or BIZHAWK_HOME)
  --output-root PATH   run parent (default: target/audio-parity/s1-ghz)
  -h, --help           show this help

Exit codes: 0=match, 2=usage, 3=mismatch, 4=capture/tool failure.
All four detailed captures and both reports remain in the printed unique run directory.
EOF
}

fail() {
	echo "S1 audio parity capture/tool failure: $*" >&2
	exit "$EXIT_TOOL_FAILURE"
}

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
REPO=$(cd "$SCRIPT_DIR/../.." && pwd)
ROM_PATH=""
MOVIE_PATH="$REPO/src/test/resources/audio/parity/s1/s1-soundtest-ghz.bk2"
BIZHAWK_DIR="${BIZHAWK_HOME:-}"
OUTPUT_ROOT="$REPO/target/audio-parity/s1-ghz"
COMMON_GIT_DIR=$(git -C "$REPO" rev-parse --path-format=absolute --git-common-dir 2>/dev/null || true)
MAIN_REPO=""
[ -n "$COMMON_GIT_DIR" ] && MAIN_REPO=$(dirname "$COMMON_GIT_DIR")

while [ "$#" -gt 0 ]; do
	case "$1" in
		--rom|--movie|--bizhawk-home|--output-root)
			[ "$#" -ge 2 ] || { echo "Argument error: $1 requires a value" >&2; usage >&2; exit "$EXIT_USAGE"; }
			case "$1" in
				--rom) ROM_PATH=$2 ;;
				--movie) MOVIE_PATH=$2 ;;
				--bizhawk-home) BIZHAWK_DIR=$2 ;;
				--output-root) OUTPUT_ROOT=$2 ;;
			esac
			shift 2 ;;
		-h|--help) usage; exit "$EXIT_MATCH" ;;
		*) echo "Argument error: unknown option: $1" >&2; usage >&2; exit "$EXIT_USAGE" ;;
	esac
done

if [ -z "$BIZHAWK_DIR" ]; then
	for candidate in "$REPO/docs/BizHawk-2.11-linux-x64" \
		"$MAIN_REPO/docs/BizHawk-2.11-linux-x64"; do
		if [ -f "$candidate/EmuHawk.exe" ]; then
			BIZHAWK_DIR=$candidate
			break
		fi
	done
fi
[ -n "$BIZHAWK_DIR" ] || fail "BizHawk 2.11 home was not found; pass --bizhawk-home or set BIZHAWK_HOME"

if [ -n "${OGGF_AUDIO_PARITY_JAVA_BIN:-}" ]; then
	# Narrow test/process seam: one executable, never a shell fragment or eval.
	[ -x "$OGGF_AUDIO_PARITY_JAVA_BIN" ] || fail "OGGF_AUDIO_PARITY_JAVA_BIN is not executable"
	JAVA_TOOL=("$OGGF_AUDIO_PARITY_JAVA_BIN")
else
	CLASSPATH_FILE="$REPO/target/s1-audio-parity.classpath"
	if ! mvn -q -Pci -DskipTests compile dependency:build-classpath \
		-Dmdep.outputFile="$CLASSPATH_FILE" -f "$REPO/pom.xml"; then
		fail "Maven could not compile the parity tool or resolve its runtime classpath"
	fi
	[ -s "$CLASSPATH_FILE" ] || fail "Maven did not produce the parity tool classpath"
	JAVA_CP="$REPO/target/classes:$(<"$CLASSPATH_FILE")"
	JAVA_TOOL=(java -cp "$JAVA_CP" com.openggf.tools.audio.parity.S1AudioParityTool)
fi

VALIDATE_ARGS=(validate --repo "$REPO" --movie "$MOVIE_PATH" --bizhawk-home "$BIZHAWK_DIR" \
	--output-root "$OUTPUT_ROOT")
if [ -n "$ROM_PATH" ]; then
	VALIDATE_ARGS+=(--rom "$ROM_PATH")
else
	ROM_SEARCH_ROOT=$REPO
	[ -z "$MAIN_REPO" ] || ROM_SEARCH_ROOT=$MAIN_REPO
	VALIDATE_ARGS+=(--rom-search-root "$ROM_SEARCH_ROOT")
fi
VALIDATED=$("${JAVA_TOOL[@]}" "${VALIDATE_ARGS[@]}") || fail "input validation failed"
declare -A VALIDATION_SEEN=()
while IFS= read -r record; do
	[[ "$record" == *=* && "${record#*=}" != *=* ]] || fail "malformed validation record"
	key=${record%%=*}
	value=${record#*=}
	[ -n "$value" ] || fail "empty validation record: $key"
	[[ "$value" != *$'\r'* && "$value" != *$'\t'* ]] || fail "control character in validation record: $key"
	case "$key" in
		ROM_PATH|MOVIE_PATH|BIZHAWK_HOME|OUTPUT_ROOT) ;;
		*) fail "unknown validation record: $key" ;;
	esac
	[[ ! -v "VALIDATION_SEEN[$key]" ]] || fail "duplicate validation record: $key"
	case "$key" in
		ROM_PATH) ROM_PATH=$value ;;
		MOVIE_PATH) MOVIE_PATH=$value ;;
		BIZHAWK_HOME) BIZHAWK_DIR=$value ;;
		OUTPUT_ROOT) OUTPUT_ROOT=$value ;;
	esac
	VALIDATION_SEEN[$key]=1
done <<< "$VALIDATED"
for key in ROM_PATH MOVIE_PATH BIZHAWK_HOME OUTPUT_ROOT; do
	[[ -v "VALIDATION_SEEN[$key]" ]] || fail "missing validation record: $key"
done

mkdir -p -- "$OUTPUT_ROOT" || fail "cannot create safe output root: $OUTPUT_ROOT"
RUN_DIR=$(mktemp -d "$OUTPUT_ROOT/run.XXXXXXXX") || fail "cannot create a fresh run directory"
REFERENCE_1="$RUN_DIR/reference-1.jsonl"
REFERENCE_2="$RUN_DIR/reference-2.jsonl"
OPENGGF_1="$RUN_DIR/openggf-1.jsonl"
OPENGGF_2="$RUN_DIR/openggf-2.jsonl"
PROBE="$REPO/tools/bizhawk/probes/s1_audio_driver_parity_probe.lua"
LAUNCHER="$REPO/tools/bizhawk/run_bizhawk_lua.sh"

capture_reference() {
	local output=$1
	local log=$2
	if ! OGGF_OUT="$output" BIZHAWK_HOME="$BIZHAWK_DIR" \
		"$LAUNCHER" "$PROBE" "$MOVIE_PATH" "$ROM_PATH" >"$log" 2>&1; then
		return 1
	fi
	[ -s "$output" ]
}

capture_engine() {
	local reference=$1
	local output=$2
	"${JAVA_TOOL[@]}" capture --repo "$REPO" --run-root "$RUN_DIR" \
		--reference "$reference" --rom "$ROM_PATH" --output "$output"
}

echo "Run directory: $RUN_DIR"
echo "Recording BizHawk reference capture 1/2..."
capture_reference "$REFERENCE_1" "$RUN_DIR/reference-1.log" || fail "BizHawk reference capture 1 failed; see $RUN_DIR/reference-1.log"
echo "Recording BizHawk reference capture 2/2..."
capture_reference "$REFERENCE_2" "$RUN_DIR/reference-2.log" || fail "BizHawk reference capture 2 failed; see $RUN_DIR/reference-2.log"
cmp -s -- "$REFERENCE_1" "$REFERENCE_2" || fail "normalized BizHawk captures differ byte-for-byte"

echo "Recording OpenGGF capture 1/2 for the reference terminal count..."
capture_engine "$REFERENCE_1" "$OPENGGF_1" || fail "OpenGGF capture 1 failed"
echo "Recording OpenGGF capture 2/2 for the reference terminal count..."
capture_engine "$REFERENCE_1" "$OPENGGF_2" || fail "OpenGGF capture 2 failed"
cmp -s -- "$OPENGGF_1" "$OPENGGF_2" || fail "normalized OpenGGF captures differ byte-for-byte"

HUMAN_REPORT="$RUN_DIR/parity-report.txt"
JSON_REPORT="$RUN_DIR/parity-report.json"
"${JAVA_TOOL[@]}" compare --repo "$REPO" --run-root "$RUN_DIR" \
	--reference "$REFERENCE_1" --openggf "$OPENGGF_1" \
	--human-report "$HUMAN_REPORT" --json-report "$JSON_REPORT"
RESULT=$?
echo "Detailed captures preserved: $RUN_DIR"
echo "Parity report: $HUMAN_REPORT"
echo "Machine summary: $JSON_REPORT"
case "$RESULT" in
	"$EXIT_MATCH"|"$EXIT_MISMATCH") exit "$RESULT" ;;
	*) fail "comparison could not validate both captures; see $HUMAN_REPORT" ;;
esac
