#!/usr/bin/bash -p
set -euo pipefail

script_dir=${BASH_SOURCE[0]%/*}; [[ "$script_dir" != "${BASH_SOURCE[0]}" ]] || script_dir=.
script_dir=$(cd -P -- "$script_dir" && pwd)
source "$script_dir/secure-runtime.sh"
fail() { printf 'reproduce-stock-core: %s\n' "$*" >&2; exit 1; }
source_dir=
toolchain_dir=
stock_dir=
output=
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
for pair in "source:$source_dir" "toolchain:$toolchain_dir"; do
  name=${pair%%:*}; value=${pair#*:}
  [[ "$value" = /* && -d "$value" && ! -L "$value" ]] \
    || fail "$name must be an absolute, non-symlink directory"
done
[[ "$stock_dir" = /* && -d "$stock_dir" && ! -L "$stock_dir" ]] || fail "stock must be an absolute, non-symlink directory"

parent=${output%/*}; [[ -n "$parent" ]] || parent=/
stage=$(mktemp -d "$parent/.gpgx-reproduction-staging.XXXXXX")
cleanup() { if [[ -n "${stage-}" && -d "$stage" ]]; then rm -rf -- "$stage"; fi; }
trap cleanup EXIT
mkdir "$stage/build-source" "$stage/toolchain-input" "$stage/stock-input"
mkdir "$stage/stock-input/dll"
cp -a "$source_dir/." "$stage/build-source/"
cp -a "$toolchain_dir/." "$stage/toolchain-input/"
cp -- "$stock_dir/EmuHawk.exe" "$stage/stock-input/"
cp -- "$stock_dir/dll/BizHawk.Emulation.Cores.dll" \
  "$stock_dir/dll/BizHawk.Emulation.Common.dll" \
  "$stock_dir/dll/libwaterboxhost.so" "$stock_dir/dll/gpgx.wbx.zst" "$stage/stock-input/dll/"
source_dir=$stage/build-source
toolchain_dir=$stage/toolchain-input
stock_dir=$stage/stock-input

stock_compressed="$stock_dir/dll/gpgx.wbx.zst"
[[ -f "$stock_compressed" && ! -L "$stock_compressed" ]] || fail "stock compressed core is missing"
while read -r expected file; do
  printf '%s  %s\n' "$expected" "$stock_dir/$file" | sha256sum -c - >/dev/null || fail "stock artifact differs: $file"
done <<'LOCKED_STOCK'
b2d4be5e2a766a5161cc26f3af2a90753c39d64c91c54a9884171aed09e21df3 EmuHawk.exe
0144e6e236be68ce126eb771dcb5a9ae7c153a083fa0333f345ac37b4a60acf7 dll/BizHawk.Emulation.Cores.dll
f20cd009f6f5b0a95bd47b66c48dc8de85afcd7ae0cc6aab3486baf55f501fb4 dll/BizHawk.Emulation.Common.dll
d2367818aafb4e520ad5ab005b5762c61506b0c819c4d79687235acfb0fc0c78 dll/libwaterboxhost.so
c4231296ec5ba59b431df22b68e234ae7bfbbfc87b6e72fa471234ac1b220d12 dll/gpgx.wbx.zst
LOCKED_STOCK
printf '%s  %s\n' e1a35b81e8ba6de2eb11ff5cf82a5521b7b4fa719f425f027c2b0496e8ef62ca /usr/bin/readelf | sha256sum -c - >/dev/null || fail "host readelf differs"
printf '%s  %s\n' eabbccb0f7f755b96d30834026a9b5d941c606400d097d87c1ff16622edaf68c /usr/bin/bwrap | sha256sum -c - >/dev/null || fail "host bwrap differs"

recipe_sha=$(secure_verify_recipe "$script_dir")
verified_identity=$(/usr/bin/bash -p "$script_dir/verify-inputs.sh" --source "$source_dir" --toolchain "$toolchain_dir")
rm -rf -- "$stage/build-source/waterbox/sysroot" \
  "$stage/build-source/waterbox/emulibc/obj" "$stage/build-source/waterbox/gpgx/obj"
cp -a "$toolchain_dir/sysroot" "$stage/build-source/waterbox/sysroot"

build_root_hex=2f686f6d652f
build_root_hex+=66656f73
build_root_hex+=2f7368617265732f73686172652f42697a4861776b
build_root=$(printf '%s' "$build_root_hex" | xxd -r -p)
build_parent=$(dirname "$build_root")
build_grandparent=$(dirname "$build_parent")
build_great_grandparent=$(dirname "$build_grandparent")
if ! env -i /usr/bin/bwrap --die-with-parent --ro-bind / / \
  --dev /dev --proc /proc --tmpfs /home --tmpfs /opt \
  --dir "$build_great_grandparent" --dir "$build_grandparent" --dir "$build_parent" \
  --bind "$stage/build-source" "$build_root" \
  --bind "$toolchain_dir/clang" /opt/task6-clang \
  --setenv PATH /opt/task6-clang/usr/bin:/usr/bin:/bin \
  --setenv LD_LIBRARY_PATH /opt/task6-clang/usr/lib/x86_64-linux-gnu:/opt/task6-clang/usr/lib/llvm-16/lib \
  --setenv LC_ALL C --setenv TZ UTC --setenv SOURCE_DATE_EPOCH 1758367997 --setenv MAKEFLAGS -j1 \
  /bin/sh -eu -c "umask 0022; /usr/bin/make -C '$build_root/waterbox/emulibc' -j1; /usr/bin/make -C '$build_root/waterbox/gpgx' -j1" >"$stage/build.log" 2>&1; then
  tail -200 "$stage/build.log" >&2
  fail "stock core build failed"
fi

generated="$stage/build-source/waterbox/gpgx/obj/release/gpgx.wbx"
[[ -f "$generated" ]] || fail "build did not produce gpgx.wbx"
cp "$generated" "$stage/gpgx.wbx"
"$toolchain_dir/zstd" --stdout --ultra -22 --threads=0 "$stage/gpgx.wbx" > "$stage/gpgx.wbx.zst"
"$toolchain_dir/zstd" -d --stdout "$stock_compressed" > "$stage/stock-gpgx.wbx"

[[ $(stat -c %s "$stage/gpgx.wbx") = 39558192 ]] || fail "wrong decompressed size"
printf '%s  %s\n' b4cc6dabc069a6f1b87790212d80f665d216e603aa4990955cc816d5bf98d218 "$stage/gpgx.wbx" | sha256sum -c - >/dev/null || fail "wrong decompressed hash"
[[ $(stat -c %s "$stage/gpgx.wbx.zst") = 400161 ]] || fail "wrong compressed size"
printf '%s  %s\n' c4231296ec5ba59b431df22b68e234ae7bfbbfc87b6e72fa471234ac1b220d12 "$stage/gpgx.wbx.zst" | sha256sum -c - >/dev/null || fail "wrong compressed hash"
build_id=$(/usr/bin/readelf -n "$stage/gpgx.wbx" | sed -n 's/^ *Build ID: //p')
[[ "$build_id" = 7696adca7ad14b79 ]] || fail "wrong BuildID: $build_id"
cmp -s "$stage/gpgx.wbx" "$stage/stock-gpgx.wbx" || fail "decompressed core does not byte-match stock"
cmp -s "$stage/gpgx.wbx.zst" "$stock_compressed" || fail "compressed core does not byte-match stock"
rm -- "$stage/stock-gpgx.wbx"
rm -rf -- "$stage/build-source" "$stage/toolchain-input" "$stage/stock-input" "$stage/build.log"

printf '{"schema":"openggf.gpgx-stock-reproduction.v1","bizhawk_commit":"427556b5ef3ac437eba754d90c5e7e9096c9a8df","gpgx_commit":"051d430d3d1b54625f9900c8f152d7f232e06daf","musl_commit":"2063abc4e16c84218757b1db10d3cdf9f36ef3f8","build_recipe_sha256":"%s","verified_input_identity_sha256":"%s","complete_toolchain_tree_sha256":"9caa5c02dcd2d9c01e5d0196956787a0f31760195c6544a2ceafcb771f469521","sysroot_tree_sha256":"fc06187ae45bcedeea4f76f33868ccb05a8c80831d5dce19adbd5eee6e6e06e1","decompressed_size":39558192,"decompressed_sha256":"b4cc6dabc069a6f1b87790212d80f665d216e603aa4990955cc816d5bf98d218","build_id":"7696adca7ad14b79","compressed_size":400161,"compressed_sha256":"c4231296ec5ba59b431df22b68e234ae7bfbbfc87b6e72fa471234ac1b220d12","stock_cmp":true}\n' "$recipe_sha" "$verified_identity" > "$stage/identity.json"
secure_publish_create_new "$stage" "$output"
stage=
printf '%s\n' "$output"
