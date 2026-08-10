#!/usr/bin/env bash
set -euo pipefail

fail() { printf 'reproduce-stock-managed: %s\n' "$*" >&2; exit 1; }
publish_create_new() {
  local source=$1 target=$2
  printf '%s  %s\n' 4dc8719b3b60a5e03b3720f3060415a8dd3b564b74319539b2a0dc52bc50c0df /usr/bin/mv \
    | sha256sum -c - >/dev/null || fail "host no-replace publisher differs"
  /usr/bin/mv -T --no-copy --no-clobber -- "$source" "$target"
  [[ ! -e "$source" && ! -L "$source" ]] || fail "output already exists: $target"
}
source_dir=
sdk_archive=
nuget_dir=
stock_dir=
output=
while (($#)); do
  case "$1" in
    --source) source_dir=${2-}; shift 2 ;;
    --sdk-archive) sdk_archive=${2-}; shift 2 ;;
    --nuget-packages) nuget_dir=${2-}; shift 2 ;;
    --stock) stock_dir=${2-}; shift 2 ;;
    --output) output=${2-}; shift 2 ;;
    *) fail "unknown argument: $1" ;;
  esac
done
[[ "$output" = /* ]] || fail "output must be an absolute path"
[[ ! -e "$output" && ! -L "$output" ]] || fail "output already exists: $output"
[[ -d "$(dirname "$output")" ]] || fail "output parent does not exist"
for pair in "source:$source_dir" "nuget-packages:$nuget_dir" "stock:$stock_dir"; do
  name=${pair%%:*}; value=${pair#*:}
  [[ "$value" = /* && -d "$value" && ! -L "$value" ]] || fail "$name must be an absolute, non-symlink directory"
done
[[ "$sdk_archive" = /* && -f "$sdk_archive" && ! -L "$sdk_archive" ]] || fail "SDK archive must be an absolute, non-symlink file"

parent=$(dirname "$output")
stage=$(mktemp -d "$parent/.gpgx-managed-staging.XXXXXX")
cleanup() { if [[ -n "${stage-}" && -d "$stage" ]]; then rm -rf -- "$stage"; fi; }
trap cleanup EXIT
mkdir "$stage/source" "$stage/nuget-input-tree" "$stage/stock-input"
mkdir "$stage/stock-input/dll"
cp -a "$source_dir/." "$stage/source/"
cp -a "$nuget_dir/." "$stage/nuget-input-tree/"
cp -- "$sdk_archive" "$stage/sdk-archive.tar.gz"
cp -- "$stock_dir/dll/BizHawk.Emulation.Cores.dll" \
  "$stock_dir/dll/BizHawk.Emulation.Common.dll" "$stage/stock-input/dll/"
source_dir=$stage/source
nuget_dir=$stage/nuget-input-tree
sdk_archive=$stage/sdk-archive.tar.gz
stock_dir=$stage/stock-input

[[ $(git -C "$source_dir" rev-parse HEAD) = 427556b5ef3ac437eba754d90c5e7e9096c9a8df ]] || fail "wrong BizHawk commit"
[[ $(git -C "$source_dir" rev-parse HEAD^{tree}) = 7281227ed2f3b89c0962b2792b28539e35361c6b ]] || fail "wrong BizHawk tree"
[[ $(git -C "$source_dir/waterbox/gpgx/Genesis-Plus-GX" rev-parse HEAD) = 051d430d3d1b54625f9900c8f152d7f232e06daf ]] || fail "wrong GPGX commit"
[[ $(git -C "$source_dir/waterbox/gpgx/Genesis-Plus-GX" rev-parse HEAD^{tree}) = 1bb96ca74d660d383e70d9cd56b88906a0773519 ]] || fail "wrong GPGX tree"
[[ $(git -C "$source_dir/waterbox/musl" rev-parse HEAD) = 2063abc4e16c84218757b1db10d3cdf9f36ef3f8 ]] || fail "wrong musl commit"
[[ $(git -C "$source_dir/waterbox/musl" rev-parse HEAD^{tree}) = a9969a63cd1780cdcc4c09745a8789206a72b8b4 ]] || fail "wrong musl tree"
for repository in "$source_dir" "$source_dir/waterbox/gpgx/Genesis-Plus-GX" "$source_dir/waterbox/musl"; do
  [[ -z $(git -C "$repository" status --short --untracked-files=all --ignore-submodules=none) ]] \
    || fail "source tree is not clean: $repository"
  [[ -z $(git -C "$repository" clean -ndx) ]] || fail "source tree has untracked or ignored files: $repository"
done
printf '%s  %s\n' 7786bbe5093e3a5d354a1ffa56083b6a32ad12837a83170f1f3b51ad7df28516 "$sdk_archive" | sha256sum -c - >/dev/null || fail "wrong SDK archive"

package_count=$(find "$nuget_dir" -type f -name '*.nupkg' | wc -l)
[[ "$package_count" = 114 ]] || fail "wrong NuGet package count: $package_count"
nuget_manifest=$(
  cd "$nuget_dir"
  find . -type f -name '*.nupkg' -printf '%P\n' | LC_ALL=C sort | while IFS= read -r path; do
    printf '%s  %s\n' "$(sha256sum "$path" | cut -d' ' -f1)" "$path"
  done | sha256sum | cut -d' ' -f1
)
[[ "$nuget_manifest" = e0afe65b153f1f3cbaed03c8e3987542322a9ea1a220cac3696bc7ba59c42290 ]] || fail "wrong NuGet package manifest: $nuget_manifest"
while read -r expected file; do
  printf '%s  %s\n' "$expected" "$source_dir/$file" | sha256sum -c - >/dev/null || fail "managed source differs: $file"
done <<'LOCKED_MANAGED_SOURCE'
885423654996ef60f577d909c28af5a39cbf454b2531c1db649cc4db9595816b global.json
9683ffbf9905480fca479bda914b56f9b7137cbfefb5491f44ebc531fe51c7b9 .github/workflows/release.yml
bdd8443737a4f993b38c2e4786deb686a86e99cf6dd8c8f42870e6fe3a94b05d Dist/BuildRelease.sh
c7901d62bfee8a8f8790e11b3776d5caad5c548be3d5a5ea905a63cbaf944241 Dist/.BuildInConfigX.sh
92588389ff8ea57855217a7b469b73e5b26e2e2e3565b51be6804dfa30c57506 Dist/.InvokeCLIOnMainSln.sh
056dd1375264dc985c7c5217d255af286445f5a8409eb42435082389980f2589 Dist/UpdateVersionInfoForRelease.sh
2d3b851e8fdb4136815242e0a5127ca6650a29cdb38f1064168f40570b61af78 src/BizHawk.Emulation.Cores/BizHawk.Emulation.Cores.csproj
dd2e02606076de7d42a3b1dba42b08d6eedf83a92439f841c587db83bb1a40b5 src/BizHawk.Emulation.Common/BizHawk.Emulation.Common.csproj
LOCKED_MANAGED_SOURCE
while read -r expected file; do
  printf '%s  %s\n' "$expected" "$stock_dir/$file" | sha256sum -c - >/dev/null || fail "managed stock differs: $file"
done <<'LOCKED_MANAGED_STOCK'
0144e6e236be68ce126eb771dcb5a9ae7c153a083fa0333f345ac37b4a60acf7 dll/BizHawk.Emulation.Cores.dll
f20cd009f6f5b0a95bd47b66c48dc8de85afcd7ae0cc6aab3486baf55f501fb4 dll/BizHawk.Emulation.Common.dll
LOCKED_MANAGED_STOCK

mkdir "$stage/sdk" "$stage/feed" "$stage/packages" "$stage/dotnet-home"
mkdir "$stage/dotnet-home/.dotnet"
touch "$stage/dotnet-home/.dotnet/8.0.414.dotnetFirstUseSentinel" \
  "$stage/dotnet-home/.dotnet/8.0.414.aspNetCertificateSentinel" \
  "$stage/dotnet-home/.dotnet/8.0.414.toolpath.sentinel" \
  "$stage/dotnet-home/.dotnet/.workloadAdvertisingManifestSentinel8.0.400" \
  "$stage/dotnet-home/.dotnet/.workloadAdvertisingUpdates8.0.400"
find "$stage/source" -type d \( -name bin -o -name obj \) -prune -exec rm -rf -- {} +
/usr/bin/tar -xf "$sdk_archive" -C "$stage/sdk"
printf '%s  %s\n' 37674a9f73c1f531b7dcb26f569692f2c85419c0ba4fb7622d5bfc65bb0f5810 "$stage/sdk/dotnet" | sha256sum -c - >/dev/null || fail "wrong SDK executable"
while IFS= read -r package; do
  relative=${package#"$nuget_dir"/}
  ln -s "/opt/task6-nuget-tree/$relative" "$stage/feed/$(basename "$package")"
done < <(find "$nuget_dir" -type f -name '*.nupkg' | LC_ALL=C sort)
sed -i 's/ReleaseDate = "[^"]*"/ReleaseDate = "September 20, 2025"/' "$stage/source/src/BizHawk.Common/VersionInfo.cs"
sed -i 's/DeveloperBuild = true/DeveloperBuild = false/' "$stage/source/src/BizHawk.Common/VersionInfo.cs"

build_root_hex=2f686f6d652f
build_root_hex+=66656f73
build_root_hex+=2f7368617265732f73686172652f42697a4861776b
build_root=$(printf '%s' "$build_root_hex" | xxd -r -p)
build_parent=$(dirname "$build_root")
build_grandparent=$(dirname "$build_parent")
build_great_grandparent=$(dirname "$build_grandparent")
set +e
env -i /usr/bin/bwrap --die-with-parent --ro-bind / / \
  --dev /dev --proc /proc --tmpfs /tmp --tmpfs /home --tmpfs /opt \
  --dir "$build_great_grandparent" --dir "$build_grandparent" --dir "$build_parent" \
  --bind "$stage/source" "$build_root" \
  --ro-bind "$stage/sdk" /opt/task6-dotnet \
  --ro-bind "$nuget_dir" /opt/task6-nuget-tree \
  --ro-bind "$stage/feed" /opt/task6-nuget-input \
  --bind "$stage/packages" /opt/task6-nuget-cache \
  --bind "$stage/dotnet-home" /opt/task6-home \
  --setenv HOME /opt/task6-home --setenv DOTNET_CLI_HOME /opt/task6-home \
  --setenv NUGET_PACKAGES /opt/task6-nuget-cache \
  --setenv PATH /opt/task6-dotnet:/usr/bin:/bin \
  --setenv LC_ALL C --setenv TZ UTC --setenv SOURCE_DATE_EPOCH 1758367997 \
  --setenv DOTNET_NOLOGO 1 --setenv DOTNET_CLI_TELEMETRY_OPTOUT 1 \
  --setenv DOTNET_SKIP_FIRST_TIME_EXPERIENCE 1 \
  /bin/sh -eu -c "/opt/task6-dotnet/dotnet restore '$build_root/BizHawk.sln' --source /opt/task6-nuget-input --packages /opt/task6-nuget-cache --disable-parallel --ignore-failed-sources; /opt/task6-dotnet/dotnet build '$build_root/src/BizHawk.Emulation.Cores/BizHawk.Emulation.Cores.csproj' -c Release --no-restore -m:1 -p:Version=2.11 -p:SourceRevisionId=427556b5ef3ac437eba754d90c5e7e9096c9a8df" >"$stage/build.log" 2>&1
build_status=$?
set -e
[[ "$build_status" = 0 ]] || { sed -n '1,200p' "$stage/build.log" >&2; fail "managed candidate build failed"; }

cores="$stage/source/src/BizHawk.Emulation.Cores/bin/Release/BizHawk.Emulation.Cores.dll"
common="$stage/source/src/BizHawk.Emulation.Cores/bin/Release/BizHawk.Emulation.Common.dll"
[[ -f "$cores" && -f "$common" ]] || fail "managed candidate artifacts are missing"
cores_size=$(stat -c %s "$cores"); common_size=$(stat -c %s "$common")
cores_sha=$(sha256sum "$cores" | cut -d' ' -f1); common_sha=$(sha256sum "$common" | cut -d' ' -f1)
[[ "$cores_size" = 8779776 && "$cores_sha" = f7e7ea11f05adb7bcdc1f55c09810f873abfe06debdc3f3b100185f20a69c031 ]] \
  || fail "managed Cores candidate differs from the locked mismatch"
[[ "$common_size" = 421376 && "$common_sha" = 96f494af9be13f52dc63ab3d430b15641fc142cf469339a8bf013e67b99b757e ]] \
  || fail "managed Common candidate differs from the locked mismatch"
cores_cmp=false; common_cmp=false
cmp -s "$cores" "$stock_dir/dll/BizHawk.Emulation.Cores.dll" && cores_cmp=true
cmp -s "$common" "$stock_dir/dll/BizHawk.Emulation.Common.dll" && common_cmp=true
[[ "$cores_cmp" = false || "$common_cmp" = false ]] || fail "managed stock unexpectedly reproduced; adapter lock requires review"

printf '{"schema":"openggf.gpgx-managed-reproduction.v1","status":"BYTE_MISMATCH","sdk_version":"8.0.414","nuget_manifest_sha256":"e0afe65b153f1f3cbaed03c8e3987542322a9ea1a220cac3696bc7ba59c42290","cores_size":%s,"cores_sha256":"%s","cores_stock_cmp":%s,"common_size":%s,"common_sha256":"%s","common_stock_cmp":%s,"selected_adapter":"REFLECTION","patched_managed_dll_permitted":false}\n' \
  "$cores_size" "$cores_sha" "$cores_cmp" "$common_size" "$common_sha" "$common_cmp" > "$stage/identity.json"
rm -rf -- "$stage/source" "$stage/sdk" "$stage/feed" "$stage/packages" "$stage/dotnet-home" \
  "$stage/nuget-input-tree" "$stage/sdk-archive.tar.gz" "$stage/stock-input" "$stage/build.log"
publish_create_new "$stage" "$output"
stage=
printf 'managed bytes did not match stock; reflection remains required\n' >&2
exit 3
