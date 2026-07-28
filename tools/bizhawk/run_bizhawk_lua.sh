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
#   BIZHAWK_HOME        BizHawk install dir. Default: repo-local
#                       docs/BizHawk-2.11-linux-x64. An explicit override is
#                       allowed, but no unversioned system build is selected.
#   OGGF_WORKDIR        directory to cd into before launch (default: $PWD). NOTE:
#                       BizHawk resets CWD to the Lua script's own directory, so
#                       recorders that write a CWD-relative trace_output/ (s1/s2)
#                       land it next to the .lua, NOT here. Use
#                       OGGF_TRACE_OUTPUT_DIR with a recorder that honors it
#                       (s1_complete / s2_ss / s3k*) to control the location.
#   OGGF_TRACE_OUTPUT_DIR  passed through unchanged to the recorder.
#   BIZHAWK_ALLOW_SLOW_LUA=1  drop --chromeless so Lua load/parse errors surface
#                       in a window instead of failing silently (first-run gate).
#   OGGF_BIZHAWK_SOFTGL=1  force Mesa software GL (llvmpipe). Default is hardware
#                       GL, which the repo-local build needs to load a movie.
#   OGGF_NO_LUACONSOLE=1  don't pass --luaconsole.
#   BIZHAWK_EXTRA_ARGS  extra args appended after the positional three.
#
# Environment prep this script encodes for BizHawk 2.11 on a
# CachyOS/Wayland + mono box:
#   * BizHawk runs "portable" and writes config/system dirs beside EmuHawk.exe,
#     so BIZHAWK_HOME must be a WRITABLE tree. The repo-local
#     docs/BizHawk-2.11-linux-x64 build is the supported default.
#   * DISPLAY must be set (EmuHawk is WinForms even headless); XWayland :0 works
#     (the X BadMatch on the display control is a non-fatal layout warning).
#   * --luaconsole avoids a "Stack empty" crash that command-line --lua + --movie
#     otherwise throws in LuaConsole.EnableLuaFile on this mono build.
#   * BizHawk 2.11 provides client.invisibleemulation for fast no-render capture.
#
# The launcher keeps the established Mono, display, and GL defaults; validate a
# first capture against a known trace before using a new host configuration.
set -euo pipefail

if [ "$#" -lt 3 ]; then
	echo "Usage: $(basename "$0") <lua_script> <bk2_movie> <rom> [extra args...]" >&2
	exit 2
fi

LUA_SCRIPT=$(realpath "$1"); shift
BK2_PATH=$(realpath "$1"); shift
ROM_PATH=$(realpath "$1"); shift

# Resolve BIZHAWK_HOME: explicit env wins; otherwise search candidate locations
# for a build containing EmuHawk.exe. The repo-local docs/BizHawk-2.11-linux-x64
# build is required by default. It is untracked and lives in whichever checkout
# downloaded it, so across git worktrees you may need to set BIZHAWK_HOME
# explicitly.
SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
if [ -z "${BIZHAWK_HOME:-}" ]; then
	# git-toplevel/docs covers the main checkout; ../../docs covers this script's
	# checkout; the sibling OpenGGF checkout covers the worktree case.
	GIT_TOP=$(cd "$SCRIPT_DIR" && git rev-parse --show-toplevel 2>/dev/null || true)
	for cand in \
		"$SCRIPT_DIR"/../../docs/BizHawk-2.11-linux-x64 \
		${GIT_TOP:+"$GIT_TOP"/docs/BizHawk-2.11-linux-x64} \
		"$SCRIPT_DIR"/../../../OpenGGF/docs/BizHawk-2.11-linux-x64; do
		if [ -f "$cand/EmuHawk.exe" ]; then BIZHAWK_HOME="$cand"; break; fi
	done
fi
BIZHAWK_HOME="${BIZHAWK_HOME:-"$SCRIPT_DIR/../../docs/BizHawk-2.11-linux-x64"}"
EMUHAWK_EXE="$BIZHAWK_HOME/EmuHawk.exe"
# Resolve mono to an absolute path so the launcher is independent of PATH (a
# non-login/background shell may not have /usr/bin on PATH).
MONO_BIN="${MONO_BIN:-$(command -v mono || true)}"
[ -n "$MONO_BIN" ] || { for m in /usr/bin/mono /usr/local/bin/mono; do [ -x "$m" ] && { MONO_BIN="$m"; break; }; done; }
for p in "$LUA_SCRIPT" "$BK2_PATH" "$ROM_PATH" "$EMUHAWK_EXE" "$MONO_BIN"; do
	[ -n "$p" ] && [ -e "$p" ] || { echo "Missing: ${p:-mono (set MONO_BIN)}" >&2; exit 2; }
done

# Authoritative shared-lib path for the recorders' oggf_lib_dir() loader —
# mirrors the OGGF_BIZHAWK_LIB export in run_bizhawk_lua.bat. Trailing slash
# matches the loader's dir .. "file" concatenation.
LUA_DIR=$(dirname "$LUA_SCRIPT")
export OGGF_BIZHAWK_LIB="${OGGF_BIZHAWK_LIB:-$LUA_DIR/lib/}"
export OGGF_BIZHAWK_PROBE_RUNTIME="${OGGF_BIZHAWK_PROBE_RUNTIME:-$SCRIPT_DIR/probes/probe_runtime.lua}"

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
# Mono repaints the entire Lua Console pane for every print, making long
# recorder runs progressively slower. Keep launcher-driven captures quiet by
# default; set OGGF_TRACE_QUIET=0 when interactive recorder status is useful.
export OGGF_TRACE_QUIET="${OGGF_TRACE_QUIET:-1}"
# The repo-local build loads BK2s only under hardware GL, so hardware GL is the
# default. Set OGGF_BIZHAWK_SOFTGL=1 to force Mesa software GL (llvmpipe) — a
# fallback for GPUs where the hardware GL context fails to go current
# ("eglMakeCurrent … EGL_BAD_ACCESS"), though software GL has not been observed
# to load a movie on this box.
if [ "${OGGF_BIZHAWK_SOFTGL:-}" = "1" ]; then
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

exec "$MONO_BIN" "$EMUHAWK_EXE" "${ARGS[@]}"
