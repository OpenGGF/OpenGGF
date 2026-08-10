# Task 7 report — native buffered GPGX observer

## Outcome

Task 7 freezes and reproducibly builds the generic observation-only native
observer for the exact BizHawk 2.11 GPGX Waterbox core. The artifact remains a
separate create-new installation; stock and its managed DLLs are unchanged.
The selected managed lane is the Task 6 exact-hash `REFLECTION` adapter. No
replacement managed DLL or provisional S2/S3K profile is shipped. Game-specific
real Z80 capability remains Task 8.

## RED/GREEN

The registered initial C# test failed because the native patch and artifact
lock were absent. A later exact-Clang build failed because the public header
omitted the departure prototypes; the same `-Werror` build passed after the
exact prototypes were added. Dirty/ignored source and provisional bundle locks
were independently rejected without publishing output.

The actual M68K boundary harness was RED when a zero-initialized prefetch cache
made the intended multiword `MOVE.B` fixture decode as another instruction and
emit no FM write. Invalidating the synthetic prefetch cache made the real
interpreter fetch the intended opcode and its literal start-PC assertion green.
The managed proof later reproduced zero-capacity empty-array drain as
`TRACE_INVALID_PHASE`: BizInvoker marshalled the empty array as a non-null
pointer. The adapter now normalizes that exact case to null, and a fresh empty
READY frame proves the corrected contract through Waterbox.

## Feasibility and ABI proof

Five packed candidates and the 65,536-entry event array were compiled/linked
with the exact Task 6 Clang 16, LLD, musl sysroot, link script, historical build
path, and environment. Static assertions freeze every published offset and
sizes 64/16/32/16/32, `CHAR_BIT == 8`, fixed integer widths, and little-endian
`LSB_FIRST`. The linked ELF has no dynamic section. Every observer object is
LOCAL in writable/alloc `.invis`, aligned to 32 bytes, and excluded from
savestate serialization. Exactly ten `gpgx_audio_trace_*` departures are
GLOBAL; no host/interior pointer is retained after configure.

```text
.invis size       0x2088d0 (2,132,176 bytes)
stock .invis      0x000d10 (3,344 bytes)
observer addition 0x207bc0 (2,128,832 bytes)
event array       2,097,152 bytes
```

## Native implementation and proof

- Z80 visits after interrupt admission and before `R++`/`ROP()`.
- M68K visits at the ordinary prefetch boundary and IRQ-delay direct fetch.
- scoped issue-source save/restore wraps both runners; chip events use the
  latched instruction-start PC, not the advanced dispatch-time PC.
- one common FM wrapper precedes YM2612/YM3438 mutation for all five managed
  Genesis selectors; the common PSG entry precedes sync/latch mutation for the
  M68K, Z80, and banked-Z80 paths.
- copied manifests drive a depth-eight stack, deterministic same-PC selection,
  atomic snapshots/tails, typed iterations, continuation, saturation, and the
  disabled/configured/recording/ready state machine.
- reset pre-reserves cancellation groups and its protected tail, cancels inner
  to outer, attributes reset writes to the reset root, and fails closed without
  discarding retained in-frame events.

The exact-Clang native suite produced:

```text
native-observer-selftest: 12 ordered nested events; scoped CPU PCs; READY reset fail-closed
```

Five linked harnesses prove copied inputs, ordered nested ownership, distinct
snapshots, exact FM-port/PSG vectors, phase/drain/overflow/reset behavior, token
wrap, and ordinal continuity. Two harnesses execute the actual patched Z80 and
M68K interpreters: ordinary and post-IRQ Z80 admission, plus a multiword M68K
write at PC 4 and the IRQ-delay extra-fetch seam at PC 8. These are synthetic
CPU-boundary proofs, not real-game capability claims.

## Frozen identities

```text
patch SHA-256                 45d85fc19405457c788be4f6c17d2b14281d33fbff163cd42eead76e08f7f6d2
raw core size                 41718744
raw core SHA-256              7807b57ffdfa303465ec2a2e707a5aacc38bd56cd10e201aca2965620eb71fb2
compressed core size          409653
compressed core SHA-256       ba276573fc7802fb2313c051471dbdd664959c06aaafa6ef73564799886d083f
Waterbox BuildID              8e822239d27df092
source bundle raw SHA-256     6f2e6d6102f594d0ae014be8ff5030dc3599595ecff36e01f8517e3a5d111b5a
source bundle zst SHA-256     abd68651d633a0a75d01cb9569cfb9dc15da4a7540eb072fc2d8eb11e548ed0e
path manifest SHA-256         ca94c5213d326ab3affac073dff5b67cf6b9c275db6d2c6291688376703709c1
mode manifest SHA-256         03b81d212882a71329d5d45377bdebe1aa6194a0012bd9a4749ec35cd5683440
Task 7 recipe SHA-256         eb58429b3b0bb47b337c60055d849f917842b8e973083d23261bdb2e04783d99
whole identity SHA-256        f3721d457aa867559d6ebad16111a4a1d737b9187c8655b144788a685d869e28
adapter source SHA-256        770dfcfef0fabc2eb7211add26d7a3716e33b75ddbe7dd3d7ba1568c8cb3a102
callgraph proof SHA-256       536711d3eada4bc9898c256c7479ee5a651381025f0476499bac76c9e82dd5c6
ELF proof SHA-256             756b497189800fa7e12a01736e109a2cc0cbaf6cdfa72cb49ef6e1d0a3e21869
Task 6 input identity         36dde84c81429343b2f4425ff66c04f8fbdf54bcaf42a2459e68c52f95e9a0d4
```

Fresh locked builds use independent toolchain copies and compare raw,
compressed, source-bundle, manifest, and identity bytes. The source archive
normalizes LF, modes, uid/gid, names, mtime, and path order, excluding
Git/generated/sysroot/cache/binary material. Installation copies rather than
links stock and includes corresponding source, literal patch, build evidence,
BizHawk/GPGX/musl/zstd/LLVM/Debian notices, and complete GPGX terms.

## Semantic and regression evidence

Stock and observer-disabled patched cores advance exact S1 REV01 for ten frames
to the same full 64 KiB 68K RAM digest:

```text
de2f256064a0af797747c2b97505dc0b9f3df0de4f489eac731c23ae9ca9cc31
```

The Waterbox proof invokes all ten departures, validates every managed offset
and little-endian vector, destroys caller arrays after configure, drains Reset
snapshots from copied data, and proves `.invis` savestate exclusion. It boots
all five Genesis FM selectors. No runtime performance percentage is claimed
from this short prefix; disabled execution takes one enable split and then an
observer-free loop.

Task 8 owns real S2/S3K hook manifests and the first positive headless capture.

## Final verification

```text
selftest/run.sh                                      PASS (5 native harnesses)
fresh locked build A / fresh locked build B          PASS (byte-identical)
independent install A / independent install B         PASS (create-new)
GpgxHost departure/reset/savestate proof              PASS
GpgxHost five Genesis FM selector boots               PASS
GpgxHost 10-frame 64 KiB RAM digest                   PASS (de2f2560...)
```

The registered slow gate passed and exercises two fresh builds/installs,
outside-root/existing-root refusal, a failure after stock verification,
unchanged caller stock, no symlinks/hard links, and full notice/source
publication. The final focused Java Task 1–3 regression is 103/103, the C#
source-lock suite is 3/3 with the slow gate intentionally skipped by default,
and the separately enabled slow gate is 1/1.
