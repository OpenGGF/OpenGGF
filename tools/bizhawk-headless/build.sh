#!/usr/bin/env bash
set -euo pipefail

BIZHAWK_TOOL_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
source "$BIZHAWK_TOOL_DIR/common-env.sh"
cd "$BIZHAWK_TOOL_DIR"

# The project produced a library before the production CLI transition.
# Remove that generated artifact so Mono cannot resolve the stale DLL ahead
# of the executable assembly with the same identity.
LEGACY_HARNESS_DLL="$BIZHAWK_TOOL_DIR/bin/Release/BizHawk.Headless.Gpgx.dll"
if [[ -f "$LEGACY_HARNESS_DLL" ]]; then
  rm -- "$LEGACY_HARNESS_DLL"
fi

xbuild /nologo /verbosity:minimal \
  /property:Configuration=Release \
  /property:BizHawkDllDir="$BIZHAWK_HOME/dll" \
  BizHawk.Headless.Gpgx.csproj
xbuild /nologo /verbosity:minimal \
  /property:Configuration=Release \
  /property:BizHawkDllDir="$BIZHAWK_HOME/dll" \
  BizHawk.Headless.Gpgx.Tests.csproj
