#!/usr/bin/env bash
set -euo pipefail

fail() { printf 'verify-inputs: %s\n' "$*" >&2; exit 1; }
source_dir=
toolchain_dir=
while (($#)); do
  case "$1" in
    --source) source_dir=${2-}; shift 2 ;;
    --toolchain) toolchain_dir=${2-}; shift 2 ;;
    *) fail "unknown argument: $1" ;;
  esac
done
for pair in "source:$source_dir" "toolchain:$toolchain_dir"; do
  name=${pair%%:*}; value=${pair#*:}
  [[ "$value" = /* && -d "$value" && ! -L "$value" ]] || fail "$name must be an absolute, non-symlink directory"
done

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

while read -r expected relative; do
  printf '%s  %s\n' "$expected" "$source_dir/$relative" | sha256sum -c - >/dev/null
done <<'LOCKED_SOURCE'
c328932fde7df37ce21759045b5b90f13170b9df88b1798e064c35a34b8fbb1f waterbox/common.mak
3a5f16e86596f0bb4b254b0fa0c4ba68effbf8a438ef34bbfbe7b179692cd536 waterbox/linkscript.T
c92fd9b2cbce52c75b580bf91d357cc028c5ea5c935475b042cce7110ef4caaa waterbox/gpgx/Makefile
34abbf5b7c115b3c8c1cf58cc4b2efef87d20a175dccfef1739b0444a022662c waterbox/musl/wbox_configure.sh
ef7b3279e8be2e1b519f73812a217f4027bd97c8f09dd6a691f1061766d2af2b waterbox/musl/wbox_build.sh
LOCKED_SOURCE
while read -r expected relative; do
  printf '%s  %s\n' "$expected" "$toolchain_dir/$relative" | sha256sum -c - >/dev/null
done <<'LOCKED_TOOLCHAIN'
bb6556bdcdeb00dca0c758da9966a9982542a23ddcaffa784a2de9344ede3fc0 clang/usr/lib/llvm-16/bin/clang
f8d0601bf957a1b063e29c3c43613a5b76482f6c14664b9fcac4d596871e14df clang/usr/lib/llvm-16/bin/ld.lld
55f9e1b3c3b98853fc31787414064de36a22cc23f870962b45832fc904c498a2 clang/usr/lib/x86_64-linux-gnu/libLLVM-16.so.1
f9bf97848329b4d444c8c8791b9f8a584b58016852a6ba4b55db164726623ac7 clang/usr/lib/llvm-16/lib/libclang-cpp.so.16
08b20f771fa51719ba64c84eb9cfefe54fde05d6a6948ffcd9753fba38da2f5d sysroot/bin/musl-clang
8a3b835e2cbdc52db8259d8c03c5e32227fd1c350af22925438cd8b54fd2d2db sysroot/bin/ld.musl-clang
9b8f89ee3105aad8b2a18805362677b6d983721e9d3706629359ddf7c9ec837b sysroot/lib/libc.a
2f257b223dbee10ea0415e5f95385a71dc05bb94505a21a4be1d22ce733e624d sysroot/lib/linux/libclang_rt.builtins-x86_64.a
7bc75866617449d384679bd29298a222a458ff0daea0fc4c221122b5513cf307 zstd
LOCKED_TOOLCHAIN

count=$(find "$toolchain_dir/sysroot" -type f | wc -l)
[[ "$count" = 235 ]] || fail "wrong sysroot file count: $count"
tree_digest=$(
  cd "$toolchain_dir/sysroot"
  find . -type f -printf '%P\n' | LC_ALL=C sort | while IFS= read -r path; do
    printf 'f\t%s\t%s\t%s\n' "$(stat -c %a "$path")" "$(sha256sum "$path" | cut -d' ' -f1)" "$path"
  done | sha256sum | cut -d' ' -f1
)
[[ "$tree_digest" = fc06187ae45bcedeea4f76f33868ccb05a8c80831d5dce19adbd5eee6e6e06e1 ]] || fail "wrong sysroot tree digest: $tree_digest"

complete_tree_digest=$(
  cd "$toolchain_dir"
  find . \( -type f -o -type l \) -printf '%P\n' | LC_ALL=C sort | while IFS= read -r path; do
    if [[ -L "$path" ]]; then
      printf 'l\t%s\t%s\n' "$(readlink "$path")" "$path"
    else
      printf 'f\t%s\t%s\t%s\n' "$(stat -c %a "$path")" "$(sha256sum "$path" | cut -d' ' -f1)" "$path"
    fi
  done | sha256sum | cut -d' ' -f1
)
[[ "$complete_tree_digest" = 9caa5c02dcd2d9c01e5d0196956787a0f31760195c6544a2ceafcb771f469521 ]] \
  || fail "wrong complete toolchain tree digest: $complete_tree_digest"

identity=$(printf '%s\n%s\n%s\n' \
  427556b5ef3ac437eba754d90c5e7e9096c9a8df \
  fc06187ae45bcedeea4f76f33868ccb05a8c80831d5dce19adbd5eee6e6e06e1 \
  7bc75866617449d384679bd29298a222a458ff0daea0fc4c221122b5513cf307 | sha256sum | cut -d' ' -f1)
printf '%s\n' "$identity"
