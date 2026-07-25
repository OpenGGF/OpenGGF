#!/usr/bin/env bash
set -euo pipefail

BIZHAWK_TOOL_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
BIZHAWK_REPO_ROOT="$(cd "$BIZHAWK_TOOL_DIR/../.." && pwd -P)"
BIZHAWK_DEFAULT_HOME="$BIZHAWK_REPO_ROOT/docs/BizHawk-2.11-linux-x64"
BIZHAWK_S1_REV01_SHA1="69E102855D4389C3FD1A8F3DC7D193F8EEE5FE5B"
BIZHAWK_HOME_WAS_SET=false
if [[ -v BIZHAWK_HOME ]]; then
  BIZHAWK_HOME_WAS_SET=true
fi

if [[ "$#" -ge 2 && "$1" == "--filter" ]] &&
   { [[ "$2" == "EndToEnd" ]] || [[ "$2" == "GpgxHost" ]]; } &&
   [[ -v S1_ROM_PATH ]]
then
  if [[ ! -f "$S1_ROM_PATH" ]]; then
    echo "Supplied S1_ROM_PATH does not exist: $S1_ROM_PATH" >&2
    exit 1
  fi
  command -v sha1sum >/dev/null || {
    echo "Required command not found: sha1sum" >&2
    exit 1
  }
  BIZHAWK_S1_ACTUAL_SHA1="$(
    sha1sum -- "$S1_ROM_PATH" | awk '{ print toupper($1) }'
  )"
  if [[ "$BIZHAWK_S1_ACTUAL_SHA1" != "$BIZHAWK_S1_REV01_SHA1" ]]; then
    echo "S1_ROM_PATH SHA-1 was $BIZHAWK_S1_ACTUAL_SHA1; expected $BIZHAWK_S1_REV01_SHA1." >&2
    exit 1
  fi
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
