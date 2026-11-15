#!/usr/bin/env bash
# Build (once) and run tools/soniccd/fmv_native_probe.cs against official BizHawk 2.11 (lane END, 2026-11-12).
# Usage: fmv_native_probe.sh <schedule.json> <new output dir>
# Needs the user's USA disc and BIOS under $SONICCD_MEDIA (default ~/Downloads/Sonic-CD/media), the BizHawk 2.11
# tree under docs/BizHawk-2.11-linux-x64 of the main checkout, and tools/soniccd/oracle_probe.cs from the frozen branch.
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
OG=${OPENGGF_MAIN:-$(git -C "$HERE" rev-parse --path-format=absolute --git-common-dir | xargs dirname)}
DLL=$OG/docs/BizHawk-2.11-linux-x64/dll
M=${SONICCD_MEDIA:-$HOME/Downloads/Sonic-CD/media}
B=${FMVPROBE_BUILD:-$HOME/Downloads/Sonic-CD/native/rehost-programs/_tools/build}
mkdir -p "$B"; ln -sfn "$DLL" "$B/dll"
if [ ! -e "$B/fmv_native_probe.exe" ] || [ "$HERE/fmv_native_probe.cs" -nt "$B/fmv_native_probe.exe" ]; then
  git -C "$OG" show feature/ai-nothing-to-see-here:tools/soniccd/oracle_probe.cs > "$B/oracle_probe.cs"
  refs=(); for n in BizHawk.Common.dll BizHawk.Emulation.Common.dll BizHawk.Emulation.Cores.dll BizHawk.Emulation.DiscSystem.dll BizHawk.BizInvoke.dll Newtonsoft.Json.dll; do refs+=("/reference:$DLL/$n"); done
  mono /usr/lib/mono/msbuild/Current/bin/Roslyn/csc.exe /nologo /langversion:9 /deterministic+ /main:FmvProbe "/out:$B/fmv_native_probe.exe" /reference:/usr/lib/mono/4.8-api/Facades/netstandard.dll "${refs[@]}" "$HERE/fmv_native_probe.cs" "$B/oracle_probe.cs"
fi
unset DISPLAY WAYLAND_DISPLAY PULSE_SERVER JACK_DEFAULT_SERVER
export MONO_PATH=$DLL LD_LIBRARY_PATH=$DLL TMPDIR=$B
cd "$B"
exec mono fmv_native_probe.exe "$M/USA/Sonic CD (US).cue" "$M/BIOS" "$2" "$1" f4f315adcef9b8feb0364c21ab7f0eaf5457f3ed
