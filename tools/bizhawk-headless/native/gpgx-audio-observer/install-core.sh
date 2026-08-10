#!/usr/bin/bash -p
set -euo pipefail

script_dir=${BASH_SOURCE[0]%/*}; [[ "$script_dir" != "${BASH_SOURCE[0]}" ]] || script_dir=.
script_dir=$(cd -P -- "$script_dir" && pwd)
source "$script_dir/secure-runtime.sh"
fail() { /usr/bin/printf 'install-core: %s\n' "$*" >&2; exit 1; }
validate_plain_tree() {
  local root=$1 label=$2 bad
  [[ -d "$root" && ! -L "$root" ]] || { /usr/bin/printf 'install-core: %s root is not a real directory\n' "$label" >&2; return 1; }
  bad=$(/usr/bin/find -P "$root" -mindepth 1 \
    \( -type l -o \( ! -type d ! -type f \) -o \( -type f -links +1 \) \) -print -quit)
  [[ -z "$bad" ]] || { /usr/bin/printf 'install-core: %s tree contains a link or special entry: %s\n' "$label" "$bad" >&2; return 1; }
}
validate_notice_tree() {
  validate_plain_tree "$1" notices
}
notice_tree_hash() {
  (
    cd "$1"
    /usr/bin/find . -type f -printf '%P\n' | LC_ALL=C /usr/bin/sort | while IFS= read -r path; do
      /usr/bin/printf 'f\t%s\t%s\t%s\n' "$(/usr/bin/stat -c %a "$path")" \
        "$(/usr/bin/sha256sum "$path" | /usr/bin/cut -d' ' -f1)" "$path"
    done | /usr/bin/sha256sum | /usr/bin/cut -d' ' -f1
  )
}
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
validate_stock() { validate_plain_tree "$1" stock && verify_stock "$1"; }
build= stock= output=
while (($#)); do
  case "$1" in
    --build) build=${2-}; shift 2 ;;
    --stock) stock=${2-}; shift 2 ;;
    --output) output=${2-}; shift 2 ;;
    *) fail "unknown argument: $1" ;;
  esac
done
secure_require_absent_output "$output"
secure_verify_recipe "$script_dir" >/dev/null
artifact_lock_sha=$(/usr/bin/sha256sum "$script_dir/artifact-lock.json"); artifact_lock_sha=${artifact_lock_sha%% *}
readme_sha=$(/usr/bin/sha256sum "$script_dir/README.md"); readme_sha=${readme_sha%% *}
repo_root=$(cd -P -- "$script_dir/../../../.." && pwd)
output_parent=${output%/*}; output_name=${output##*/}
[[ -n "$output_name" && "$output_name" != . && "$output_name" != .. ]] \
  || fail "output has an unsafe final component"
output_parent=$(cd -P -- "$output_parent" && pwd) \
  || fail "output parent must already exist"
output=$output_parent/$output_name
case "$output" in
  "$repo_root"/target/audio-parity/native/*|"$repo_root"/tools/bizhawk-headless/.scratch/*) ;;
  *) fail "output must be beneath an ignored audio-parity target or harness scratch root" ;;
esac
for pair in "build:$build" "stock:$stock"; do
  name=${pair%%:*}; value=${pair#*:}
  [[ "$value" = /* && -d "$value" && ! -L "$value" ]] \
    || fail "$name must be an absolute, non-symlink directory"
done
for file in gpgx.wbx gpgx.wbx.zst source-bundle.tar source-bundle.tar.zst source-bundle.paths \
  source-bundle.path-modes identity.json build.log BizHawk-LICENSE GPGX-LICENSE.txt \
  musl-COPYRIGHT zstd-LICENSE native-selftest.log elf-proof.txt callgraph-proof.txt \
  GpgxAudioObserverAdapter.cs GpgxHost.cs; do
  [[ -e "$build/$file" || -L "$build/$file" ]] || fail "build output missing $file"
  [[ -f "$build/$file" && ! -L "$build/$file" \
    && "$(/usr/bin/stat -c %h "$build/$file")" = 1 ]] || fail "build output is not a private regular file: $file"
done
raw_sha=$(/usr/bin/sha256sum "$build/gpgx.wbx"); raw_sha=${raw_sha%% *}
zst_sha=$(/usr/bin/sha256sum "$build/gpgx.wbx.zst"); zst_sha=${zst_sha%% *}
bundle_sha=$(/usr/bin/sha256sum "$build/source-bundle.tar.zst"); bundle_sha=${bundle_sha%% *}
bundle_raw_sha=$(/usr/bin/sha256sum "$build/source-bundle.tar"); bundle_raw_sha=${bundle_raw_sha%% *}
identity_sha=$(/usr/bin/sha256sum "$build/identity.json"); identity_sha=${identity_sha%% *}
[[ "$raw_sha" = "$(/usr/bin/jq -er '.core.decompressed_sha256' "$script_dir/artifact-lock.json")" \
  && "$zst_sha" = "$(/usr/bin/jq -er '.core.compressed_sha256' "$script_dir/artifact-lock.json")" \
  && "$bundle_sha" = "$(/usr/bin/jq -er '.source_bundle.compressed_sha256' "$script_dir/artifact-lock.json")" ]] \
  || fail "build output differs from artifact lock"
[[ "$bundle_raw_sha" = "$(/usr/bin/jq -er '.source_bundle.uncompressed_sha256' "$script_dir/artifact-lock.json")" ]] \
  || fail "uncompressed source bundle differs from artifact lock"
[[ "$identity_sha" = "$(/usr/bin/jq -er '.identity.sha256' "$script_dir/artifact-lock.json")" ]] \
  || fail "whole build identity differs from artifact lock"
stock_core=$stock/dll/gpgx.wbx.zst
validate_stock "$stock" || fail "caller stock distribution differs before install"
caller_stock=$stock
stage=
cleanup() {
  status=$?; trap - EXIT; stock_status=0
  validate_stock "$caller_stock" || stock_status=$?
  [[ -z "${stage-}" || ! -d "$stage" ]] || /usr/bin/rm -rf -- "$stage"
  if ((stock_status)); then /usr/bin/printf 'install-core: caller stock changed during install\n' >&2; exit 1; fi
  exit "$status"
}
trap cleanup EXIT
for tuple in \
  "source-bundle.paths:source_bundle.path_manifest_sha256" \
  "source-bundle.path-modes:source_bundle.path_mode_manifest_sha256" \
  "build.log:build_log.sha256" "native-selftest.log:native_selftest.log_sha256" \
  "elf-proof.txt:elf_proof.sha256" \
  "callgraph-proof.txt:callgraph_proof.sha256" \
  "GpgxAudioObserverAdapter.cs:managed_reflection.adapter_source_sha256" \
  "GpgxHost.cs:managed_reflection.host_source_sha256" \
  "BizHawk-LICENSE:notices.bizhawk_license_sha256" \
  "GPGX-LICENSE.txt:notices.gpgx_license_sha256" "musl-COPYRIGHT:notices.musl_copyright_sha256" \
  "zstd-LICENSE:notices.zstd_license_sha256"; do
  file=${tuple%%:*}; key=${tuple#*:}; actual=$(/usr/bin/sha256sum "$build/$file"); actual=${actual%% *}
  [[ "$actual" = "$(/usr/bin/jq -er ".$key" "$script_dir/artifact-lock.json")" ]] \
    || fail "build evidence differs: $file"
done
validate_notice_tree "$build/llvm-debian-notices" || fail "LLVM/Debian notice tree is unsafe"
notices_tree=$(notice_tree_hash "$build/llvm-debian-notices")
[[ "$notices_tree" = "$(/usr/bin/jq -er '.notices.llvm_debian_notices_tree_sha256' "$script_dir/artifact-lock.json")" ]] \
  || fail "LLVM/Debian notice tree differs"
for tuple in patch_sha256:native_patch.sha256 build_recipe_sha256:build_recipe.sha256 \
  decompressed_sha256:core.decompressed_sha256 compressed_sha256:core.compressed_sha256 \
  build_id:core.build_id source_bundle_sha256:source_bundle.compressed_sha256 \
  source_bundle_uncompressed_sha256:source_bundle.uncompressed_sha256 \
  path_manifest_sha256:source_bundle.path_manifest_sha256 \
  path_mode_manifest_sha256:source_bundle.path_mode_manifest_sha256 build_log_sha256:build_log.sha256 \
  native_selftest_sha256:native_selftest.log_sha256 elf_proof_sha256:elf_proof.sha256 \
  callgraph_proof_sha256:callgraph_proof.sha256; do
  key=${tuple%%:*}; lock_key=${tuple#*:}
  [[ "$(/usr/bin/jq -er ".$key" "$build/identity.json")" \
    = "$(/usr/bin/jq -er ".$lock_key" "$script_dir/artifact-lock.json")" ]] \
    || fail "identity differs: $key"
done
for tuple in 0001-buffer-z80-audio-events.patch:native_patch.sha256 \
  task7-build-recipe.json:build_recipe.sha256; do
  file=${tuple%%:*}; lock_key=${tuple#*:}; actual=$(/usr/bin/sha256sum "$script_dir/$file"); actual=${actual%% *}
  [[ "$actual" = "$(/usr/bin/jq -er ".$lock_key" "$script_dir/artifact-lock.json")" ]] \
    || fail "installed recipe input differs: $file"
done

parent=${output%/*}; [[ -n "$parent" ]] || parent=/
stage=$(/usr/bin/mktemp -d "$parent/.gpgx-observer-install-staging.XXXXXX")
/usr/bin/cp -a -- "$stock/." "$stage/"
validate_plain_tree "$stage" staged-stock || fail "staged stock tree is unsafe"
/usr/bin/rm -- "$stage/dll/gpgx.wbx.zst"
/usr/bin/cp -- "$build/gpgx.wbx.zst" "$stage/dll/gpgx.wbx.zst"
/usr/bin/mkdir "$stage/gpgx-audio-observer-source"
/usr/bin/cp -- "$build/gpgx.wbx" "$build/source-bundle.tar" "$build/source-bundle.tar.zst" \
  "$build/source-bundle.paths" "$build/source-bundle.path-modes" "$build/identity.json" \
  "$build/build.log" "$build/BizHawk-LICENSE" "$build/GPGX-LICENSE.txt" \
  "$build/musl-COPYRIGHT" "$build/zstd-LICENSE" "$build/native-selftest.log" \
  "$build/elf-proof.txt" \
  "$build/callgraph-proof.txt" \
  "$build/GpgxAudioObserverAdapter.cs" "$build/GpgxHost.cs" \
  "$stage/gpgx-audio-observer-source/"
/usr/bin/cp -a -- "$build/llvm-debian-notices" "$stage/gpgx-audio-observer-source/"
validate_notice_tree "$stage/gpgx-audio-observer-source/llvm-debian-notices" \
  || fail "staged LLVM/Debian notice tree is unsafe"
/usr/bin/cp -- "$script_dir/0001-buffer-z80-audio-events.patch" \
  "$script_dir/artifact-lock.json" "$script_dir/task7-build-recipe.json" \
  "$script_dir/build-core.sh" "$script_dir/install-core.sh" "$script_dir/secure-runtime.sh" \
  "$script_dir/verify-inputs.sh" "$script_dir/source-lock.json" "$script_dir/toolchain-lock.json" \
  "$script_dir/build-recipe.json" "$script_dir/fetch-source.sh" \
  "$script_dir/prepare-toolchain.sh" "$script_dir/prepare-managed-inputs.sh" \
  "$script_dir/reproduce-stock-core.sh" "$script_dir/reproduce-stock-managed.sh" \
  "$script_dir/reproduce-stock-pair.sh" "$script_dir/managed-nuget-manifest.json" \
  "$script_dir/managed-toolchain-lock.json" "$script_dir/README.md" \
  "$stage/gpgx-audio-observer-source/"
/usr/bin/cp -a -- "$script_dir/selftest" "$stage/gpgx-audio-observer-source/"
/usr/bin/mkdir "$stage/gpgx-audio-observer-source/notices"
/usr/bin/cp -- "$script_dir/notices/zstd-LICENSE" "$stage/gpgx-audio-observer-source/notices/"
[[ ! -L "$stage/dll/gpgx.wbx.zst" && ! -L "$stage/gpgx-audio-observer-source/source-bundle.tar.zst" ]] \
  || fail "installation contains linked primary artifacts"

# Everything below is private staged state. Revalidate the complete publication from
# these bytes so concurrent mutations of caller-owned stock, build, or recipe inputs
# cannot change the content-addressed installation that is atomically published.
installed_source=$stage/gpgx-audio-observer-source
validate_plain_tree "$stage" staged-install || fail "staged installation tree is unsafe"
while read -r expected relative; do
  /usr/bin/printf '%s  %s\n' "$expected" "$stage/$relative" | /usr/bin/sha256sum -c - >/dev/null \
    || fail "staged stock artifact differs: $relative"
done <<'LOCKED_INSTALLED_STOCK'
b2d4be5e2a766a5161cc26f3af2a90753c39d64c91c54a9884171aed09e21df3 EmuHawk.exe
0144e6e236be68ce126eb771dcb5a9ae7c153a083fa0333f345ac37b4a60acf7 dll/BizHawk.Emulation.Cores.dll
f20cd009f6f5b0a95bd47b66c48dc8de85afcd7ae0cc6aab3486baf55f501fb4 dll/BizHawk.Emulation.Common.dll
8d05389bf0e02be1244bdc7a2adcd93b4cff95acf199fc927987ca699760a1b7 dll/BizHawk.BizInvoke.dll
438a49d6a45d9fcac17016240ae205d1af7a4632865f6f70468b684b82323f33 dll/BizHawk.Common.dll
d2367818aafb4e520ad5ab005b5762c61506b0c819c4d79687235acfb0fc0c78 dll/libwaterboxhost.so
LOCKED_INSTALLED_STOCK
staged_lock_sha=$(/usr/bin/sha256sum "$installed_source/artifact-lock.json"); staged_lock_sha=${staged_lock_sha%% *}
[[ "$staged_lock_sha" = "$artifact_lock_sha" ]] || fail "staged artifact lock differs"
staged_readme_sha=$(/usr/bin/sha256sum "$installed_source/README.md"); staged_readme_sha=${staged_readme_sha%% *}
[[ "$staged_readme_sha" = "$readme_sha" ]] || fail "staged README differs"
for tuple in \
  "gpgx.wbx:core.decompressed_sha256" \
  "source-bundle.tar:source_bundle.uncompressed_sha256" \
  "source-bundle.tar.zst:source_bundle.compressed_sha256" \
  "source-bundle.paths:source_bundle.path_manifest_sha256" \
  "source-bundle.path-modes:source_bundle.path_mode_manifest_sha256" \
  "identity.json:identity.sha256" "build.log:build_log.sha256" \
  "native-selftest.log:native_selftest.log_sha256" "elf-proof.txt:elf_proof.sha256" \
  "callgraph-proof.txt:callgraph_proof.sha256" \
  "GpgxAudioObserverAdapter.cs:managed_reflection.adapter_source_sha256" \
  "GpgxHost.cs:managed_reflection.host_source_sha256" \
  "BizHawk-LICENSE:notices.bizhawk_license_sha256" \
  "GPGX-LICENSE.txt:notices.gpgx_license_sha256" \
  "musl-COPYRIGHT:notices.musl_copyright_sha256" \
  "zstd-LICENSE:notices.zstd_license_sha256"; do
  file=${tuple%%:*}; key=${tuple#*:}; actual=$(/usr/bin/sha256sum "$installed_source/$file"); actual=${actual%% *}
  [[ "$actual" = "$(/usr/bin/jq -er ".$key" "$installed_source/artifact-lock.json")" ]] \
    || fail "staged build evidence differs: $file"
done
installed_core_sha=$(/usr/bin/sha256sum "$stage/dll/gpgx.wbx.zst"); installed_core_sha=${installed_core_sha%% *}
[[ "$installed_core_sha" = "$(/usr/bin/jq -er '.core.compressed_sha256' "$installed_source/artifact-lock.json")" ]] \
  || fail "staged installed core differs"
[[ "$(notice_tree_hash "$installed_source/llvm-debian-notices")" \
  = "$(/usr/bin/jq -er '.notices.llvm_debian_notices_tree_sha256' "$installed_source/artifact-lock.json")" ]] \
  || fail "staged LLVM/Debian notice tree differs"
staged_recipe_sha=$(/usr/bin/sha256sum "$installed_source/task7-build-recipe.json"); staged_recipe_sha=${staged_recipe_sha%% *}
[[ "$staged_recipe_sha" = "$(/usr/bin/jq -er '.build_recipe.sha256' "$installed_source/artifact-lock.json")" ]] \
  || fail "staged Task 7 recipe differs"
while IFS=$'\t' read -r relative expected; do
  [[ "$relative" != /* && "$relative" != *'..'* \
    && -f "$installed_source/$relative" && ! -L "$installed_source/$relative" ]] \
    || fail "unsafe or missing staged Task 7 recipe input: $relative"
  observed=$(/usr/bin/sha256sum "$installed_source/$relative"); observed=${observed%% *}
  [[ "$observed" = "$expected" ]] || fail "staged Task 7 recipe input differs: $relative"
done < <(/usr/bin/jq -er '.versioned_inputs | to_entries[] | [.key,.value] | @tsv' \
  "$installed_source/task7-build-recipe.json")
secure_verify_recipe "$installed_source" >/dev/null
validate_stock "$stock" || fail "caller stock distribution changed during install"
secure_publish_create_new "$stage" "$output"
stage=
/usr/bin/printf '%s\n' "$output"
