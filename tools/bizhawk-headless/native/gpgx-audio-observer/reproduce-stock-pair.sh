#!/usr/bin/bash -p
set -euo pipefail

script_dir=${BASH_SOURCE[0]%/*}; [[ "$script_dir" != "${BASH_SOURCE[0]}" ]] || script_dir=.
script_dir=$(cd -P -- "$script_dir" && pwd)
source "$script_dir/secure-runtime.sh"
fail() { printf 'reproduce-stock-pair: %s\n' "$*" >&2; exit 1; }

packages_dir=
sdk_archive=
nuget_dir=
stock_dir=
output=
while (($#)); do
  case "$1" in
    --packages) packages_dir=${2-}; shift 2 ;;
    --sdk-archive) sdk_archive=${2-}; shift 2 ;;
    --nuget-packages) nuget_dir=${2-}; shift 2 ;;
    --stock) stock_dir=${2-}; shift 2 ;;
    --output) output=${2-}; shift 2 ;;
    *) fail "unknown argument: $1" ;;
  esac
done
secure_require_absent_output "$output"
for pair in "packages:$packages_dir" "nuget-packages:$nuget_dir" "stock:$stock_dir"; do
  name=${pair%%:*}; value=${pair#*:}
  [[ "$value" = /* && -d "$value" && ! -L "$value" ]] \
    || fail "$name must be an absolute, non-symlink directory"
done
[[ "$sdk_archive" = /* && -f "$sdk_archive" && ! -L "$sdk_archive" ]] \
  || fail "SDK archive must be an absolute, non-symlink file"
recipe_sha=$(secure_verify_recipe "$script_dir")

parent=${output%/*}; [[ -n "$parent" ]] || parent=/
stage=$(mktemp -d "$parent/.gpgx-stock-pair-staging.XXXXXX")
cleanup() { if [[ -n "${stage-}" && -d "$stage" ]]; then rm -rf -- "$stage"; fi; }
trap cleanup EXIT
secure_snapshot_tree "$stock_dir" "$stage/stock-input"

for run in a b; do
  /usr/bin/bash -p "$script_dir/fetch-source.sh" --output "$stage/source-$run"
  /usr/bin/bash -p "$script_dir/prepare-toolchain.sh" \
    --source "$stage/source-$run" --packages "$packages_dir" \
    --output "$stage/toolchain-$run"
  /usr/bin/bash -p "$script_dir/reproduce-stock-core.sh" \
    --source "$stage/source-$run" --toolchain "$stage/toolchain-$run" \
    --stock "$stage/stock-input" --output "$stage/native-$run"
done

secure_equal_files "$stage/native-a/gpgx.wbx" "$stage/native-b/gpgx.wbx"
secure_equal_files "$stage/native-a/gpgx.wbx.zst" "$stage/native-b/gpgx.wbx.zst"
secure_equal_files "$stage/native-a/identity.json" "$stage/native-b/identity.json"
secure_equal_files "$stage/native-a/gpgx.wbx.zst" "$stage/stock-input/dll/gpgx.wbx.zst"
secure_equal_files "$stage/native-b/gpgx.wbx.zst" "$stage/stock-input/dll/gpgx.wbx.zst"
"$stage/toolchain-a/zstd" -d --stdout "$stage/stock-input/dll/gpgx.wbx.zst" > "$stage/stock-gpgx.wbx"
secure_equal_files "$stage/native-a/gpgx.wbx" "$stage/stock-gpgx.wbx"
secure_equal_files "$stage/native-b/gpgx.wbx" "$stage/stock-gpgx.wbx"
rm -- "$stage/stock-gpgx.wbx"

/usr/bin/bash -p "$script_dir/prepare-managed-inputs.sh" \
  --sdk-archive "$sdk_archive" --nuget-packages "$nuget_dir" \
  --output "$stage/managed-inputs"
for run in a b; do
  set +e
  /usr/bin/bash -p "$script_dir/reproduce-stock-managed.sh" \
    --source "$stage/source-$run" --managed-inputs "$stage/managed-inputs" \
    --stock "$stage/stock-input" --output "$stage/managed-$run"
  managed_status=$?
  set -e
  [[ "$managed_status" = 3 ]] \
    || fail "managed $run did not report the locked byte mismatch: $managed_status"
done
secure_equal_files "$stage/managed-a/identity.json" "$stage/managed-b/identity.json"

printf '{"schema":"openggf.gpgx-stock-pair.v1","build_recipe_sha256":"%s","native_run_count":2,"raw_pair_cmp":true,"compressed_pair_cmp":true,"identity_pair_cmp":true,"raw_stock_cmp_a":true,"raw_stock_cmp_b":true,"compressed_stock_cmp_a":true,"compressed_stock_cmp_b":true,"managed_run_count":2,"managed_status":"BYTE_MISMATCH","selected_adapter":"REFLECTION"}\n' \
  "$recipe_sha" > "$stage/identity.json"
secure_publish_create_new "$stage" "$output"
stage=
printf '%s\n' "$output"
