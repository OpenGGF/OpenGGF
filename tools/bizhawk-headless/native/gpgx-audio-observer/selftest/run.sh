#!/usr/bin/bash -p
set -euo pipefail
root=${BASH_SOURCE[0]%/*}; root=$(cd -P -- "$root" && pwd)
source_dir=${1-}; toolchain=${2-}; scratch=${3-}
[[ "$source_dir" = /* && "$toolchain" = /* && "$scratch" = /* && -d "$scratch" ]] || exit 2
/usr/bin/mkdir "$scratch/native-selftest"
/usr/bin/cp -- "$source_dir/waterbox/gpgx/cinterface/audio_trace.c" \
  "$source_dir/waterbox/gpgx/cinterface/audio_trace.h" "$scratch/native-selftest/"
/usr/bin/cp -- "$root/shared.h" "$root/emulibc.h" "$root/harness.c" "$root/wrap_harness.c" \
  "$root/matrix_harness.c" \
  "$root/cpu_boundary_harness.c" "$root/m68k_boundary_harness.c" \
  "$scratch/native-selftest/"
/usr/bin/env -i PATH=/usr/bin:/bin \
  LD_LIBRARY_PATH="$toolchain/clang/usr/lib/x86_64-linux-gnu:$toolchain/clang/usr/lib/llvm-16/lib" \
  "$toolchain/clang/usr/bin/clang-16" -std=c99 -DLSB_FIRST -O2 -Wall -Wextra -Werror \
  "$scratch/native-selftest/audio_trace.c" "$scratch/native-selftest/harness.c" \
  -o "$scratch/native-selftest/harness"
"$scratch/native-selftest/harness"
/usr/bin/env -i PATH=/usr/bin:/bin \
  LD_LIBRARY_PATH="$toolchain/clang/usr/lib/x86_64-linux-gnu:$toolchain/clang/usr/lib/llvm-16/lib" \
  "$toolchain/clang/usr/bin/clang-16" -std=c99 -DLSB_FIRST -O2 -Wall -Wextra -Werror \
  "$scratch/native-selftest/wrap_harness.c" -o "$scratch/native-selftest/wrap-harness"
"$scratch/native-selftest/wrap-harness"
/usr/bin/env -i PATH=/usr/bin:/bin \
  LD_LIBRARY_PATH="$toolchain/clang/usr/lib/x86_64-linux-gnu:$toolchain/clang/usr/lib/llvm-16/lib" \
  "$toolchain/clang/usr/bin/clang-16" -std=c99 -DLSB_FIRST -O2 -Wall -Wextra -Werror \
  "$scratch/native-selftest/matrix_harness.c" -o "$scratch/native-selftest/matrix-harness"
"$scratch/native-selftest/matrix-harness"
/usr/bin/env -i PATH=/usr/bin:/bin \
  LD_LIBRARY_PATH="$toolchain/clang/usr/lib/x86_64-linux-gnu:$toolchain/clang/usr/lib/llvm-16/lib" \
  "$toolchain/clang/usr/bin/clang-16" -std=c99 -DLSB_FIRST -DcdStream=cdStream \
  -DINLINE='static __inline__' -include string.h \
  -O2 -Wall -Wextra -Werror -Wno-unused-function -Wno-sign-compare \
  -I"$source_dir/waterbox/emulibc" \
  -I"$scratch/native-selftest" -I"$source_dir/waterbox/gpgx/Genesis-Plus-GX/core" \
  -I"$source_dir/waterbox/gpgx/Genesis-Plus-GX/core/z80" \
  -I"$source_dir/waterbox/gpgx/cinterface" \
  "$scratch/native-selftest/audio_trace.c" \
  "$source_dir/waterbox/gpgx/Genesis-Plus-GX/core/z80/z80.c" \
  "$scratch/native-selftest/cpu_boundary_harness.c" \
  -o "$scratch/native-selftest/cpu-boundary-harness"
"$scratch/native-selftest/cpu-boundary-harness"
/usr/bin/env -i PATH=/usr/bin:/bin \
  LD_LIBRARY_PATH="$toolchain/clang/usr/lib/x86_64-linux-gnu:$toolchain/clang/usr/lib/llvm-16/lib" \
  "$toolchain/clang/usr/bin/clang-16" -std=c99 -DLSB_FIRST -DcdStream=cdStream \
  -DINLINE='static __inline__' -include string.h \
  -O2 -Wall -Wextra -Werror -Wno-unused-function -Wno-sign-compare \
  -I"$source_dir/waterbox/emulibc" \
  -I"$source_dir/waterbox/gpgx/util" \
  -I"$source_dir/waterbox/gpgx/Genesis-Plus-GX/core" \
  -I"$source_dir/waterbox/gpgx/Genesis-Plus-GX/core/cart_hw" \
  -I"$source_dir/waterbox/gpgx/Genesis-Plus-GX/core/cart_hw/svp" \
  -I"$source_dir/waterbox/gpgx/Genesis-Plus-GX/core/cd_hw" \
  -I"$source_dir/waterbox/gpgx/Genesis-Plus-GX/core/debug" \
  -I"$source_dir/waterbox/gpgx/Genesis-Plus-GX/core/input_hw" \
  -I"$source_dir/waterbox/gpgx/Genesis-Plus-GX/core/m68k" \
  -I"$source_dir/waterbox/gpgx/Genesis-Plus-GX/core/ntsc" \
  -I"$source_dir/waterbox/gpgx/Genesis-Plus-GX/core/sound" \
  -I"$source_dir/waterbox/gpgx/Genesis-Plus-GX/core/z80" \
  -I"$source_dir/waterbox/gpgx/cinterface" \
  "$source_dir/waterbox/gpgx/cinterface/audio_trace.c" \
  "$source_dir/waterbox/gpgx/Genesis-Plus-GX/core/m68k/m68kcpu.c" \
  "$scratch/native-selftest/m68k_boundary_harness.c" \
  -o "$scratch/native-selftest/m68k-boundary-harness"
"$scratch/native-selftest/m68k-boundary-harness"
