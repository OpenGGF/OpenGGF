#!/usr/bin/bash -p
set -euo pipefail

script_dir=${BASH_SOURCE[0]%/*}; [[ "$script_dir" != "${BASH_SOURCE[0]}" ]] || script_dir=.
script_dir=$(cd -P -- "$script_dir" && pwd)
source "$script_dir/secure-runtime.sh"
fail() { /usr/bin/printf 'build-core: %s\n' "$*" >&2; exit 1; }
verify_stock() {
  local root=$1 expected relative
  while read -r expected relative; do
    /usr/bin/printf '%s  %s\n' "$expected" "$root/$relative" | /usr/bin/sha256sum -c - >/dev/null \
      || { /usr/bin/printf 'stock artifact differs: %s\n' "$relative" >&2; return 1; }
  done <<'LOCKED_STOCK'
b2d4be5e2a766a5161cc26f3af2a90753c39d64c91c54a9884171aed09e21df3 EmuHawk.exe
0144e6e236be68ce126eb771dcb5a9ae7c153a083fa0333f345ac37b4a60acf7 dll/BizHawk.Emulation.Cores.dll
f20cd009f6f5b0a95bd47b66c48dc8de85afcd7ae0cc6aab3486baf55f501fb4 dll/BizHawk.Emulation.Common.dll
8d05389bf0e02be1244bdc7a2adcd93b4cff95acf199fc927987ca699760a1b7 dll/BizHawk.BizInvoke.dll
438a49d6a45d9fcac17016240ae205d1af7a4632865f6f70468b684b82323f33 dll/BizHawk.Common.dll
d2367818aafb4e520ad5ab005b5762c61506b0c819c4d79687235acfb0fc0c78 dll/libwaterboxhost.so
c4231296ec5ba59b431df22b68e234ae7bfbbfc87b6e72fa471234ac1b220d12 dll/gpgx.wbx.zst
LOCKED_STOCK
}

source_dir= toolchain_dir= stock_dir= output=
while (($#)); do
  case "$1" in
    --source) source_dir=${2-}; shift 2 ;;
    --toolchain) toolchain_dir=${2-}; shift 2 ;;
    --stock) stock_dir=${2-}; shift 2 ;;
    --output) output=${2-}; shift 2 ;;
    *) fail "unknown argument: $1" ;;
  esac
done
secure_require_absent_output "$output"
recipe=$script_dir/task7-build-recipe.json
expected_recipe=$(/usr/bin/jq -er '.build_recipe.sha256' "$script_dir/artifact-lock.json")
actual_recipe=$(/usr/bin/sha256sum "$recipe"); actual_recipe=${actual_recipe%% *}
[[ "$actual_recipe" = "$expected_recipe" ]] || fail "Task 7 build recipe differs: $actual_recipe"
while IFS=$'\t' read -r relative expected; do
  [[ "$relative" != /* && "$relative" != *'..'* && -f "$script_dir/$relative" && ! -L "$script_dir/$relative" ]] \
    || fail "unsafe or missing Task 7 recipe input: $relative"
  observed=$(/usr/bin/sha256sum "$script_dir/$relative"); observed=${observed%% *}
  [[ "$observed" = "$expected" ]] || fail "Task 7 recipe input differs: $relative"
done < <(/usr/bin/jq -er '.versioned_inputs | to_entries[] | [.key,.value] | @tsv' "$recipe")
for pair in "source:$source_dir" "toolchain:$toolchain_dir" "stock:$stock_dir"; do
  name=${pair%%:*}; value=${pair#*:}
  [[ "$value" = /* && -d "$value" && ! -L "$value" ]] \
    || fail "$name must be an absolute, non-symlink directory"
done
caller_stock=$stock_dir
verify_stock "$caller_stock" || fail "caller stock distribution differs before build"

parent=${output%/*}; [[ -n "$parent" ]] || parent=/
stage=$(/usr/bin/mktemp -d "$parent/.gpgx-observer-build-staging.XXXXXX")
cleanup() {
  status=$?; trap - EXIT; stock_status=0
  verify_stock "$caller_stock" || stock_status=$?
  [[ -z "${stage-}" || ! -d "$stage" ]] || /usr/bin/rm -rf -- "$stage"
  if ((stock_status)); then /usr/bin/printf 'build-core: caller stock changed during build\n' >&2; exit 1; fi
  exit "$status"
}
trap cleanup EXIT
/usr/bin/mkdir "$stage/build-source" "$stage/toolchain-input" "$stage/stock-input"
/usr/bin/cp -a -- "$source_dir/." "$stage/build-source/"
/usr/bin/cp -a -- "$toolchain_dir/." "$stage/toolchain-input/"
/usr/bin/cp -a -- "$stock_dir/." "$stage/stock-input/"
source_dir=$stage/build-source
toolchain_dir=$stage/toolchain-input
stock_dir=$stage/stock-input

stock_core=$stock_dir/dll/gpgx.wbx.zst
verify_stock "$stock_dir" || fail "snapshotted stock distribution differs"
verified_identity=$(/usr/bin/bash -p "$script_dir/verify-inputs.sh" \
  --source "$source_dir" --toolchain "$toolchain_dir")
adapter_source=$script_dir/../../src/Core/GpgxAudioObserverAdapter.cs
host_source=$script_dir/../../src/Core/GpgxHost.cs
adapter_source_sha=$(/usr/bin/sha256sum "$adapter_source"); adapter_source_sha=${adapter_source_sha%% *}
host_source_sha=$(/usr/bin/sha256sum "$host_source"); host_source_sha=${host_source_sha%% *}
[[ "$adapter_source_sha" = "$(/usr/bin/jq -er '.managed_reflection.adapter_source_sha256' "$script_dir/artifact-lock.json")" \
  && "$host_source_sha" = "$(/usr/bin/jq -er '.managed_reflection.host_source_sha256' "$script_dir/artifact-lock.json")" \
  && "$adapter_source_sha" = "$(/usr/bin/jq -er '.managed_reflection_inputs.adapter_source_sha256' "$recipe")" \
  && "$host_source_sha" = "$(/usr/bin/jq -er '.managed_reflection_inputs.host_source_sha256' "$recipe")" \
  && "$(/usr/bin/jq -er '.managed_reflection.bizinvoke_sha256' "$script_dir/artifact-lock.json")" = "$(/usr/bin/jq -er '.managed_reflection_inputs.bizinvoke_sha256' "$recipe")" \
  && "$(/usr/bin/jq -er '.managed_reflection.bizhawk_common_sha256' "$script_dir/artifact-lock.json")" = "$(/usr/bin/jq -er '.managed_reflection_inputs.bizhawk_common_sha256' "$recipe")" ]] \
  || fail "managed reflection adapter source differs from lock"

patch=$script_dir/0001-buffer-z80-audio-events.patch
expected_patch=$(/usr/bin/jq -er '.native_patch.sha256' "$script_dir/artifact-lock.json")
actual_patch=$(/usr/bin/sha256sum "$patch"); actual_patch=${actual_patch%% *}
[[ "$actual_patch" = "$expected_patch" ]] || fail "native patch differs: $actual_patch"
/usr/bin/env -i HOME=/nonexistent XDG_CONFIG_HOME=/nonexistent PATH=/usr/bin:/bin \
  LC_ALL=C GIT_CONFIG_NOSYSTEM=1 GIT_TERMINAL_PROMPT=0 \
  /usr/bin/git -c core.hooksPath=/dev/null -C "$source_dir" apply --check "$patch" \
  || fail "native patch does not apply cleanly"
/usr/bin/env -i HOME=/nonexistent XDG_CONFIG_HOME=/nonexistent PATH=/usr/bin:/bin \
  LC_ALL=C GIT_CONFIG_NOSYSTEM=1 GIT_TERMINAL_PROMPT=0 \
  /usr/bin/git -c core.hooksPath=/dev/null -C "$source_dir" apply --whitespace=error-all "$patch" \
  || fail "native patch application failed"
callgraph_root=$source_dir/waterbox/gpgx/Genesis-Plus-GX/core
fm_calls=$(
  cd "$callgraph_root"
  /usr/bin/grep -nH 'fm_write(' mem68k.c memz80.c | LC_ALL=C /usr/bin/sort
)
psg_calls=$(
  cd "$callgraph_root"
  /usr/bin/grep -nH 'psg_write(' mem68k.c memz80.c membnk.c | LC_ALL=C /usr/bin/sort
)
[[ "$(/usr/bin/printf '%s\n' "$fm_calls" | /usr/bin/wc -l)" = 7 ]] \
  || fail "Genesis FM issue callgraph differs"
[[ "$(/usr/bin/printf '%s\n' "$psg_calls" | /usr/bin/wc -l)" = 8 ]] \
  || fail "Genesis PSG issue callgraph differs"
backend_assignments=$(
  cd "$callgraph_root"
  /usr/bin/grep -nH 'fm_write_impl = YM' sound/sound.c | LC_ALL=C /usr/bin/sort
)
[[ "$(/usr/bin/printf '%s\n' "$backend_assignments" | /usr/bin/wc -l)" = 3 ]] \
  || fail "FM backend dispatch differs"
fm_observe_line=$(/usr/bin/grep -n 'gpgx_audio_trace_fm_write(address, data)' "$callgraph_root/sound/sound.c"); fm_observe_line=${fm_observe_line%%:*}
fm_mutate_line=$(/usr/bin/grep -n 'fm_write_impl(cycles, address, data)' "$callgraph_root/sound/sound.c"); fm_mutate_line=${fm_mutate_line%%:*}
psg_observe_line=$(/usr/bin/grep -n 'gpgx_audio_trace_psg_write(data)' "$callgraph_root/sound/psg.c"); psg_observe_line=${psg_observe_line%%:*}
psg_mutate_line=$(/usr/bin/sed -n '226,245{s/^[[:space:]]*//; /psg_update(clocks)/=}' "$callgraph_root/sound/psg.c")
[[ "$fm_observe_line" -lt "$fm_mutate_line" && "$psg_observe_line" -lt "$psg_mutate_line" ]] \
  || fail "observer dispatch no longer precedes chip mutation"
for selector in MAME_YM2612 MAME_ASIC_YM3438 MAME_Enhanced_YM3438 Nuked_YM2612 Nuked_YM3438; do
  /usr/bin/grep -F "case $selector:" "$source_dir/waterbox/gpgx/cinterface/cinterface.c" >/dev/null \
    || fail "Genesis FM selector is absent: $selector"
done
{
  /usr/bin/printf '%s\n' "$fm_calls" "$psg_calls" "$backend_assignments"
  (cd "$callgraph_root" && /usr/bin/grep -nH \
    'gpgx_audio_trace_fm_write\|gpgx_audio_trace_psg_write' sound/sound.c sound/psg.c)
  (cd "$callgraph_root" && /usr/bin/grep -nH \
    'gpgx_audio_trace_enter_cpu\|gpgx_audio_trace_leave_cpu' z80/z80.c m68k/m68kcpu.c)
  (cd "$source_dir/waterbox/gpgx" && /usr/bin/grep -nH \
    'gpgx_audio_trace_reset_begin\|gen_reset(0)\|gpgx_audio_trace_reset_end' cinterface/cinterface.c)
  (cd "$callgraph_root" && /usr/bin/grep -nH 'uint8 zram\[0x2000\]' genesis.c)
} > "$stage/callgraph-proof.txt"
/usr/bin/bash -p "$script_dir/selftest/run.sh" "$source_dir" "$toolchain_dir" "$stage" \
  > "$stage/native-selftest.log"

# Snapshot complete corresponding source before introducing generated build inputs.
/usr/bin/cp -a -- "$source_dir/." "$stage/source-normalized"
for repository in "$stage/source-normalized" \
  "$stage/source-normalized/waterbox/gpgx/Genesis-Plus-GX" \
  "$stage/source-normalized/waterbox/musl"; do
  while IFS= read -r -d '' relative; do
    /usr/bin/sed -i 's/\r$//' "$repository/$relative"
  done < <(/usr/bin/git -C "$repository" grep -Il -z '' -- .)
done
/usr/bin/sed -i 's/\r$//' \
  "$stage/source-normalized/waterbox/gpgx/cinterface/audio_trace.c" \
  "$stage/source-normalized/waterbox/gpgx/cinterface/audio_trace.h"
while IFS= read -r -d '' git_path; do /usr/bin/rm -rf -- "$git_path"; done \
  < <(/usr/bin/find "$stage/source-normalized" -name .git -print0)
/usr/bin/rm -rf -- "$stage/source-normalized/waterbox/sysroot" \
  "$stage/source-normalized/waterbox/emulibc/obj" "$stage/source-normalized/waterbox/gpgx/obj" \
  "$stage/source-normalized/Assets/dll"
/usr/bin/find "$stage/source-normalized" -type d -exec /usr/bin/chmod 0755 '{}' +
/usr/bin/find "$stage/source-normalized" -type f -exec /usr/bin/chmod 0644 '{}' +
/usr/bin/find "$stage/source-normalized" -type f \
  \( -name '*.sh' -o -name configure \) -exec /usr/bin/chmod 0755 '{}' +
: > "$stage/source-bundle.paths.unsorted"
: > "$stage/source-bundle.path-modes.unsorted"
while IFS= read -r -d '' relative; do
  relative=${relative#./}
  [[ -n "$relative" && "$relative" != /* && "$relative" != .. \
    && "$relative" != ../* && "$relative" != */../* && "$relative" != */.. \
    && "$relative" != *[$'\001'-$'\037'$'\177']* ]] || fail "unsafe source bundle path"
  /usr/bin/printf '%s\n' "$relative" >> "$stage/source-bundle.paths.unsorted"
  /usr/bin/printf '%s\t%s\n' "$(/usr/bin/stat -c %a "$stage/source-normalized/$relative")" "$relative" \
    >> "$stage/source-bundle.path-modes.unsorted"
done < <(cd "$stage/source-normalized" && LC_ALL=C /usr/bin/find . -mindepth 1 -print0)
LC_ALL=C /usr/bin/sort "$stage/source-bundle.paths.unsorted" > "$stage/source-bundle.paths"
LC_ALL=C /usr/bin/sort -k2 "$stage/source-bundle.path-modes.unsorted" > "$stage/source-bundle.path-modes"
/usr/bin/rm -- "$stage/source-bundle.paths.unsorted" "$stage/source-bundle.path-modes.unsorted"
(cd "$stage/source-normalized" && LC_ALL=C TZ=UTC /usr/bin/tar \
  --format=posix --sort=name --mtime=@1758367997 --owner=0 --group=0 \
  --numeric-owner --pax-option=delete=atime,delete=ctime --no-recursion \
  --verbatim-files-from --files-from="$stage/source-bundle.paths" \
  -cf "$stage/source-bundle.tar")
"$toolchain_dir/zstd" --ultra -22 --threads=0 --no-progress --force \
  "$stage/source-bundle.tar" -o "$stage/source-bundle.tar.zst"

/usr/bin/rm -rf -- "$source_dir/waterbox/sysroot" "$source_dir/waterbox/emulibc/obj" \
  "$source_dir/waterbox/gpgx/obj"
/usr/bin/cp -a -- "$toolchain_dir/sysroot" "$source_dir/waterbox/sysroot"
build_root=$(/usr/bin/printf '%s' 2f686f6d652f66656f732f7368617265732f73686172652f42697a4861776b | /usr/bin/xxd -r -p)
build_home=${build_root%%/shares/share/BizHawk}
build_shares=$build_home/shares
build_share=$build_shares/share
if ! /usr/bin/env -i /usr/bin/bwrap --die-with-parent --ro-bind / / \
  --dev /dev --proc /proc --tmpfs /home --tmpfs /opt \
  --dir "$build_home" --dir "$build_shares" --dir "$build_share" \
  --bind "$source_dir" "$build_root" --bind "$toolchain_dir/clang" /opt/task6-clang \
  --setenv PATH /opt/task6-clang/usr/bin:/usr/bin:/bin \
  --setenv LD_LIBRARY_PATH /opt/task6-clang/usr/lib/x86_64-linux-gnu:/opt/task6-clang/usr/lib/llvm-16/lib \
  --setenv LC_ALL C --setenv TZ UTC --setenv SOURCE_DATE_EPOCH 1758367997 \
  --setenv MAKEFLAGS -j1 /bin/sh -eu -c \
  "umask 0022; /usr/bin/make -C '$build_root/waterbox/emulibc' -j1; /usr/bin/make -C '$build_root/waterbox/gpgx' -j1" \
  >"$stage/build.log" 2>&1; then
  /usr/bin/tail -200 "$stage/build.log" >&2
  fail "observer core build failed"
fi
/usr/bin/cp -- "$source_dir/waterbox/gpgx/obj/release/gpgx.wbx" "$stage/gpgx.wbx"
"$toolchain_dir/zstd" --stdout --ultra -22 --threads=0 --no-progress --force \
  "$stage/gpgx.wbx" > "$stage/gpgx.wbx.zst"

section_line=$(/usr/bin/readelf -SW "$stage/gpgx.wbx" | /usr/bin/grep ' \.invis ')
[[ "$section_line" = *" 2088d0 "* && "$section_line" = *" WA "* && "$section_line" = *" 32" ]] \
  || fail "observer .invis section layout differs: $section_line"
((0x2088d0 < 4 * 1024 * 1024)) || fail "observer .invis exceeds Waterbox invisible heap"
bad_state=$(/usr/bin/readelf -Ws "$stage/gpgx.wbx" \
  | /usr/bin/awk '$4 == "OBJECT" && $8 ~ /^trace_/ && $7 != "10" { print $8 }')
[[ -z "$bad_state" ]] || fail "observer state escaped .invis: $bad_state"
enabled_symbol=$(/usr/bin/readelf -Ws "$stage/gpgx.wbx" \
  | /usr/bin/awk '$8 == "gpgx_audio_trace_enabled" { print $2, $3, $4, $5, $7 }')
[[ "$enabled_symbol" = "0000036f0035a0a1 1 OBJECT LOCAL 10" ]] \
  || fail "observer enable flag escaped .invis: $enabled_symbol"
events_symbol=$(/usr/bin/readelf -Ws "$stage/gpgx.wbx" \
  | /usr/bin/awk '$8 == "trace_events" { print $2, $3, $4, $5, $7 }')
[[ "$events_symbol" = "0000036f0035bac0 0x200000 OBJECT LOCAL 10" ]] \
  || fail "observer event array layout differs: $events_symbol"
((0x0035bac0 % 32 == 0 && 0x0035bac0 >= 0x00354000 \
  && 0x0035bac0 + 0x200000 <= 0x00354000 + 0x2088d0)) \
  || fail "observer event array is not aligned and contained in .invis"
bad_internal=$(/usr/bin/readelf -Ws "$stage/gpgx.wbx" \
  | /usr/bin/awk '$4 == "FUNC" && $8 ~ /^gpgx_audio_trace_(enter_cpu|leave_cpu|instruction|fm_write|psg_write|reset_begin|reset_end)$/ && $5 != "LOCAL" { print $8 }')
[[ -z "$bad_internal" ]] || fail "observer internal function was exported: $bad_internal"
exports=$(/usr/bin/readelf -Ws "$stage/gpgx.wbx" \
  | /usr/bin/awk '$4 == "FUNC" && $5 == "GLOBAL" && $8 ~ /^gpgx_audio_trace_/ { print $8 }' \
  | LC_ALL=C /usr/bin/sort)
expected_exports='gpgx_audio_trace_abi_version
gpgx_audio_trace_abort_frame
gpgx_audio_trace_begin_frame
gpgx_audio_trace_capacity
gpgx_audio_trace_configure
gpgx_audio_trace_disable
gpgx_audio_trace_drain
gpgx_audio_trace_end_frame
gpgx_audio_trace_event_count
gpgx_audio_trace_event_size'
[[ "$exports" = "$expected_exports" ]] || fail "observer departure exports differ"
/usr/bin/readelf -d "$stage/gpgx.wbx" | /usr/bin/grep -Fx 'There is no dynamic section in this file.' >/dev/null \
  || fail "observer core unexpectedly has a dynamic section"
{
  /usr/bin/printf '%s\n' "$section_line"
  /usr/bin/readelf -Ws "$stage/gpgx.wbx" \
    | /usr/bin/awk '$4 == "OBJECT" && ($8 ~ /^trace_/ || $8 == "gpgx_audio_trace_enabled") { print $2, $3, $7, $8 }'
  /usr/bin/objdump -h "$stage/gpgx.wbx" | /usr/bin/grep ' \.invis '
  /usr/bin/printf '%s\n' "$exports"
  /usr/bin/readelf -d "$stage/gpgx.wbx"
} > "$stage/elf-proof.txt"

raw_sha=$(/usr/bin/sha256sum "$stage/gpgx.wbx"); raw_sha=${raw_sha%% *}
zst_sha=$(/usr/bin/sha256sum "$stage/gpgx.wbx.zst"); zst_sha=${zst_sha%% *}
bundle_sha=$(/usr/bin/sha256sum "$stage/source-bundle.tar.zst"); bundle_sha=${bundle_sha%% *}
bundle_raw_sha=$(/usr/bin/sha256sum "$stage/source-bundle.tar"); bundle_raw_sha=${bundle_raw_sha%% *}
paths_sha=$(/usr/bin/sha256sum "$stage/source-bundle.path-modes"); paths_sha=${paths_sha%% *}
path_list_sha=$(/usr/bin/sha256sum "$stage/source-bundle.paths"); path_list_sha=${path_list_sha%% *}
build_log_sha=$(/usr/bin/sha256sum "$stage/build.log"); build_log_sha=${build_log_sha%% *}
selftest_sha=$(/usr/bin/sha256sum "$stage/native-selftest.log"); selftest_sha=${selftest_sha%% *}
elf_proof_sha=$(/usr/bin/sha256sum "$stage/elf-proof.txt"); elf_proof_sha=${elf_proof_sha%% *}
callgraph_proof_sha=$(/usr/bin/sha256sum "$stage/callgraph-proof.txt"); callgraph_proof_sha=${callgraph_proof_sha%% *}
build_id=$(/usr/bin/readelf -n "$stage/gpgx.wbx" | /usr/bin/sed -n 's/^ *Build ID: //p')
[[ "$raw_sha" = "$(/usr/bin/jq -er '.core.decompressed_sha256' "$script_dir/artifact-lock.json")" \
  && "$zst_sha" = "$(/usr/bin/jq -er '.core.compressed_sha256' "$script_dir/artifact-lock.json")" \
  && "$build_id" = "$(/usr/bin/jq -er '.core.build_id' "$script_dir/artifact-lock.json")" \
  && "$bundle_sha" = "$(/usr/bin/jq -er '.source_bundle.compressed_sha256' "$script_dir/artifact-lock.json")" \
  && "$bundle_raw_sha" = "$(/usr/bin/jq -er '.source_bundle.uncompressed_sha256' "$script_dir/artifact-lock.json")" \
  && "$build_log_sha" = "$(/usr/bin/jq -er '.build_log.sha256' "$script_dir/artifact-lock.json")" \
  && "$selftest_sha" = "$(/usr/bin/jq -er '.native_selftest.log_sha256' "$script_dir/artifact-lock.json")" \
  && "$elf_proof_sha" = "$(/usr/bin/jq -er '.elf_proof.sha256' "$script_dir/artifact-lock.json")" \
  && "$callgraph_proof_sha" = "$(/usr/bin/jq -er '.callgraph_proof.sha256' "$script_dir/artifact-lock.json")" \
  && "$path_list_sha" = "$(/usr/bin/jq -er '.source_bundle.path_manifest_sha256' "$script_dir/artifact-lock.json")" \
  && "$paths_sha" = "$(/usr/bin/jq -er '.source_bundle.path_mode_manifest_sha256' "$script_dir/artifact-lock.json")" ]] \
  || fail "built artifact identity differs from lock: raw=$raw_sha zst=$zst_sha build_id=$build_id bundle=$bundle_sha paths=$paths_sha"

verify_stock "$stock_dir" || fail "snapshotted stock distribution changed"
/usr/bin/cp -- "$source_dir/LICENSE" "$stage/BizHawk-LICENSE"
/usr/bin/cp -- "$source_dir/waterbox/gpgx/Genesis-Plus-GX/LICENSE.txt" "$stage/GPGX-LICENSE.txt"
/usr/bin/cp -- "$source_dir/waterbox/musl/COPYRIGHT" "$stage/musl-COPYRIGHT"
/usr/bin/cp -- "$script_dir/notices/zstd-LICENSE" "$stage/zstd-LICENSE"
/usr/bin/cp -a -- "$toolchain_dir/clang/usr/share/doc" "$stage/llvm-debian-notices"
/usr/bin/cp -- "$adapter_source" "$stage/GpgxAudioObserverAdapter.cs"
/usr/bin/cp -- "$host_source" "$stage/GpgxHost.cs"
/usr/bin/printf '{"schema":"openggf.gpgx-audio-observer-build.v1","installation_id":"bizhawk-2.11-gpgx-audio-observer-v1","core_id":"gpgx-audio-observer-v1","adapter":"REFLECTION","adapter_source_sha256":"%s","host_source_sha256":"%s","bizinvoke_sha256":"8d05389bf0e02be1244bdc7a2adcd93b4cff95acf199fc927987ca699760a1b7","bizhawk_common_sha256":"438a49d6a45d9fcac17016240ae205d1af7a4632865f6f70468b684b82323f33","abi_version":1,"event_size":32,"capacity":65536,"patch_sha256":"%s","build_recipe_sha256":"%s","decompressed_sha256":"%s","compressed_sha256":"%s","build_id":"%s","source_bundle_sha256":"%s","source_bundle_uncompressed_sha256":"%s","path_manifest_sha256":"%s","path_mode_manifest_sha256":"%s","build_log_sha256":"%s","native_selftest_sha256":"%s","elf_proof_sha256":"%s","callgraph_proof_sha256":"%s","verified_input_identity_sha256":"%s"}\n' \
  "$adapter_source_sha" "$host_source_sha" "$actual_patch" "$actual_recipe" "$raw_sha" "$zst_sha" "$build_id" "$bundle_sha" "$bundle_raw_sha" "$path_list_sha" "$paths_sha" "$build_log_sha" "$selftest_sha" "$elf_proof_sha" "$callgraph_proof_sha" "$verified_identity" > "$stage/identity.json"
identity_sha=$(/usr/bin/sha256sum "$stage/identity.json"); identity_sha=${identity_sha%% *}
[[ "$identity_sha" = "$(/usr/bin/jq -er '.identity.sha256' "$script_dir/artifact-lock.json")" ]] \
  || fail "build identity differs from lock: $identity_sha"
/usr/bin/rm -rf -- "$stage/build-source" "$stage/toolchain-input" "$stage/stock-input" \
  "$stage/source-normalized" "$stage/native-selftest"
secure_publish_create_new "$stage" "$output"
stage=
/usr/bin/printf '%s\n' "$output"
