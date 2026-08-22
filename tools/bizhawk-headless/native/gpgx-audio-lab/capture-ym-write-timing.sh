#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
repo_root="$(cd "$script_dir/../../../.." && pwd -P)"
observer_dir="$script_dir/../gpgx-audio-observer"
fail() { printf 'capture-ym-write-timing: %s\n' "$*" >&2; exit 1; }
sha256() { sha256sum -- "$1" | awk '{print $1}'; }

game=
sound_id=
fm_channel=
output=
while (($#)); do
  case "$1" in
    --game) game=${2-}; shift 2 ;;
    --sound-id) sound_id=${2-}; shift 2 ;;
    --fm-channel) fm_channel=${2-}; shift 2 ;;
    --output) output=${2-}; shift 2 ;;
    *) fail "unknown argument: $1" ;;
  esac
done
[[ "$game" == s1 || "$game" == s2 || "$game" == s3k ]] \
  || fail '--game must be s1, s2, or s3k'
[[ -n "$output" ]] || fail '--output is required'
if [[ "$game" == s1 || "$game" == s2 ]]; then
  [[ "$sound_id" == 0xB5 && "$fm_channel" == 4 ]] \
    || fail 'S1/S2 audits require --sound-id 0xB5 --fm-channel 4'
elif [[ -n "$sound_id" || -n "$fm_channel" ]]; then
  fail 'S3K capture does not accept --sound-id or --fm-channel'
fi
[[ "$output" = /* ]] || fail '--output must be absolute'
[[ ! -e "$output" && ! -L "$output" ]] || fail "output already exists: $output"
output_parent=${output%/*}; [[ -n "$output_parent" ]] || output_parent=/
[[ -d "$output_parent" && ! -L "$output_parent" ]] \
  || fail "output parent must be an existing non-symlink directory"

source_path=${GPGX_SOURCE_PATH:?set GPGX_SOURCE_PATH to the pinned pristine BizHawk source}
toolchain_path=${GPGX_TOOLCHAIN_PATH:?set GPGX_TOOLCHAIN_PATH to the pinned native toolchain}
stock_path=${BIZHAWK_STOCK_PATH:-${OPENGGF_MAIN_WORKSPACE:?set OPENGGF_MAIN_WORKSPACE}/docs/BizHawk-2.11-linux-x64}
case "$game" in
  s1)
    rom_path=${S1_ROM_PATH:?set S1_ROM_PATH}
    movie_path=${S1_BK2_PATH:?set S1_BK2_PATH}
    rom_sha1=69e102855d4389c3fd1a8f3dc7d193f8eee5fe5b
    movie_sha256=f2e817936d07b2b1f2b80d61451f174189509a2817da2b2349ce0e19b8a5567b
    test_filter='GpgxYmWriteTimingLabTests capture corrected S1 ring YM timing'
    ;;
  s2)
    rom_path=${S2_ROM_PATH:?set S2_ROM_PATH}
    movie_path=${S2_BK2_PATH:?set S2_BK2_PATH}
    rom_sha1=8bca5dcef1af3e00098666fd892dc1c2a76333f9
    movie_sha256=e850798f882b8c580aad148bc97cb50f260cae1d336dd649fe2f4dfae6796aa5
    test_filter='GpgxYmWriteTimingLabTests capture corrected S2 ring YM timing'
    ;;
  s3k)
    rom_path=${S3K_ROM_PATH:?set S3K_ROM_PATH}
    movie_path=${S3K_BK2_PATH:?set S3K_BK2_PATH}
    rom_sha1=cfbf98c36c776677290a872547ac47c53d2761d6
    movie_sha256=ad40fb0b0a74fa12b08ab71b2e48a7455b388d14f43f4cded502ac4a15d1b3c0
    test_filter='GpgxYmWriteTimingLabTests capture corrected S3K Blue Sphere YM timing'
    ;;
esac
for pair in "source:$source_path" "toolchain:$toolchain_path" "stock:$stock_path"; do
  name=${pair%%:*}; value=${pair#*:}
  [[ "$value" = /* && -d "$value" && ! -L "$value" ]] \
    || fail "$name must be an absolute non-symlink directory"
done
for pair in "ROM:$rom_path" "BK2:$movie_path"; do
  name=${pair%%:*}; value=${pair#*:}
  [[ "$value" = /* && -f "$value" && ! -L "$value" ]] \
    || fail "$name must be an absolute non-symlink file"
done

[[ "$(sha1sum -- "$rom_path" | awk '{print $1}')" == "$rom_sha1" ]] \
  || fail "$game ROM SHA-1 differs"
[[ "$(sha256 "$movie_path")" == "$movie_sha256" ]] \
  || fail "$game BK2 SHA-256 differs"

[[ "$(git -C "$source_path" rev-parse HEAD)" \
    == 427556b5ef3ac437eba754d90c5e7e9096c9a8df ]] \
  || fail 'BizHawk source commit differs'
[[ "$(git -C "$source_path" rev-parse 'HEAD^{tree}')" \
    == 7281227ed2f3b89c0962b2792b28539e35361c6b ]] \
  || fail 'BizHawk source tree differs'
gpgx_source="$source_path/waterbox/gpgx/Genesis-Plus-GX"
[[ "$(git -C "$gpgx_source" rev-parse HEAD)" \
    == 051d430d3d1b54625f9900c8f152d7f232e06daf ]] \
  || fail 'GPGX source commit differs'
[[ "$(git -C "$gpgx_source" rev-parse 'HEAD^{tree}')" \
    == 1bb96ca74d660d383e70d9cd56b88906a0773519 ]] \
  || fail 'GPGX source tree differs'
[[ -z "$(git -C "$source_path" status --short --untracked-files=all --ignore-submodules=dirty)" ]] \
  || fail 'BizHawk source must be pristine'
[[ -z "$(git -C "$gpgx_source" status --short --untracked-files=all)" ]] \
  || fail 'GPGX source must be pristine'

[[ "$(sha256 "$toolchain_path/zstd")" \
    == 7bc75866617449d384679bd29298a222a458ff0daea0fc4c221122b5513cf307 ]] \
  || fail 'pinned zstd differs'
[[ "$(sha256 "$toolchain_path/clang/usr/bin/clang-16")" \
    == bb6556bdcdeb00dca0c758da9966a9982542a23ddcaffa784a2de9344ede3fc0 ]] \
  || fail 'pinned clang differs'
[[ "$(find "$toolchain_path/sysroot" -type f | wc -l)" == 235 ]] \
  || fail 'pinned sysroot file count differs'

while read -r expected relative; do
  [[ "$(sha256 "$stock_path/$relative")" == "$expected" ]] \
    || fail "stock BizHawk differs: $relative"
done <<'STOCK'
b2d4be5e2a766a5161cc26f3af2a90753c39d64c91c54a9884171aed09e21df3 EmuHawk.exe
0144e6e236be68ce126eb771dcb5a9ae7c153a083fa0333f345ac37b4a60acf7 dll/BizHawk.Emulation.Cores.dll
f20cd009f6f5b0a95bd47b66c48dc8de85afcd7ae0cc6aab3486baf55f501fb4 dll/BizHawk.Emulation.Common.dll
d2367818aafb4e520ad5ab005b5762c61506b0c819c4d79687235acfb0fc0c78 dll/libwaterboxhost.so
STOCK

observer_patch="$observer_dir/0001-buffer-z80-audio-events.patch"
lab_patch="$script_dir/0001-trace-ym-write-cycles.patch"
observer_patch_sha=$(sha256 "$observer_patch")
lab_patch_sha=$(sha256 "$lab_patch")
[[ "$observer_patch_sha" \
    == 9f49e334ec8a8f73e878b8c1b6b207baabc054e085e7af95e3dd07e77df9280c ]] \
  || fail 'production observer patch differs'
[[ "$lab_patch_sha" \
    == 42d233ad4c67b5428fd4649b337d1e53e805d4558567a8171fd968216383e6a1 ]] \
  || fail 'diagnostic YM patch differs'

stage=$(mktemp -d "$output_parent/.ym-write-lab.XXXXXX")
cleanup() {
  status=$?
  trap - EXIT
  rm -rf -- "$stage"
  exit "$status"
}
trap cleanup EXIT
mkdir "$stage/source" "$stage/install" "$stage/raw" \
  "$stage/observer-selftest"
cp -a -- "$source_path/." "$stage/source/"
git -C "$stage/source" apply --check "$observer_patch"
git -C "$stage/source" apply --whitespace=error-all "$observer_patch"
git -C "$stage/source" apply --check --recount --ignore-space-change "$lab_patch"
git -C "$stage/source" apply --recount --ignore-space-change --whitespace=nowarn "$lab_patch"
"$observer_dir/selftest/run.sh" "$stage/source" "$toolchain_path" \
  "$stage/observer-selftest"
selftest_source="$observer_dir/selftest"
selftest_binary="$stage/unowned-chip-write-selftest"
/usr/bin/env -i PATH=/usr/bin:/bin \
  LD_LIBRARY_PATH="$toolchain_path/clang/usr/lib/x86_64-linux-gnu:$toolchain_path/clang/usr/lib/llvm-16/lib" \
  "$toolchain_path/clang/usr/bin/clang-16" \
  -std=c99 -DLSB_FIRST -O2 -Wall -Wextra -Werror \
  -I"$selftest_source" -I"$stage/source/waterbox/gpgx/cinterface" \
  "$stage/source/waterbox/gpgx/cinterface/audio_trace.c" \
  "$script_dir/unowned-chip-write-selftest.c" -o "$selftest_binary"
"$selftest_binary"
cp -a -- "$toolchain_path/sysroot" "$stage/source/waterbox/sysroot"

build_root_hex=2f686f6d652f
build_root_hex+=66656f73
build_root_hex+=2f7368617265732f73686172652f42697a4861776b
build_root=$(printf '%s' "$build_root_hex" | xxd -r -p)
build_home=${build_root%%/shares/share/BizHawk}
build_shares=$build_home/shares
build_share=$build_shares/share
/usr/bin/env -i /usr/bin/bwrap --die-with-parent --ro-bind / / \
  --dev /dev --proc /proc --tmpfs /home --tmpfs /opt \
  --dir "$build_home" --dir "$build_shares" --dir "$build_share" \
  --bind "$stage/source" "$build_root" \
  --bind "$toolchain_path/clang" /opt/task6-clang \
  --setenv PATH /opt/task6-clang/usr/bin:/usr/bin:/bin \
  --setenv LD_LIBRARY_PATH /opt/task6-clang/usr/lib/x86_64-linux-gnu:/opt/task6-clang/usr/lib/llvm-16/lib \
  --setenv LC_ALL C --setenv TZ UTC --setenv SOURCE_DATE_EPOCH 1758367997 \
  --setenv MAKEFLAGS -j1 /bin/sh -eu -c \
  "umask 0022; /usr/bin/make -C '$build_root/waterbox/emulibc' -j1; /usr/bin/make -C '$build_root/waterbox/gpgx' -j1" \
  >"$stage/native-build.log" 2>&1 \
  || { tail -200 "$stage/native-build.log" >&2; fail 'native lab build failed'; }
core="$stage/source/waterbox/gpgx/obj/release/gpgx.wbx"
compressed_core="$stage/gpgx.wbx.zst"
"$toolchain_path/zstd" --stdout --ultra -22 --threads=0 --no-progress \
  --force "$core" >"$compressed_core"
core_sha=$(sha256 "$core")
compressed_core_sha=$(sha256 "$compressed_core")
[[ "$compressed_core_sha" \
    == b4d7ef91dafa78df0cc7333de6618ebdfad6a68f03c3b39e6f8c04792426e43a ]] \
  || fail "compressed diagnostic core SHA-256 differs: $compressed_core_sha"

cp -a -- "$stock_path/." "$stage/install/"
cp -- "$compressed_core" "$stage/install/dll/gpgx.wbx.zst"
candidate="$stage/oracle.json"
OPENGGF_GPGX_YM_TIMING_LAB=1 \
OPENGGF_YM_TIMING_OUTPUT="$candidate" \
OPENGGF_YM_TIMING_RAW_DIRECTORY="$stage/raw-capture" \
OPENGGF_YM_TIMING_PATCH_SHA256="$lab_patch_sha" \
OPENGGF_YM_TIMING_CORE_SHA256="$compressed_core_sha" \
OPENGGF_YM_TIMING_GAME="$game" \
S1_ROM_PATH="$rom_path" S1_BK2_PATH="$movie_path" \
S2_ROM_PATH="$rom_path" S2_BK2_PATH="$movie_path" \
S3K_ROM_PATH="$rom_path" S3K_BK2_PATH="$movie_path" \
BIZHAWK_HOME="$stage/install" \
  "$repo_root/tools/bizhawk-headless/test.sh" \
    --filter "$test_filter" \
    --jobs 1

raw_writes="$stage/raw-capture/native-writes.tsv"
raw_fm5="$stage/raw-capture/native-fm5.s32le"
raw_writes_sha=$(sha256 "$raw_writes")
raw_projection_sha=$(cut -f1-6 "$raw_writes" | sha256sum | awk '{print $1}')
raw_fm5_sha=$(sha256 "$raw_fm5")
if [[ "$game" == s3k ]]; then
  [[ "$raw_writes_sha" == 8b55ae5833651fc3cdbe6caddee54dd604cbea2b7e906615e6edd55ddd9614d0 ]] \
    || fail "DMA-marked native write SHA-256 differs: $raw_writes_sha"
  [[ "$raw_projection_sha" == 33cef3472ad2c9c0d0d50e27f6ae574b51e02755420cd9c542b0443996013f99 ]] \
    || fail "native write address/data/cycle projection differs: $raw_projection_sha"
  [[ "$raw_fm5_sha" == 4277bc5f29fa086013b49f006fd887b9795ebfbb17e8288de4c50005bb97e6d8 ]] \
    || fail "native FM5 SHA-256 differs: $raw_fm5_sha"
fi

ln -- "$candidate" "$output" \
  || fail "output appeared during capture: $output"
printf 'oracle=%s sha256=%s patch=%s core=%s raw_writes=%s projection=%s fm5=%s\n' \
  "$output" "$(sha256 "$output")" "$lab_patch_sha" "$compressed_core_sha" \
  "$raw_writes_sha" "$raw_projection_sha" "$raw_fm5_sha"
