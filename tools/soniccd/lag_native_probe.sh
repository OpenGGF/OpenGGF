#!/usr/bin/env bash
# Sonic CD lag probe driver (LAG lane, 2026-11-13).
# Purpose: build tools/soniccd/lag_native_probe.cs against BizHawk 2.11 and run it on one movie.
# Usage: lag_native_probe.sh build <build dir>
#        lag_native_probe.sh run <build dir> <movie.bk2> <new output dir>
# Inputs: BIZHAWK_DLL (BizHawk 2.11's dll directory) and SONICCD_MEDIA (the user's media root holding
# USA/Sonic CD (US).cue and BIOS/). One BizHawk instance at a time.
# Origin: the build/run lines of rehost-fixtures/_tools/build_probe.sh and probe.sh.
set -euo pipefail
H=$(cd "$(dirname "$0")" && pwd)
DLL=${BIZHAWK_DLL:?set BIZHAWK_DLL to the BizHawk 2.11 dll directory}
M=${SONICCD_MEDIA:?set SONICCD_MEDIA to the Sonic CD media root}
BIOS_SHA1=f4f315adcef9b8feb0364c21ab7f0eaf5457f3ed
B=$2
case "$1" in
  build)
    mkdir -p "$B"; ln -sfn "$DLL" "$B/dll"
    refs=(); for n in BizHawk.Common.dll BizHawk.Emulation.Common.dll BizHawk.Emulation.Cores.dll BizHawk.Emulation.DiscSystem.dll BizHawk.BizInvoke.dll Newtonsoft.Json.dll; do refs+=("/reference:$DLL/$n"); done
    mono /usr/lib/mono/msbuild/Current/bin/Roslyn/csc.exe /nologo /langversion:9 /deterministic+ /main:LagProbe \
      "/out:$B/lag_native_probe.exe" /reference:/usr/lib/mono/4.8-api/Facades/netstandard.dll \
      /reference:System.IO.Compression.dll /reference:System.IO.Compression.FileSystem.dll "${refs[@]}" \
      "$H/lag_native_probe.cs" "$H/oracle_probe.cs" ;;
  run)
    unset DISPLAY WAYLAND_DISPLAY PULSE_SERVER JACK_DEFAULT_SERVER
    export MONO_PATH=$DLL LD_LIBRARY_PATH=$DLL TMPDIR=$B
    cd "$B"
    exec mono lag_native_probe.exe "$M/USA/Sonic CD (US).cue" "$M/BIOS" "$BIOS_SHA1" "$3" "$4" ;;
  *) echo "usage: $0 build|run ..." >&2; exit 64 ;;
esac
