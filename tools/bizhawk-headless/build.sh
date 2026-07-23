#!/usr/bin/env bash
set -euo pipefail

BIZHAWK_TOOL_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
source "$BIZHAWK_TOOL_DIR/common-env.sh"
cd "$BIZHAWK_TOOL_DIR"

xbuild /nologo /verbosity:minimal \
  /property:Configuration=Release \
  /property:BizHawkDllDir="$BIZHAWK_HOME/dll" \
  BizHawk.Headless.Gpgx.csproj
xbuild /nologo /verbosity:minimal \
  /property:Configuration=Release \
  /property:BizHawkDllDir="$BIZHAWK_HOME/dll" \
  BizHawk.Headless.Gpgx.Tests.csproj
