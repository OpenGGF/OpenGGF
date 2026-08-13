#!/usr/bin/env bash
set -euo pipefail

BIZHAWK_TOOL_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
source "$BIZHAWK_TOOL_DIR/common-env.sh"

HARNESS_EXE="$BIZHAWK_TOOL_DIR/bin/Release/BizHawk.Headless.Gpgx.exe"
"$BIZHAWK_TOOL_DIR/build.sh" >&2

unset DISPLAY
exec mono "$HARNESS_EXE" "$@"
