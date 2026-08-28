#!/usr/bin/env bash
set -euo pipefail

BIZHAWK_TOOL_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
source "$BIZHAWK_TOOL_DIR/common-env.sh"
cd "$BIZHAWK_TOOL_DIR"

ROSLYN_CSC="/usr/lib/mono/msbuild/Current/bin/Roslyn/csc.exe"
ROSLYN_CSC_SHA256="81e98ade50f3e4127237128211778bd6ebe0c3998c9cc2f5eb44f3196a0297f8"
ROSLYN_CSC_VERSION="3.9.0-6.21124.20 (db94f4cc)"
if [[ ! -f "$ROSLYN_CSC" ]]; then
  echo "Required deterministic Roslyn compiler not found: $ROSLYN_CSC" >&2
  exit 1
fi
actual_csc_sha256="$(/usr/bin/sha256sum "$ROSLYN_CSC" | /usr/bin/awk '{print $1}')"
if [[ "$actual_csc_sha256" != "$ROSLYN_CSC_SHA256" ]]; then
  echo "Deterministic Roslyn compiler SHA-256 mismatch: $actual_csc_sha256" >&2
  exit 1
fi
actual_csc_version="$(/usr/bin/mono "$ROSLYN_CSC" -version)"
if [[ "$actual_csc_version" != "$ROSLYN_CSC_VERSION" ]]; then
  echo "Deterministic Roslyn compiler version mismatch: $actual_csc_version" >&2
  exit 1
fi

ROSLYN_CSC_DIR="$(cd "$(dirname "$ROSLYN_CSC")" && pwd -P)"
BUILD_OBJECT_DIR="$BIZHAWK_TOOL_DIR/obj"
if [[ -L "$BUILD_OBJECT_DIR" ]] ||
  [[ -e "$BUILD_OBJECT_DIR" && ! -d "$BUILD_OBJECT_DIR" ]]
then
  echo "Build object path must be a local directory: $BUILD_OBJECT_DIR" >&2
  exit 1
fi
mkdir -p "$BUILD_OBJECT_DIR"
DETERMINISTIC_RESPONSE_DIR="$(
  /usr/bin/mktemp -d "$BUILD_OBJECT_DIR/.deterministic.XXXXXX"
)"
DETERMINISTIC_RESPONSE_FILE="$DETERMINISTIC_RESPONSE_DIR/roslyn.rsp"
cleanup_deterministic_response() {
  rm -rf -- "$DETERMINISTIC_RESPONSE_DIR"
}
trap cleanup_deterministic_response EXIT
printf '%s\n' \
  '/deterministic+' \
  "\"/pathmap:$BIZHAWK_TOOL_DIR=/_/openggf/tools/bizhawk-headless\"" \
  >"$DETERMINISTIC_RESPONSE_FILE"

# The project produced a library before the production CLI transition.
# Remove that generated artifact so Mono cannot resolve the stale DLL ahead
# of the executable assembly with the same identity.
LEGACY_HARNESS_DLL="$BIZHAWK_TOOL_DIR/bin/Release/BizHawk.Headless.Gpgx.dll"
if [[ -f "$LEGACY_HARNESS_DLL" ]]; then
  rm -- "$LEGACY_HARNESS_DLL"
fi

/usr/bin/xbuild /nologo /verbosity:minimal \
  /property:Configuration=Release \
  /property:BizHawkDllDir="$BIZHAWK_HOME/dll" \
  /property:CscToolPath="$ROSLYN_CSC_DIR" \
  /property:CscToolExe=csc.exe \
  /property:CompilerResponseFile="$DETERMINISTIC_RESPONSE_FILE" \
  BizHawk.Headless.Gpgx.csproj
/usr/bin/xbuild /nologo /verbosity:minimal \
  /property:Configuration=Release \
  /property:BizHawkDllDir="$BIZHAWK_HOME/dll" \
  /property:CscToolPath="$ROSLYN_CSC_DIR" \
  /property:CscToolExe=csc.exe \
  /property:CompilerResponseFile="$DETERMINISTIC_RESPONSE_FILE" \
  BizHawk.Headless.Gpgx.Tests.csproj
