#!/usr/bin/env bash
# Build and run the native-compatible PCM vector generator (gpgx_pcm_vectors.c) against the pinned, EXTERNAL
# Genesis Plus GX sources of BizHawk 427556b5ef3ac437eba754d90c5e7e9096c9a8df. Nothing from that tree is copied here.
#
#   generate_vectors.sh build <gpgx-core-dir> <build-dir>
#   generate_vectors.sh run   <build-dir> <input-dir> <out-dir>
#
# <gpgx-core-dir> is .../waterbox/gpgx/Genesis-Plus-GX/core of that BizHawk checkout; build verifies every source it
# uses by SHA-256 first. <input-dir> holds strobes.csv and psg_writes.csv (the committed
# src/test/resources/audio/native-compatible-pcm/v1/ inputs). run writes the four vectors and their info files.
# Compiling and running are separate steps so a caller can hold the build lease and the run lease respectively
# (AGENTS.md). Requires clang and objcopy. Originating task: round-2 lane pcm-output, 2026-10-16.
set -euo pipefail
here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)
case "${1:-}" in
  build)
    core=$(cd "$2" && pwd -P); build=$3
    mkdir -p "$build"
    ( cd "$core/sound" && sha256sum -c --quiet - ) <<'PINNED'
519d005b525db422a80b2011abdad19ec774809d2852768975103b83cee7241b  ym3438.c
8e60e35f77049d0e600ad1a47bfc3dfc8b832483e614104473a83c1f33cd7189  ym3438.h
f807ee6711663d67f15e38b82c0f8445375b13ec3a265f34c510fdcc0fc80461  blip_buf.c
367414a873407fad24cf0207429cf71e62aed080a278f40884b157f25930428e  blip_buf.h
44567e07fe92e64c368c327cd385529e88f156075cc138750ca6165fd28885d3  eq.c
4f55824dd90db958d8c00837d1456ea0e01f05fac8fdd323d8ebc34c3c596955  eq.h
8ce153597bd02b34472e6b74b29be9eddce796e8a269049eda9bad815b766700  psg.c
a8a9d4dd19b00569e0e3101f7e4a04e5b0b2a4e75c6bbf850bb50e6cd55eb8f3  psg.h
PINNED
    cc=${CC:-clang}
    flags=(-O2 -std=gnu99 -DHAVE_YM3438_CORE -DLSB_FIRST)
    "$cc" "${flags[@]}" -fno-builtin-sin -I"$core/sound" -I"$core" -c -o "$build/eq.o" "$core/sound/eq.c"
    objcopy --redefine-sym sin=musl_sin "$build/eq.o"
    "$cc" "${flags[@]}" -I"$here/shared_stub" -I"$core/sound" -o "$build/gpgx_pcm_vectors" \
      "$here/gpgx_pcm_vectors.c" "$here/musl_sin.c" "$core/sound/ym3438.c" "$core/sound/blip_buf.c" \
      "$core/sound/psg.c" "$build/eq.o" -lm
    ;;
  run)
    build=$2; input=$3; out=$4
    mkdir -p "$out"
    run() { "$build/gpgx_pcm_vectors" "$input/strobes.csv" 8 1 "$@" "$input/psg_writes.csv"; }
    run 0 26214 880 5000 100 100 100 "$out/native-none.s16" "$out/info-none.txt"
    run 2 26214 880 5000 100 100 100 "$out/native-threeband-unity.s16" "$out/info-threeband-unity.txt"
    run 1 26214 880 5000 100 100 100 "$out/native-lowpass-6666.s16" "$out/info-lowpass-6666.txt"
    run 2 26214 880 5000 150 50 200 "$out/native-threeband-150-50-200.s16" "$out/info-threeband-150-50-200.txt"
    ;;
  *) echo "usage: $0 build <gpgx-core-dir> <build-dir> | run <build-dir> <input-dir> <out-dir>" >&2; exit 2 ;;
esac
