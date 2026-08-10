#!/usr/bin/bash -p
set -euo pipefail

script_dir=${BASH_SOURCE[0]%/*}; [[ "$script_dir" != "${BASH_SOURCE[0]}" ]] || script_dir=.
script_dir=$(cd -P -- "$script_dir" && pwd)
source "$script_dir/secure-runtime.sh"
fail() { printf 'prepare-managed-inputs: %s\n' "$*" >&2; exit 1; }

sdk_archive=
nuget_dir=
output=
while (($#)); do
  case "$1" in
    --sdk-archive) sdk_archive=${2-}; shift 2 ;;
    --nuget-packages) nuget_dir=${2-}; shift 2 ;;
    --output) output=${2-}; shift 2 ;;
    *) fail "unknown argument: $1" ;;
  esac
done
secure_require_absent_output "$output"
[[ "$sdk_archive" = /* && -f "$sdk_archive" && ! -L "$sdk_archive" ]] \
  || fail "SDK archive must be an absolute, non-symlink file"
[[ "$nuget_dir" = /* && -d "$nuget_dir" && ! -L "$nuget_dir" ]] \
  || fail "NuGet packages must be an absolute, non-symlink directory"
recipe_sha=$(secure_verify_recipe "$script_dir")

parent=${output%/*}; [[ -n "$parent" ]] || parent=/
stage=$(mktemp -d "$parent/.gpgx-managed-input-staging.XXXXXX")
cleanup() { if [[ -n "${stage-}" && -d "$stage" ]]; then rm -rf -- "$stage"; fi; }
trap cleanup EXIT
mkdir -p "$stage/nuget"
cp -- "$sdk_archive" "$stage/dotnet-sdk-8.0.414-linux-x64.tar.gz"
printf '%s  %s\n' 7786bbe5093e3a5d354a1ffa56083b6a32ad12837a83170f1f3b51ad7df28516 \
  "$stage/dotnet-sdk-8.0.414-linux-x64.tar.gz" | sha256sum -c - >/dev/null \
  || fail "wrong SDK archive"

input_count=$(find "$nuget_dir" -type f -name '*.nupkg' | wc -l)
[[ "$input_count" = 114 ]] || fail "wrong NuGet package count: $input_count"
while IFS=$'\t' read -r relative expected; do
  [[ "$relative" != /* && "$relative" != *'..'* ]] || fail "unsafe package path: $relative"
  source_package=$nuget_dir/$relative
  [[ -f "$source_package" && ! -L "$source_package" ]] || fail "missing locked package: $relative"
  destination=$stage/nuget/$relative
  mkdir -p "${destination%/*}"
  cp -- "$source_package" "$destination"
  observed=$(sha256sum "$destination"); observed=${observed%% *}
  [[ "$observed" = "$expected" ]] || fail "snapshotted package differs: $relative"
done < <(/usr/bin/jq -er '.packages[] | [.path,.sha256] | @tsv' "$script_dir/managed-nuget-manifest.json")
prepared_count=$(find "$stage/nuget" -type f -name '*.nupkg' | wc -l)
[[ "$prepared_count" = 114 ]] || fail "prepared package count differs: $prepared_count"
package_tree_sha=$(
  cd "$stage/nuget"
  find . -type f -name '*.nupkg' -printf '%P\n' | LC_ALL=C sort \
    | while IFS= read -r relative; do
        observed=$(sha256sum "$relative"); observed=${observed%% *}
        printf '%s  %s\n' "$observed" "$relative"
      done \
    | sha256sum | cut -d' ' -f1
)
[[ "$package_tree_sha" = e0afe65b153f1f3cbaed03c8e3987542322a9ea1a220cac3696bc7ba59c42290 ]] \
  || fail "prepared package tree differs: $package_tree_sha"
manifest_sha=$(sha256sum "$script_dir/managed-nuget-manifest.json"); manifest_sha=${manifest_sha%% *}
printf '{"schema":"openggf.gpgx-managed-inputs.v1","sdk_archive_sha256":"7786bbe5093e3a5d354a1ffa56083b6a32ad12837a83170f1f3b51ad7df28516","nuget_package_count":114,"nuget_manifest_sha256":"%s","nuget_package_tree_sha256":"%s","build_recipe_sha256":"%s"}\n' \
  "$manifest_sha" "$package_tree_sha" "$recipe_sha" > "$stage/identity.json"
secure_publish_create_new "$stage" "$output"
stage=
printf '%s\n' "$output"
