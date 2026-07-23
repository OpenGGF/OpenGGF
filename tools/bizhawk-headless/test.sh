#!/usr/bin/env bash
set -euo pipefail

BIZHAWK_TOOL_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
BIZHAWK_REPO_ROOT="$(cd "$BIZHAWK_TOOL_DIR/../.." && pwd -P)"
BIZHAWK_DEFAULT_HOME="$BIZHAWK_REPO_ROOT/docs/BizHawk-2.11-linux-x64"
BIZHAWK_HOME_WAS_SET=false
if [[ -v BIZHAWK_HOME ]]; then
  BIZHAWK_HOME_WAS_SET=true
fi

if [[ "$#" -ge 2 && "$1" == "--filter" ]] &&
   { [[ "$2" == "EndToEnd" ]] || [[ "$2" == "GpgxHost" ]]; } &&
   [[ "$BIZHAWK_HOME_WAS_SET" == false && ! -d "$BIZHAWK_DEFAULT_HOME" ]]
then
  echo "SKIP $2: BizHawk distribution not installed"
  exit 0
fi

source "$BIZHAWK_TOOL_DIR/common-env.sh"
"$BIZHAWK_TOOL_DIR/build.sh"
unset DISPLAY
exec mono "$BIZHAWK_TOOL_DIR/bin/Release/BizHawk.Headless.Gpgx.Tests.exe" "$@"
