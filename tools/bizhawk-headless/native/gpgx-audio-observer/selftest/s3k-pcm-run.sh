#!/usr/bin/bash -p
set -euo pipefail
root=${BASH_SOURCE[0]%/*}; root=$(cd -P -- "$root" && pwd)
source_dir=${1-}; toolchain=${2-}; scratch=${3-}
[[ "$source_dir" = /* && "$toolchain" = /* && "$scratch" = /* && -d "$scratch" ]] || exit 2
"$root/s3k-parity-run.sh" "$source_dir" "$toolchain" "$scratch"
/usr/bin/cp -- "$root/s3k_pcm_harness.c" "$scratch/native-selftest/"
/usr/bin/cp -- "$root/s3k_pcm_replay_harness.c" "$scratch/native-selftest/"
/usr/bin/env -i PATH=/usr/bin:/bin \
  LD_LIBRARY_PATH="$toolchain/clang/usr/lib/x86_64-linux-gnu:$toolchain/clang/usr/lib/llvm-16/lib" \
  "$toolchain/clang/usr/bin/clang-16" -std=c99 -DLSB_FIRST -O2 -Wall -Wextra -Werror \
  "$scratch/native-selftest/audio_trace.c" "$scratch/native-selftest/s3k_pcm_harness.c" \
  -o "$scratch/native-selftest/s3k-pcm-harness"
"$scratch/native-selftest/s3k-pcm-harness"
/usr/bin/env -i PATH=/usr/bin:/bin \
  LD_LIBRARY_PATH="$toolchain/clang/usr/lib/x86_64-linux-gnu:$toolchain/clang/usr/lib/llvm-16/lib" \
  "$toolchain/clang/usr/bin/clang-16" -std=c99 -DLSB_FIRST -O2 -Wall -Wextra -Werror \
  -I"$source_dir/waterbox/gpgx/cinterface" \
  -I"$source_dir/waterbox/gpgx/Genesis-Plus-GX/core" \
  -I"$source_dir/waterbox/gpgx/Genesis-Plus-GX/core/sound" \
  "$scratch/native-selftest/s3k_pcm_replay_harness.c" -lm \
  -o "$scratch/native-selftest/s3k-pcm-replay-harness"
"$scratch/native-selftest/s3k-pcm-replay-harness"
