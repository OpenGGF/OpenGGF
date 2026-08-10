#!/usr/bin/env bash
set -euo pipefail

fail() { printf 'fetch-source: %s\n' "$*" >&2; exit 1; }
require_absent_output() {
  [[ "$1" = /* ]] || fail "output must be an absolute path"
  [[ ! -e "$1" && ! -L "$1" ]] || fail "output already exists: $1"
  [[ -d "$(dirname "$1")" ]] || fail "output parent does not exist"
}
publish_create_new() {
  local source=$1 target=$2
  printf '%s  %s\n' 4dc8719b3b60a5e03b3720f3060415a8dd3b564b74319539b2a0dc52bc50c0df /usr/bin/mv \
    | sha256sum -c - >/dev/null || fail "host no-replace publisher differs"
  /usr/bin/mv -T --no-copy --no-clobber -- "$source" "$target"
  [[ ! -e "$source" && ! -L "$source" ]] || fail "output already exists: $target"
}

output=
bizhawk_repository=https://github.com/TASEmulators/BizHawk.git
gpgx_repository=https://github.com/TASEmulators/Genesis-Plus-GX.git
musl_repository=https://github.com/nattthebear/musl.git
while (($#)); do
  case "$1" in
    --output) output=${2-}; shift 2 ;;
    --bizhawk-repository) bizhawk_repository=${2-}; shift 2 ;;
    --gpgx-repository) gpgx_repository=${2-}; shift 2 ;;
    --musl-repository) musl_repository=${2-}; shift 2 ;;
    *) fail "unknown argument: $1" ;;
  esac
done
[[ -n "$output" ]] || fail "--output is required"
require_absent_output "$output"

parent=$(dirname "$output")
stage=$(mktemp -d "$parent/.gpgx-source-staging.XXXXXX")
cleanup() { if [[ -n "${stage-}" && -d "$stage" ]]; then rm -rf -- "$stage"; fi; }
trap cleanup EXIT

git -C "$stage" init -q
git -C "$stage" fetch -q --depth=1 "$bizhawk_repository" 427556b5ef3ac437eba754d90c5e7e9096c9a8df
git -C "$stage" checkout -q --detach FETCH_HEAD
[[ $(git -C "$stage" rev-parse HEAD) = 427556b5ef3ac437eba754d90c5e7e9096c9a8df ]] || fail "wrong BizHawk commit"
[[ $(git -C "$stage" rev-parse HEAD^{tree}) = 7281227ed2f3b89c0962b2792b28539e35361c6b ]] || fail "wrong BizHawk tree"

git -C "$stage/waterbox/gpgx/Genesis-Plus-GX" init -q
git -C "$stage/waterbox/gpgx/Genesis-Plus-GX" fetch -q --depth=1 "$gpgx_repository" 051d430d3d1b54625f9900c8f152d7f232e06daf
git -C "$stage/waterbox/gpgx/Genesis-Plus-GX" checkout -q --detach FETCH_HEAD
git -C "$stage/waterbox/musl" init -q
git -C "$stage/waterbox/musl" fetch -q --depth=1 "$musl_repository" 2063abc4e16c84218757b1db10d3cdf9f36ef3f8
git -C "$stage/waterbox/musl" checkout -q --detach FETCH_HEAD

[[ $(git -C "$stage/waterbox/gpgx/Genesis-Plus-GX" rev-parse HEAD) = 051d430d3d1b54625f9900c8f152d7f232e06daf ]] || fail "wrong GPGX commit"
[[ $(git -C "$stage/waterbox/gpgx/Genesis-Plus-GX" rev-parse HEAD^{tree}) = 1bb96ca74d660d383e70d9cd56b88906a0773519 ]] || fail "wrong GPGX tree"
[[ $(git -C "$stage/waterbox/musl" rev-parse HEAD) = 2063abc4e16c84218757b1db10d3cdf9f36ef3f8 ]] || fail "wrong musl commit"
[[ $(git -C "$stage/waterbox/musl" rev-parse HEAD^{tree}) = a9969a63cd1780cdcc4c09745a8789206a72b8b4 ]] || fail "wrong musl tree"

while read -r expected relative; do
  printf '%s  %s\n' "$expected" "$stage/$relative" | sha256sum -c - >/dev/null
done <<'LOCKED_FILES'
4b86754f2c5d8ebe759efa90f9e74a985098492ad011bfe197ba23a93e1173fd waterbox/emulibc/emulibc.c
90eadc83d089550dfbbfe012839ba5804cbe46628c3264b0b7bbea1b0ccabb89 waterbox/emulibc/emulibc.h
b4be11bda3c1e608fd5d38be48d70a4d506f92c32249e64ca9338c26a06810f3 waterbox/emulibc/waterboxcore.h
0524d95e1e350a42ef8f3676b6d59b06d959a72574a18261edf9fa0c8d029a9a waterbox/emulibc/Makefile
c328932fde7df37ce21759045b5b90f13170b9df88b1798e064c35a34b8fbb1f waterbox/common.mak
3a5f16e86596f0bb4b254b0fa0c4ba68effbf8a438ef34bbfbe7b179692cd536 waterbox/linkscript.T
c92fd9b2cbce52c75b580bf91d357cc028c5ea5c935475b042cce7110ef4caaa waterbox/gpgx/Makefile
1c6b2127d864cdc912645e7130debcd55e47ba1ce63e8e004ea3cd08fed71b22 waterbox/musl/configure
34abbf5b7c115b3c8c1cf58cc4b2efef87d20a175dccfef1739b0444a022662c waterbox/musl/wbox_configure.sh
ef7b3279e8be2e1b519f73812a217f4027bd97c8f09dd6a691f1061766d2af2b waterbox/musl/wbox_build.sh
LOCKED_FILES

for repository in "$stage" "$stage/waterbox/gpgx/Genesis-Plus-GX" "$stage/waterbox/musl"; do
  [[ -z $(git -C "$repository" status --short --untracked-files=all --ignore-submodules=none) ]] \
    || fail "source tree is not clean: $repository"
  [[ -z $(git -C "$repository" clean -ndx) ]] || fail "source tree has untracked or ignored files: $repository"
done
publish_create_new "$stage" "$output"
stage=
printf '%s\n' "$output"
