#!/usr/bin/env bash
# run_bizhawk_lua.sh — Linux counterpart to run_bizhawk_lua.bat.
#
# Launches an EmuHawk Lua/BK2/ROM combination headless via mono, with the same
# recorder-facing contract as the Windows launcher:
#   * exports OGGF_BIZHAWK_LIB=<lua dir>/lib so the recorders' oggf_lib_dir()
#     loader is authoritative for lib/oggf_trace_common.lua on this route;
#   * runs from a controllable working directory so recorders that write a
#     CWD-relative trace_output/ (e.g. s1_trace_recorder.lua) land it there;
#   * passes OGGF_TRACE_OUTPUT_DIR straight through for recorders that honor it.
#
# Usage:
#   run_bizhawk_lua.sh <lua_script> <bk2_movie> <rom> [extra EmuHawk args...]
#
# Env:
#   BIZHAWK_HOME        BizHawk install dir (default: /opt/bizhawk)
#   OGGF_WORKDIR        directory to cd into before launch (default: $PWD).
#                       CWD-relative trace_output/ is created here.
#   OGGF_TRACE_OUTPUT_DIR  passed through unchanged to the recorder.
#   BIZHAWK_ALLOW_SLOW_LUA=1  drop --chromeless so Lua load/parse errors surface
#                       in a window instead of failing silently (first-run gate).
#   OGGF_BIZHAWK_HWGL=1  keep hardware GL (default forces Mesa software GL).
#   OGGF_NO_LUACONSOLE=1  don't pass --luaconsole.
#   BIZHAWK_EXTRA_ARGS  extra args appended after the positional three.
#
# Environment prep this script encodes (learned bringing BizHawk 2.11.1 up on a
# CachyOS/Wayland + mono box):
#   * BizHawk runs "portable" and writes config/system dirs beside EmuHawk.exe.
#     /opt/bizhawk is root-owned, so point BIZHAWK_HOME at a writable copy
#     (cp -a /opt/bizhawk ~/.local/share/bizhawk-run).
#   * DISPLAY must be set (EmuHawk is WinForms even headless); XWayland :0 works.
#   * Hardware GL under XWayland dies with EGL_BAD_ACCESS, so software GL is
#     forced by default (or set config DispMethod=1 for the GDI+ renderer).
#   * --luaconsole avoids a "Stack empty" crash that command-line --lua + --movie
#     otherwise throws in LuaConsole.EnableLuaFile.
#
# KNOWN BLOCKER (BizHawk 2.11.1 + mono, NOT this launcher): with a BK2 passed via
# --movie, EmuHawk hangs inside the movie-load path right after "WaterboxHost
# Sealed" / "GPGX Controller report", before the main form's OnShown fires — so
# the recorder's Lua never runs and no trace_output/ is produced. Verified with
# --chromeless and windowed, OpenGL/software-GL/GDI+, with and without
# --luaconsole; the hung process maps no X window (it is not a dismissible
# dialog). Launching the SAME recorder Lua WITHOUT --movie runs fine (Lua loads,
# frames advance, clean exit), which isolates the fault to command-line BK2
# loading on this build. A working headless BK2 path (different BizHawk build,
# Xvfb, or a Lua-side movie loader) is still needed before this launcher can
# drive an end-to-end trace regen on Linux.
set -euo pipefail

if [ "$#" -lt 3 ]; then
	echo "Usage: $(basename "$0") <lua_script> <bk2_movie> <rom> [extra args...]" >&2
	exit 2
fi

LUA_SCRIPT=$(realpath "$1"); shift
BK2_PATH=$(realpath "$1"); shift
ROM_PATH=$(realpath "$1"); shift

BIZHAWK_HOME="${BIZHAWK_HOME:-/opt/bizhawk}"
EMUHAWK_EXE="$BIZHAWK_HOME/EmuHawk.exe"
for p in "$LUA_SCRIPT" "$BK2_PATH" "$ROM_PATH" "$EMUHAWK_EXE"; do
	[ -e "$p" ] || { echo "Missing: $p" >&2; exit 2; }
done

# Authoritative shared-lib path for the recorders' oggf_lib_dir() loader —
# mirrors the OGGF_BIZHAWK_LIB export in run_bizhawk_lua.bat. Trailing slash
# matches the loader's dir .. "file" concatenation.
LUA_DIR=$(dirname "$LUA_SCRIPT")
export OGGF_BIZHAWK_LIB="${OGGF_BIZHAWK_LIB:-$LUA_DIR/lib/}"

# Native deps (EmuHawkMono.sh replicates this per-distro; /usr/lib covers Arch).
export LD_LIBRARY_PATH="$BIZHAWK_HOME/dll:$BIZHAWK_HOME:/usr/lib${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
export MONO_CRASH_NOFILE=1
export MONO_WINFORMS_XIM_STYLE=disabled
# EmuHawk is a WinForms app: it needs a reachable X server even headless
# (--chromeless still constructs the main form + a display control). Without a
# DISPLAY the mono window never loads and the --lua script never runs. Default
# to :0 (XWayland is fine — the resulting BadMatch on the display control is a
# non-fatal layout warning). Override DISPLAY to point at Xvfb for true headless.
export DISPLAY="${DISPLAY:-:0}"
# Hardware GL under XWayland fails with "eglMakeCurrent … EGL_BAD_ACCESS", which
# hangs --chromeless and crashes windowed. Force Mesa's software rasteriser so
# the GL context creation succeeds. (Alternatively set config DispMethod=1 for
# the GDI+ renderer.) Override OGGF_BIZHAWK_HWGL=1 to keep hardware GL.
if [ "${OGGF_BIZHAWK_HWGL:-}" != "1" ]; then
	export LIBGL_ALWAYS_SOFTWARE="${LIBGL_ALWAYS_SOFTWARE:-1}"
	export GALLIUM_DRIVER="${GALLIUM_DRIVER:-llvmpipe}"
fi

ARGS=(--audiosync false)
if [ "${BIZHAWK_ALLOW_SLOW_LUA:-}" != "1" ]; then
	ARGS+=(--chromeless)   # headless; omit to show the window so a Lua load error is visible
fi
# --luaconsole: with a movie loaded, command-line --lua otherwise throws
# "Stack empty" in LuaConsole.EnableLuaFile (OnShown -> LoadFromCommandLine) on
# the mono build; opening the console first avoids that crash. Skip via
# OGGF_NO_LUACONSOLE=1.
[ "${OGGF_NO_LUACONSOLE:-}" = "1" ] || ARGS+=(--luaconsole)
ARGS+=(--lua "$LUA_SCRIPT" --movie "$BK2_PATH" "$ROM_PATH")
# shellcheck disable=SC2206
[ -n "${BIZHAWK_EXTRA_ARGS:-}" ] && ARGS+=($BIZHAWK_EXTRA_ARGS)
ARGS+=("$@")

WORKDIR="${OGGF_WORKDIR:-$PWD}"
mkdir -p "$WORKDIR"
cd "$WORKDIR"

echo "=== BizHawk Lua Launcher (Linux) ==="
echo "EmuHawk:  $EMUHAWK_EXE"
echo "Lua:      $LUA_SCRIPT"
echo "Movie:    $BK2_PATH"
echo "ROM:      $ROM_PATH"
echo "Workdir:  $WORKDIR"
echo "LibDir:   $OGGF_BIZHAWK_LIB"
[ -n "${OGGF_TRACE_OUTPUT_DIR:-}" ] && echo "OutDir:   $OGGF_TRACE_OUTPUT_DIR"
echo

exec mono "$EMUHAWK_EXE" "${ARGS[@]}"
