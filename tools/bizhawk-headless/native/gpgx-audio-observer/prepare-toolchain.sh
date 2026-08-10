#!/usr/bin/env bash
set -euo pipefail

fail() { printf 'prepare-toolchain: %s\n' "$*" >&2; exit 1; }
publish_create_new() {
  local source=$1 target=$2
  printf '%s  %s\n' 4dc8719b3b60a5e03b3720f3060415a8dd3b564b74319539b2a0dc52bc50c0df /usr/bin/mv \
    | sha256sum -c - >/dev/null || fail "host no-replace publisher differs"
  /usr/bin/mv -T --no-copy --no-clobber -- "$source" "$target"
  [[ ! -e "$source" && ! -L "$source" ]] || fail "output already exists: $target"
}
source_dir=
packages_dir=
output=
while (($#)); do
  case "$1" in
    --source) source_dir=${2-}; shift 2 ;;
    --packages) packages_dir=${2-}; shift 2 ;;
    --output) output=${2-}; shift 2 ;;
    *) fail "unknown argument: $1" ;;
  esac
done
[[ "$output" = /* ]] || fail "output must be an absolute path"
[[ ! -e "$output" && ! -L "$output" ]] || fail "output already exists: $output"
[[ -d "$(dirname "$output")" ]] || fail "output parent does not exist"
for pair in "source:$source_dir" "packages:$packages_dir"; do
  name=${pair%%:*}; value=${pair#*:}
  [[ "$value" = /* && -d "$value" && ! -L "$value" ]] || fail "$name must be an absolute, non-symlink directory"
done

parent=$(dirname "$output")
stage=$(mktemp -d "$parent/.gpgx-toolchain-staging.XXXXXX")
cleanup() { if [[ -n "${stage-}" && -d "$stage" ]]; then rm -rf -- "$stage"; fi; }
trap cleanup EXIT
mkdir -p "$stage/clang" "$stage/deb" "$stage/work-source" "$stage/package-input"
cp -a "$source_dir/." "$stage/work-source/"
for file in \
  clang-16_16.0.6-15_amd64.deb \
  libclang-cpp16_16.0.6-15_amd64.deb \
  libllvm16_16.0.6-15_amd64.deb \
  llvm-16-linker-tools_16.0.6-15_amd64.deb \
  libclang-common-16-dev_16.0.6-15_all.deb \
  lld-16_16.0.6-15_amd64.deb \
  libclang-rt-16-dev_16.0.6-15_amd64.deb \
  libedit2_3.1-20221030-2_amd64.deb \
  libxml2_2.9.14+dfsg-1.3ubuntu0.1_amd64.deb \
  libicu72_72.1-3ubuntu3_amd64.deb \
  zstd-1.5.5.tar.gz; do
  [[ -f "$packages_dir/$file" && ! -L "$packages_dir/$file" ]] || fail "missing locked package: $file"
  cp -- "$packages_dir/$file" "$stage/package-input/$file"
done
source_dir=$stage/work-source
packages_dir=$stage/package-input

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

while read -r expected file; do
  [[ -f "$packages_dir/$file" && ! -L "$packages_dir/$file" ]] || fail "missing locked package: $file"
  printf '%s  %s\n' "$expected" "$packages_dir/$file" | sha256sum -c - >/dev/null
done <<'LOCKED_PACKAGES'
b9cd4d27a5d1b6c429fccf56a4ac1c4ac5baf2cb9b5a53e2a20fcd6593153e5a clang-16_16.0.6-15_amd64.deb
39eb3e73119ef0180489c7e594d29398152b3a2d7eec2361cf87d367032f466a libclang-cpp16_16.0.6-15_amd64.deb
3353bbe1910cfc99a8ef96e1cd7df45c65e2aaebefcfc801bcb7587bab819a15 libllvm16_16.0.6-15_amd64.deb
39f6c47b5ecc04c064899a99d224650b2d932e7f27ac02246073395fc8bd1300 llvm-16-linker-tools_16.0.6-15_amd64.deb
ada57e3ac045bb324397c6d269dbad56a0b0f3608c89d321d1fed38206570ff5 libclang-common-16-dev_16.0.6-15_all.deb
e75a2e784d2da2e3d90a31d7b8002892ac58b90e53073a14c7db1a8d80172204 lld-16_16.0.6-15_amd64.deb
20f3b1a105d5b8fba261a03bd6ad531e09a87c929f33f54e5dd4db78f980dda2 libclang-rt-16-dev_16.0.6-15_amd64.deb
d1c26768f5e108c97d9520c8a19356ddf5a1967222af4f38efb1f5af21da46b5 libedit2_3.1-20221030-2_amd64.deb
7c4d4ec04145f854bb824cb72fb34233c99f7db3eaafaa3d2049bd82800c0f85 libxml2_2.9.14+dfsg-1.3ubuntu0.1_amd64.deb
3db0831a7a8da3c8d878fdbc4644d4131ed914b22c8a0cffbcabe68a2c3f6ec4 libicu72_72.1-3ubuntu3_amd64.deb
9c4396cc829cfae319a6e2615202e82aad41372073482fce286fac78646d3ee4 zstd-1.5.5.tar.gz
LOCKED_PACKAGES

while read -r expected executable; do
  printf '%s  %s\n' "$expected" "$executable" | sha256sum -c - >/dev/null || fail "wrong host build executable: $executable"
done <<'LOCKED_HOST_TOOLS'
0052cc9e1280ad0874744623d7241afa01f689be9c0d627056876bb254af5c51 /usr/bin/make
69c93ee96fe89de9a071010905786a48c136fbabcdafff2fbd5bc4f2d7866f84 /usr/bin/ar
23fad77931641e49fc9f6ca955796f1713436b4c00d1da871786f11af460c462 /usr/bin/tar
ed4c733407f4a77de4e4e35a89e8575f4efe04823ec07495a02c99a9169baf8b /usr/bin/gcc
eabbccb0f7f755b96d30834026a9b5d941c606400d097d87c1ff16622edaf68c /usr/bin/bwrap
LOCKED_HOST_TOOLS
while read -r expected library; do
  printf '%s  %s\n' "$expected" "$library" | sha256sum -c - >/dev/null || fail "wrong zstd build library: $library"
done <<'LOCKED_ZSTD_LIBRARIES'
2a252a45a28d93ca2e6a7d2662f6cef5cfa666c9da2f8cfbc90bc521c45a03c5 /usr/lib/libz.so.1
901c835bf040bb531c1801d9e9400cf1181c27db708f9634ca33941e5fd5f0d5 /usr/lib/liblzma.so.5
2999ba4a7587726402b0ecd4ea970ba6da9bd4ac93f1e63a26d948e37abdf9b5 /usr/lib/liblz4.so.1
4804f1729b20c523cd1cc84034a38c80f83db72645c1366bfa2e300e112f193f /usr/lib/libc.so.6
97c4ef84e2abe44c1ab1f37753f259b00b3f73574fe711b6a123e5fe75ae6b7c /usr/lib64/ld-linux-x86-64.so.2
LOCKED_ZSTD_LIBRARIES

for package in "$packages_dir"/*.deb; do
  package_name=$(basename "$package")
  case "$package_name" in
    clang-16_16.0.6-15_amd64.deb|libclang-cpp16_16.0.6-15_amd64.deb|libllvm16_16.0.6-15_amd64.deb|llvm-16-linker-tools_16.0.6-15_amd64.deb|libclang-common-16-dev_16.0.6-15_all.deb|lld-16_16.0.6-15_amd64.deb|libclang-rt-16-dev_16.0.6-15_amd64.deb|libedit2_3.1-20221030-2_amd64.deb|libxml2_2.9.14+dfsg-1.3ubuntu0.1_amd64.deb|libicu72_72.1-3ubuntu3_amd64.deb) ;;
    *) continue ;;
  esac
  unpack="$stage/deb/${package_name%.deb}"
  mkdir "$unpack"
  (cd "$unpack" && /usr/bin/ar x "$package")
  data_archive=$(find "$unpack" -maxdepth 1 -type f -name 'data.tar.*' -print -quit)
  [[ -n "$data_archive" ]] || fail "package has no data archive: $package_name"
  /usr/bin/tar -xf "$data_archive" -C "$stage/clang"
done
ln -s ld.lld-16 "$stage/clang/usr/bin/ld.lld"

printf '%s  %s\n' bb6556bdcdeb00dca0c758da9966a9982542a23ddcaffa784a2de9344ede3fc0 "$stage/clang/usr/lib/llvm-16/bin/clang" | sha256sum -c - >/dev/null
printf '%s  %s\n' f8d0601bf957a1b063e29c3c43613a5b76482f6c14664b9fcac4d596871e14df "$stage/clang/usr/lib/llvm-16/bin/ld.lld" | sha256sum -c - >/dev/null

/usr/bin/tar -xf "$packages_dir/zstd-1.5.5.tar.gz" -C "$stage"
if ! env -i PATH=/usr/bin:/bin LC_ALL=C TZ=UTC SOURCE_DATE_EPOCH=1758367997 \
  /usr/bin/make -C "$stage/zstd-1.5.5" -j1 zstd CC=/usr/bin/gcc >"$stage/zstd-build.log" 2>&1; then
  tail -200 "$stage/zstd-build.log" >&2
  fail "zstd build failed"
fi
printf '%s  %s\n' 7bc75866617449d384679bd29298a222a458ff0daea0fc4c221122b5513cf307 "$stage/zstd-1.5.5/programs/zstd" | sha256sum -c - >/dev/null || fail "zstd build is not the locked binary"

rm -rf -- "$stage/work-source/waterbox/sysroot" "$stage/work-source/waterbox/emulibc/obj"
git -C "$stage/work-source/waterbox/musl" clean -ffdx >/dev/null
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
  --bind "$stage/work-source" "$build_root" \
  --bind "$stage/clang" /opt/task6-clang \
  --setenv PATH /opt/task6-clang/usr/bin:/usr/bin:/bin \
  --setenv LD_LIBRARY_PATH /opt/task6-clang/usr/lib/x86_64-linux-gnu:/opt/task6-clang/usr/lib/llvm-16/lib \
  --setenv LC_ALL C --setenv TZ UTC --setenv SOURCE_DATE_EPOCH 1758367997 --setenv MAKEFLAGS -j1 \
  /bin/sh -eu -c "umask 0022; cd '$build_root/waterbox/musl'; CC=clang-16 SYSROOT='$build_root/waterbox/sysroot' ./wbox_configure.sh; ./wbox_build.sh; mkdir -p '$build_root/waterbox/sysroot/lib/linux'; cp /opt/task6-clang/usr/lib/llvm-16/lib/clang/16/lib/linux/libclang_rt.builtins-x86_64.a '$build_root/waterbox/sysroot/lib/linux/'; /usr/bin/make -C '$build_root/waterbox/emulibc' -j1" >"$stage/waterbox-build.log" 2>&1; then
  tail -200 "$stage/waterbox-build.log" >&2
  fail "Waterbox toolchain build failed"
fi

printf '%s  %s\n' c787fe4acc581a8b4787f737133425abe65a589200ce049aeec9780626afe620 "$stage/work-source/waterbox/emulibc/obj/release/emulibc.c.o" | sha256sum -c - >/dev/null || fail "emulibc build differs"
mv "$stage/work-source/waterbox/sysroot" "$stage/sysroot"
cp "$stage/work-source/waterbox/emulibc/obj/release/emulibc.c.o" "$stage/emulibc.c.o"
cp "$stage/zstd-1.5.5/programs/zstd" "$stage/zstd"
rm -rf -- "$stage/work-source" "$stage/package-input" "$stage/zstd-1.5.5" "$stage/deb" \
  "$stage/zstd-build.log" "$stage/waterbox-build.log"

count=$(find "$stage/sysroot" -type f | wc -l)
[[ "$count" = 235 ]] || fail "wrong sysroot file count: $count"
tree_digest=$(
  cd "$stage/sysroot"
  find . -type f -printf '%P\n' | LC_ALL=C sort | while IFS= read -r path; do
    printf 'f\t%s\t%s\t%s\n' "$(stat -c %a "$path")" "$(sha256sum "$path" | cut -d' ' -f1)" "$path"
  done | sha256sum | cut -d' ' -f1
)
[[ "$tree_digest" = fc06187ae45bcedeea4f76f33868ccb05a8c80831d5dce19adbd5eee6e6e06e1 ]] || fail "sysroot differs: $tree_digest"

complete_tree_digest=$(
  cd "$stage"
  find . \( -type f -o -type l \) -printf '%P\n' | LC_ALL=C sort | while IFS= read -r path; do
    if [[ -L "$path" ]]; then
      printf 'l\t%s\t%s\n' "$(readlink "$path")" "$path"
    else
      printf 'f\t%s\t%s\t%s\n' "$(stat -c %a "$path")" "$(sha256sum "$path" | cut -d' ' -f1)" "$path"
    fi
  done | sha256sum | cut -d' ' -f1
)
[[ "$complete_tree_digest" = 9caa5c02dcd2d9c01e5d0196956787a0f31760195c6544a2ceafcb771f469521 ]] \
  || fail "complete toolchain tree differs: $complete_tree_digest"

publish_create_new "$stage" "$output"
stage=
printf '%s\n' "$output"
