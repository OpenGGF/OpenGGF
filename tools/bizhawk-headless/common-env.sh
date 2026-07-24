#!/usr/bin/env bash
set -euo pipefail

BIZHAWK_TOOL_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
BIZHAWK_REPO_ROOT="$(cd "$BIZHAWK_TOOL_DIR/../.." && pwd -P)"

if [[ -v BIZHAWK_HOME ]]; then
  if [[ ! -e "$BIZHAWK_HOME" ]]; then
    echo "BIZHAWK_HOME does not exist: $BIZHAWK_HOME" >&2
    return 1 2>/dev/null || exit 1
  fi
  BIZHAWK_HOME="$(realpath "$BIZHAWK_HOME")"
else
  BIZHAWK_HOME="$BIZHAWK_REPO_ROOT/docs/BizHawk-2.11-linux-x64"
fi

if [[ "$BIZHAWK_HOME" != /* || ! -d "$BIZHAWK_HOME" ]]; then
  echo "BIZHAWK_HOME must resolve to an existing absolute directory: $BIZHAWK_HOME" >&2
  return 1 2>/dev/null || exit 1
fi

command -v mono >/dev/null || {
  echo "Required command not found: mono" >&2
  return 1 2>/dev/null || exit 1
}
command -v xbuild >/dev/null || {
  echo "Required command not found: xbuild" >&2
  return 1 2>/dev/null || exit 1
}

for required_file in \
  BizHawk.Common.dll \
  BizHawk.Emulation.Common.dll \
  BizHawk.Emulation.Cores.dll \
  BizHawk.Emulation.DiscSystem.dll \
  BizHawk.BizInvoke.dll \
  Newtonsoft.Json.dll \
  gpgx.wbx.zst \
  libwaterboxhost.so
do
  if [[ ! -f "$BIZHAWK_HOME/dll/$required_file" ]]; then
    echo "Required BizHawk file not found: $BIZHAWK_HOME/dll/$required_file" >&2
    return 1 2>/dev/null || exit 1
  fi
done

export BIZHAWK_HOME
export MONO_PATH="$BIZHAWK_HOME/dll${MONO_PATH:+:$MONO_PATH}"
export LD_LIBRARY_PATH="$BIZHAWK_HOME/dll${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
