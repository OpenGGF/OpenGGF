# Native-compatible PCM component vectors, v1

Consumer: `com.openggf.audio.synth.TestNativeCompatiblePcmOutput`.
Tier: **output stage on an identical strobe schedule** (FM + DAC through the
YM2612 pins, NTSC, 44.1 kHz). These vectors do not qualify PSG, the MAME YM2612
core, production write scheduling, carry state, any game window or rendered
ending PCM; those remain open.

## Input

`strobes.csv` (sha256 `5a2f9d2117d76de76532c80c115879c053459b797e63c6f462b15a0d43a268fd`)
is the raw YM bus strobe schedule (`cycle,bus_port,value`, applied before YM
internal clock `cycle`) that the production `Ym2612Chip` presents for the fixed
synthetic FM + DAC stream in `NativeCompatiblePcmVectors` (194 strobes, 7,113
24-cycle frames). The test re-derives it and requires byte equality, so the
vectors and the engine run share one input.

## Native-source generator (outside the repository)

These sources are not committed, and their hashes are recorded here.
`gpgx_fm_vectors.c` mirrors the YM/filter loops of GPGX `sound.c`/`system.c`,
whose non-commercial licence keeps that text out of this GPL repository. The
harness can be rebuilt in minutes from the pinned BizHawk source and this
description.

- Genesis Plus GX files from BizHawk `427556b5ef3ac437eba754d90c5e7e9096c9a8df`
  (`waterbox/gpgx/Genesis-Plus-GX/core/sound/`): `ym3438.c` `519d005b...7241b`,
  `ym3438.h` `8e60e35f...7cd189`, `blip_buf.c` `f807ee67...0fc80461`,
  `blip_buf.h` `367414a8...0428e`, `eq.c` `44567e07...d885d3`, `eq.h`
  `4f55824d...596955`.
- `gpgx_fm_vectors.c` (sha256 `6ef2a14c211f263431d742a818b355a57cd24046d1972aace97713717d6080dd`):
  replays the strobes into `ym3438.c` in `ym3438_mode_ym2612` (BizHawk
  `Nuked_YM2612`). It reproduces `sound.c` `YM3438_Update` / `sound_update`:
  the held 24-cycle sum times 11, `fm_preamp` 100, and `blip_add_delta` at
  `start + k * 42` within 3420 * 262 master-clock frames. It reproduces the
  `system.c` `audio_init(44100, 0)` rates and the `audio_update` filter block.
  The filter is none, LowPass (`lp_range`, 16-bit carries) or ThreeBand
  (`eq.c`, 16-bit clipping).
- `musl_sin_harness.c` (sha256 `f4df658f8a5434ee9fd5f41bd800adb764c47df6733b3b6761582178bc7fc29b`):
  `eq.o`'s `sin` reference is renamed to this musl `__sin` branch with
  `objcopy --redefine-sym`. The BizHawk core links musl, not the host glibc.
- Compile (clang 23.1.1, x86-64 host, 2026-10-16T19:24:17Z):
  `clang -O2 -std=gnu99 -fno-builtin-sin -DHAVE_YM3438_CORE -DLSB_FIRST -I<gpgx>/core/sound -I<gpgx>/core -c -o eq.o <gpgx>/core/sound/eq.c && objcopy --redefine-sym sin=musl_sin_harness eq.o && clang -O2 -std=gnu99 -DHAVE_YM3438_CORE -DLSB_FIRST -I<gpgx>/core/sound -I<gpgx>/core -o gpgx_fm_vectors gpgx_fm_vectors.c musl_sin_harness.c <gpgx>/core/sound/ym3438.c <gpgx>/core/sound/blip_buf.c eq.o -lm`
- Run: `gpgx_fm_vectors strobes.csv 8 1 <filter> <lp_range> <low> <high> <lg> <mg> <hg> <out.s16> <info.txt>`.

| Vector | Arguments after `strobes.csv 8 1` | sha256 |
|---|---|---|
| `native-none.s16` | `0 26214 880 5000 100 100 100` | `3d93b2708bbd2ef9bb511b8ee6ecdb71a5693191929f1d1251b160a905d5f5c9` |
| `native-threeband-unity.s16` | `2 26214 880 5000 100 100 100` (Sonic/Tails movie SyncSettings) | `f75953af92f3e3a2d20ca8360df12f9ed459fb63fbf7d820d4dd4045de5941cc` |
| `native-lowpass-6666.s16` | `1 26214 880 5000 100 100 100` | `7bd9a27303e70ed8f703c71db7c53551d737e0cc888c420564b8337e2826dbfb` |
| `native-threeband-150-50-200.s16` | `2 26214 880 5000 150 50 200` | `cffd6a42ed877e20227a247b2ed8310fa468a5d19647e78dc1323b684ce86dd1` |

Each vector holds 5,887 interleaved stereo s16 LE samples (8 NTSC frames).
The harness applied all 194 strobes over 170,675 YM clocks.

## Equaliser `sin` from the actual native core

The stock BizHawk 2.11 core is `dll/gpgx.wbx.zst`, sha256
`c4231296ec5ba59b431df22b68e234ae7bfbbfc87b6e72fa471234ac1b220d12`. This is
the TraceChaser source-lock "stock" core. Its ELF decompresses to sha256
`b4cc6dabc069a6f1b87790212d80f665d216e603aa4990955cc816d5bf98d218` and is
not stripped. Its inlined `init_3band_state`, in `audio_reset`, calls musl's
`sin` at `0x36f00004e14`.

`tools/audio/native-pcm/stock_core_sin.c` maps that ELF's load segments at
their fixed addresses. The scratch original that produced the pinned dump had
sha256 `827b9393721c83821c5a23710fdaf263208e0ddbc9e768fe94bd2252e47735b8`;
the promoted copy differs only in its header comment. It executes the core's own
`sin` for every 16-bit cutoff at 44.1 kHz. The test pins the dump hash
`c2cc5844...cceeb8`, and `MuslSin` reproduces all 65,536 values.
`StrictMath.sin` differs on 792 of them. Host glibc gives a different
880 Hz cutoff: in an earlier harness build, that changed 916 of 11,774
unity-gain ThreeBand samples.

## Perturbations that must fail

A one-unit pin-sum change and a one-clock (42 master clocks) origin shift
break `native-none`. Mid gain 1.01, a low cutoff of 881 Hz and the no-filter
output break `native-threeband-unity`. `lp_range + 1` breaks
`native-lowpass-6666`.
